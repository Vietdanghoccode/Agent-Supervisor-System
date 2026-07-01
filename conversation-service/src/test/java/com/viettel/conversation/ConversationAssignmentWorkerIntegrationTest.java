package com.viettel.conversation;

import com.viettel.conversation.assignment.AgentConfirmationResponse;
import com.viettel.conversation.assignment.AgentReservationResponse;
import com.viettel.conversation.assignment.AgentServiceClient;
import com.viettel.conversation.assignment.ConversationAssignmentWorker;
import com.viettel.conversation.exception.AgentServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.assignment.worker.enabled=true",
        "app.assignment.worker.assigning-timeout=1s"
})
class ConversationAssignmentWorkerIntegrationTest extends PostgresIntegrationTest {
    @Autowired ConversationAssignmentWorker worker;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockBean AgentServiceClient agentServiceClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
    }

    @Test
    void reserveWaitingConversationIntoQueue() {
        UUID conversationId = insertConversation("WAITING", "support", null, Instant.now());
        when(agentServiceClient.reserve(conversationId, "support"))
                .thenReturn(new AgentReservationResponse(conversationId, null, "WAITING", 0));

        worker.processNextBatch();

        assertThat(status(conversationId)).isEqualTo("QUEUED");
        assertThat(agentId(conversationId)).isNull();
    }

    @Test
    void reserveAndConfirmWaitingConversationIntoAssigned() {
        UUID conversationId = insertConversation("WAITING", null, null, Instant.now());
        when(agentServiceClient.reserve(conversationId, "support"))
                .thenReturn(new AgentReservationResponse(conversationId, 101L, "RESERVED", 30));
        when(agentServiceClient.confirm(conversationId))
                .thenReturn(new AgentConfirmationResponse(conversationId, 101L, "CONFIRMED"));

        worker.processNextBatch();

        assertThat(status(conversationId)).isEqualTo("ASSIGNED");
        assertThat(agentId(conversationId)).isEqualTo(101L);
    }

    @Test
    void pollQueuedConversationAndConfirmWhenReserved() {
        UUID conversationId = insertConversation("QUEUED", "support", null, Instant.now());
        when(agentServiceClient.reservation(conversationId))
                .thenReturn(new AgentReservationResponse(conversationId, 102L, "RESERVED", 30));
        when(agentServiceClient.confirm(conversationId))
                .thenReturn(new AgentConfirmationResponse(conversationId, 102L, "CONFIRMED"));

        worker.processNextBatch();

        assertThat(status(conversationId)).isEqualTo("ASSIGNED");
        assertThat(agentId(conversationId)).isEqualTo(102L);
    }

    @Test
    void returnsConversationToRetryableStatusAfterTransientFailure() {
        UUID conversationId = insertConversation("WAITING", "support", null, Instant.now());
        when(agentServiceClient.reserve(conversationId, "support"))
                .thenThrow(new AgentServiceException("unavailable"));

        worker.processNextBatch();

        assertThat(status(conversationId)).isEqualTo("WAITING");
    }

    @Test
    void recoversStaleAssigningConversation() {
        UUID conversationId = insertConversation("ASSIGNING", "support", null,
                Instant.now().minus(10, ChronoUnit.SECONDS));
        when(agentServiceClient.reserve(conversationId, "support"))
                .thenReturn(new AgentReservationResponse(conversationId, null, "WAITING", 0));

        worker.processNextBatch();

        assertThat(status(conversationId)).isEqualTo("QUEUED");
    }

    private UUID insertConversation(String status, String skill, Long agentId, Instant updatedAt) {
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO conversations
                            (id, customer_id, status, agent_id, channel, skill,
                             idempotency_key, request_hash, created_at, updated_at)
                        VALUES (?, 1, ?, ?, 'webchat', ?, ?, ?, ?, ?)
                        """,
                conversationId, status, agentId, skill, "key-" + conversationId,
                "hash-" + conversationId, Timestamp.from(now), Timestamp.from(updatedAt));
        return conversationId;
    }

    private String status(UUID conversationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM conversations WHERE id = ?", String.class, conversationId);
    }

    private Long agentId(UUID conversationId) {
        return jdbcTemplate.queryForObject(
                "SELECT agent_id FROM conversations WHERE id = ?", Long.class, conversationId);
    }
}
