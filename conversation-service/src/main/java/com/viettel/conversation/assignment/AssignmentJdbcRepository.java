package com.viettel.conversation.assignment;

import com.viettel.conversation.domain.ConversationStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class AssignmentJdbcRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    AssignmentJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ClaimedConversation> claim(int batchSize, Instant now, Duration assigningTimeout) {
        String sql = """
                WITH candidates AS (
                    SELECT id, status AS original_status, skill
                    FROM conversations
                    WHERE status IN ('WAITING', 'QUEUED')
                       OR (status = 'ASSIGNING' AND updated_at <= :staleBefore)
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                )
                UPDATE conversations conversation
                SET status = 'ASSIGNING',
                    updated_at = :now
                FROM candidates
                WHERE conversation.id = candidates.id
                RETURNING conversation.id, candidates.original_status, candidates.skill
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("now", Timestamp.from(now))
                .addValue("staleBefore", Timestamp.from(now.minus(assigningTimeout)));
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new ClaimedConversation(
                resultSet.getObject("id", UUID.class),
                ConversationStatus.valueOf(resultSet.getString("original_status")),
                resultSet.getString("skill")));
    }

    int markQueued(UUID conversationId, Instant now) {
        return guardedUpdate("""
                UPDATE conversations
                SET status = 'QUEUED', updated_at = :now
                WHERE id = :conversationId AND status = 'ASSIGNING'
                """, conversationId, null, now);
    }

    int markWaiting(UUID conversationId, Instant now) {
        return guardedUpdate("""
                UPDATE conversations
                SET status = 'WAITING', updated_at = :now
                WHERE id = :conversationId AND status = 'ASSIGNING'
                """, conversationId, null, now);
    }

    int markAssigned(UUID conversationId, long agentId, Instant now) {
        return guardedUpdate("""
                UPDATE conversations
                SET status = 'ASSIGNED', agent_id = :agentId, updated_at = :now
                WHERE id = :conversationId AND status = 'ASSIGNING'
                """, conversationId, agentId, now);
    }

    private int guardedUpdate(String sql, UUID conversationId, Long agentId, Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("agentId", agentId)
                .addValue("now", Timestamp.from(now));
        return jdbcTemplate.update(sql, parameters);
    }
}
