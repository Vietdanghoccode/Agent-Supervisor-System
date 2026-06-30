package com.viettel.conversation.outbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class OutboxJdbcRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    OutboxJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ClaimedOutboxEvent> claim(List<String> eventTypes, int batchSize, String workerId,
                                   Instant now, Duration leaseDuration) {
        String sql = """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE event_type IN (:eventTypes)
                      AND ((status = 'PENDING' AND next_attempt_at <= :now)
                        OR (status = 'PROCESSING' AND locked_until <= :now))
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                )
                UPDATE outbox_events event
                SET status = 'PROCESSING',
                    attempt_count = event.attempt_count + 1,
                    locked_by = :workerId,
                    locked_until = :lockedUntil,
                    last_error = NULL
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.id, event.aggregate_id, event.event_type,
                          event.payload::text, event.attempt_count
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventTypes", eventTypes)
                .addValue("batchSize", batchSize)
                .addValue("workerId", workerId)
                .addValue("now", Timestamp.from(now))
                .addValue("lockedUntil", Timestamp.from(now.plus(leaseDuration)));
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new ClaimedOutboxEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("payload"),
                resultSet.getInt("attempt_count")));
    }

    int markPublished(UUID eventId, String workerId, Instant publishedAt) {
        return jdbcTemplate.update("""
                        UPDATE outbox_events
                        SET status = 'PUBLISHED', published_at = :publishedAt,
                            locked_by = NULL, locked_until = NULL, last_error = NULL
                        WHERE id = :eventId AND status = 'PROCESSING' AND locked_by = :workerId
                        """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("workerId", workerId)
                        .addValue("publishedAt", Timestamp.from(publishedAt)));
    }

    int markForRetry(UUID eventId, String workerId, Instant nextAttemptAt, String error) {
        return jdbcTemplate.update("""
                        UPDATE outbox_events
                        SET status = 'PENDING', next_attempt_at = :nextAttemptAt,
                            locked_by = NULL, locked_until = NULL, last_error = :error
                        WHERE id = :eventId AND status = 'PROCESSING' AND locked_by = :workerId
                        """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("workerId", workerId)
                        .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
                        .addValue("error", error));
    }

    long pendingCount(List<String> eventTypes) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM outbox_events
                        WHERE event_type IN (:eventTypes) AND status IN ('PENDING', 'PROCESSING')
                        """,
                new MapSqlParameterSource("eventTypes", eventTypes), Long.class);
        return count == null ? 0 : count;
    }

    long oldestPendingAgeSeconds(List<String> eventTypes, Instant now) {
        Long age = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(EXTRACT(EPOCH FROM (:now - min(created_at)))::bigint, 0)
                        FROM outbox_events
                        WHERE event_type IN (:eventTypes) AND status IN ('PENDING', 'PROCESSING')
                        """,
                new MapSqlParameterSource()
                        .addValue("eventTypes", eventTypes)
                        .addValue("now", Timestamp.from(now)), Long.class);
        return age == null ? 0 : Math.max(0, age);
    }
}
