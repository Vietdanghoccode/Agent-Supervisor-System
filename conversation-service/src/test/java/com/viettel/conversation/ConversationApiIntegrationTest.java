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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationApiIntegrationTest extends PostgresIntegrationTest {
    private static final String REQUEST = """
            {"customerId":1,"message":"Tôi cần hỗ trợ","channel":" webchat ","skill":" support "}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
    }

    @Test
    void createsConversationMessageAndOutboxAtomically() throws Exception {
        MvcResult result = create("create-1", REQUEST)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.agentId").doesNotExist())
                .andExpect(jsonPath("$.channel").value("webchat"))
                .andExpect(jsonPath("$.skill").value("support"))
                .andExpect(jsonPath("$.initialMessage.senderType").value("CUSTOMER"))
                .andExpect(jsonPath("$.initialMessage.content").value("Tôi cần hỗ trợ"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID conversationId = UUID.fromString(response.get("id").asText());
        assertThat(count("conversations")).isEqualTo(1);
        assertThat(count("messages")).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_id FROM messages", UUID.class)).isEqualTo(conversationId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT agent_id FROM conversations", Long.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM outbox_events", String.class)).isEqualTo("ConversationCreated");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events", String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload->>'conversationId' FROM outbox_events", String.class))
                .isEqualTo(conversationId.toString());
    }

    @Test
    void replaysSameResourceForSameKeyAndPayload() throws Exception {
        String first = create("replay-1", REQUEST).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = create("replay-1", REQUEST).andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second)).isEqualTo(objectMapper.readTree(first));
        assertSingleAggregate();
    }

    @Test
    void rejectsSameKeyWithDifferentPayload() throws Exception {
        create("conflict-1", REQUEST).andExpect(status().isCreated());
        create("conflict-1", """
                {"customerId":1,"message":"Nội dung khác","channel":"webchat","skill":"support"}
                """)
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Idempotency conflict"));
        assertSingleAggregate();
    }

    @Test
    void validatesHeaderAndBodyAsProblemDetails() throws Exception {
        mockMvc.perform(post("/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));

        create("invalid-1", """
                {"customerId":0,"message":" ","channel":" "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));

        create("invalid-json", "{not-json")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
        assertThat(count("conversations")).isZero();
    }

    @Test
    void publishesLocalGatewayAsOpenApiServer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"));
    }

    @Test
    void concurrentRequestsWithSameKeyCreateOnlyOneAggregate() throws Exception {
        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return create("race-1", REQUEST).andExpect(status().isCreated()).andReturn();
                }));
            }
            ready.await();
            start.countDown();

            Set<String> conversationIds = futures.stream()
                    .map(this::getResult)
                    .map(this::getBody)
                    .map(this::parseId)
                    .collect(Collectors.toSet());
            assertThat(conversationIds).hasSize(1);
            assertSingleAggregate();
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions create(String key, String body) throws Exception {
        return mockMvc.perform(post("/conversations")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MvcResult getResult(Future<MvcResult> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String parseId(String json) {
        try {
            return objectMapper.readTree(json).get("id").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String getBody(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertSingleAggregate() {
        assertThat(count("conversations")).isEqualTo(1);
        assertThat(count("messages")).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
