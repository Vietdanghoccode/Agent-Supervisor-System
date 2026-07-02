package com.viettel.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationMessagingApiIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversations");
    }

    @Test
    void sendsReplaysAndRejectsChangedPayload() throws Exception {
        UUID id = createConversation("conversation-1");

        String first = send(id, "customer", 1, "message-2", "Second")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.messageSeq").value(2))
                .andExpect(jsonPath("$.senderId").value("1"))
                .andReturn().getResponse().getContentAsString();
        String replay = send(id, "customer", 1, "message-2", "Second")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay)).isEqualTo(objectMapper.readTree(first));

        send(id, "customer", 1, "message-2", "Changed")
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(3);
    }

    @Test
    void enforcesStatusAndParticipantRules() throws Exception {
        UUID id = createConversation("conversation-rules");

        send(id, "agent", 101, "agent-before-assignment", "Hello")
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE conversations SET status='ASSIGNED', agent_id=101 WHERE id=?", id);
        send(id, "agent", 102, "wrong-agent", "Hello")
                .andExpect(status().isForbidden());
        send(id, "agent", 101, "right-agent", "Hello")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderType").value("AGENT"));
        jdbc.update("UPDATE conversations SET status='CLOSED' WHERE id=?", id);
        send(id, "customer", 1, "after-close", "No")
                .andExpect(status().isConflict());
    }

    @Test
    void pagesMessagesInSequenceOrder() throws Exception {
        UUID id = createConversation("conversation-pages");
        send(id, "customer", 1, "m2", "two").andExpect(status().isCreated());
        send(id, "customer", 1, "m3", "three").andExpect(status().isCreated());
        send(id, "customer", 1, "m4", "four").andExpect(status().isCreated());

        mockMvc.perform(get("/conversations/{id}/messages", id)
                        .headers(identity("customer", 1)).param("afterSeq", "1").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].messageSeq").value(2))
                .andExpect(jsonPath("$.messages[1].messageSeq").value(3))
                .andExpect(jsonPath("$.nextAfterSeq").value(3))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/conversations/{id}/messages", id)
                        .headers(identity("customer", 1)).param("beforeSeq", "4").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].messageSeq").value(2))
                .andExpect(jsonPath("$.messages[1].messageSeq").value(3))
                .andExpect(jsonPath("$.nextBeforeSeq").value(2));

        mockMvc.perform(get("/conversations/{id}/messages", id)
                        .headers(identity("customer", 1)).param("afterSeq", "1").param("beforeSeq", "4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsOnlyCurrentAgentsAssignedInbox() throws Exception {
        UUID own = createConversation("own-inbox");
        UUID other = createConversation("other-inbox");
        jdbc.update("UPDATE conversations SET status='ASSIGNED', agent_id=101 WHERE id=?", own);
        jdbc.update("UPDATE conversations SET status='ASSIGNED', agent_id=102 WHERE id=?", other);

        mockMvc.perform(get("/conversation/agent/me").headers(identity("agent", 101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations.length()").value(1))
                .andExpect(jsonPath("$.conversations[0].id").value(own.toString()));
        mockMvc.perform(get("/conversation/agent/me").headers(identity("customer", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void serializesConcurrentMessagesIntoDistinctSequences() throws Exception {
        UUID id = createConversation("concurrent-messages");
        int count = 6;
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return send(id, "customer", 1, "parallel-" + index, "message-" + index)
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
                }));
            }
            start.countDown();
            Set<Long> sequences = futures.stream().map(future -> {
                try { return objectMapper.readTree(future.get()).get("messageSeq").asLong(); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).collect(Collectors.toSet());
            assertThat(sequences).containsExactlyInAnyOrder(2L, 3L, 4L, 5L, 6L, 7L);
            assertThat(jdbc.queryForObject("SELECT last_message_seq FROM conversations WHERE id=?", Long.class, id))
                    .isEqualTo(7L);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createConversation(String clientMessageId) throws Exception {
        String body = mockMvc.perform(post("/conversations")
                        .headers(identity("customer", 1))
                        .header("Idempotency-Key", "key-" + clientMessageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientMessageId":"%s","message":"first","channel":"webchat","skill":"support"}
                                """.formatted(clientMessageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return UUID.fromString(json.get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions send(
            UUID id, String role, long userId, String clientMessageId, String content) throws Exception {
        return mockMvc.perform(post("/conversations/{id}/messages", id)
                .headers(identity(role, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientMessageId":"%s","content":"%s","contentType":"text/plain"}
                        """.formatted(clientMessageId, content)));
    }

    private org.springframework.http.HttpHeaders identity(String role, long userId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", Long.toString(userId));
        headers.set("X-User-Role", role);
        return headers;
    }
}
