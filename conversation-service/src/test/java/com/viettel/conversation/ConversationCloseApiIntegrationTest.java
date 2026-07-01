package com.viettel.conversation;

import com.viettel.conversation.assignment.AgentConfirmationResponse;
import com.viettel.conversation.assignment.AgentReservationResponse;
import com.viettel.conversation.assignment.AgentServiceClient;
import com.viettel.conversation.exception.AgentServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationCloseApiIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockBean AgentServiceClient agentServiceClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
    }

    @Test
    void closesWaitingConversation() throws Exception {
        UUID conversationId = insertConversation("WAITING", null);

        close(conversationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(statusOf(conversationId)).isEqualTo("CLOSED");
    }

    @Test
    void closesQueuedConversationAfterCancellingReservation() throws Exception {
        UUID conversationId = insertConversation("QUEUED", null);

        close(conversationId).andExpect(status().isOk());

        verify(agentServiceClient).cancel(conversationId);
        assertThat(statusOf(conversationId)).isEqualTo("CLOSED");
    }

    @Test
    void closesAssignedConversationAfterReleasingAgent() throws Exception {
        UUID conversationId = insertConversation("ASSIGNED", 101L);

        close(conversationId).andExpect(status().isOk());

        verify(agentServiceClient).release(101L, conversationId);
        assertThat(statusOf(conversationId)).isEqualTo("CLOSED");
    }

    @Test
    void closesAssigningConversationAfterConfirmingAndReleasingReservation() throws Exception {
        UUID conversationId = insertConversation("ASSIGNING", null);
        when(agentServiceClient.reservation(conversationId))
                .thenReturn(new AgentReservationResponse(conversationId, 102L, "RESERVED", 30));
        when(agentServiceClient.confirm(conversationId))
                .thenReturn(new AgentConfirmationResponse(conversationId, 102L, "CONFIRMED"));

        close(conversationId).andExpect(status().isOk());

        verify(agentServiceClient).release(102L, conversationId);
        assertThat(statusOf(conversationId)).isEqualTo("CLOSED");
    }

    @Test
    void closeIsIdempotent() throws Exception {
        UUID conversationId = insertConversation("CLOSED", null);

        close(conversationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void doesNotCloseWhenAgentCleanupFails() throws Exception {
        UUID conversationId = insertConversation("ASSIGNED", 101L);
        doThrow(new AgentServiceException("unavailable"))
                .when(agentServiceClient).release(101L, conversationId);

        close(conversationId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Agent Service unavailable"));

        assertThat(statusOf(conversationId)).isEqualTo("ASSIGNED");
    }

    private org.springframework.test.web.servlet.ResultActions close(UUID conversationId) throws Exception {
        return mockMvc.perform(post("/conversations/{conversationId}/close", conversationId)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private UUID insertConversation(String status, Long agentId) {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO conversations
                            (id, customer_id, status, agent_id, channel, skill,
                             idempotency_key, request_hash, created_at, updated_at)
                        VALUES (?, 1, ?, ?, 'webchat', 'support', ?, ?, ?, ?)
                        """,
                conversationId, status, agentId, "key-" + conversationId,
                "hash-" + conversationId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO messages
                            (id, conversation_id, sender_type, content, created_at)
                        VALUES (?, ?, 'CUSTOMER', 'Tôi cần hỗ trợ', ?)
                        """,
                messageId, conversationId, Timestamp.from(now));
        return conversationId;
    }

    private String statusOf(UUID conversationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM conversations WHERE id = ?", String.class, conversationId);
    }
}
