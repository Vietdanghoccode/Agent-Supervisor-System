package com.viettel.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.outbox.publisher.enabled=true",
        "app.outbox.publisher.poll-interval=100ms",
        "app.outbox.publisher.metrics-interval=1h",
        "app.outbox.publisher.retry-initial-delay=100ms",
        "app.outbox.publisher.retry-max-delay=1s"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxPublisherIntegrationTest extends PostgresIntegrationTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

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
    void publishesConversationCreatedWithStableContractAndMarksItPublished() throws Exception {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("conversation.events"));

            String response = mockMvc.perform(post("/conversations")
                            .header("Idempotency-Key", "publish-1")
                            .header("X-User-Id", "1")
                            .header("X-User-Role", "customer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientMessageId":"publish-initial","message":"Help","channel":"webchat","skill":"support"}
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String conversationId = objectMapper.readTree(response).get("id").asText();
            UUID expectedEventId = jdbcTemplate.queryForObject(
                    "SELECT id FROM outbox_events WHERE aggregate_id = ? AND event_type='ConversationCreated'", UUID.class,
                    UUID.fromString(conversationId));

            ConsumerRecord<String, String> record = pollEvent(
                    consumer, expectedEventId, Duration.ofSeconds(15));
            JsonNode payload = objectMapper.readTree(record.value());
            String eventId = payload.get("eventId").asText();

            assertThat(record.topic()).isEqualTo("conversation.events");
            assertThat(record.key()).isEqualTo(conversationId);
            assertThat(payload.get("eventType").asText()).isEqualTo("ConversationCreated");
            assertThat(payload.get("conversationId").asText()).isEqualTo(conversationId);
            assertThat(header(record, "eventId")).isEqualTo(eventId);
            assertThat(header(record, "eventType")).isEqualTo("ConversationCreated");
            assertThat(header(record, "contentType")).isEqualTo("application/json");
            assertThat(header(record, "eventVersion")).isEqualTo("1");

            awaitStatus(UUID.fromString(eventId), "PUBLISHED", Duration.ofSeconds(10));
            Map<String, Object> state = jdbcTemplate.queryForMap("""
                    SELECT status, attempt_count, published_at, locked_by, locked_until
                    FROM outbox_events WHERE id = ?
                    """, UUID.fromString(eventId));
            assertThat(state.get("status")).isEqualTo("PUBLISHED");
            assertThat(state.get("attempt_count")).isEqualTo(1);
            assertThat(state.get("published_at")).isNotNull();
            assertThat(state.get("locked_by")).isNull();
            assertThat(state.get("locked_until")).isNull();
        }
    }

    @Test
    void leavesUnsupportedEventTypePending() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, status,
                     created_at, published_at, attempt_count, next_attempt_at)
                VALUES (?, 'Conversation', ?, 'UnsupportedEvent', '{}'::jsonb, 'PENDING',
                        ?, NULL, 0, ?)
                """, eventId, aggregateId, Timestamp.from(now), Timestamp.from(now));

        Thread.sleep(500);

        Map<String, Object> state = jdbcTemplate.queryForMap(
                "SELECT status, attempt_count FROM outbox_events WHERE id = ?", eventId);
        assertThat(state.get("status")).isEqualTo("PENDING");
        assertThat(state.get("attempt_count")).isEqualTo(0);
    }

    @Test
    void reclaimsAnExpiredProcessingLease() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "eventType", "ConversationCreated",
                "conversationId", aggregateId,
                "customerId", 1,
                "channel", "webchat",
                "skill", "support",
                "occurredAt", now));
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, status,
                     created_at, published_at, attempt_count, next_attempt_at,
                     locked_by, locked_until)
                VALUES (?, 'Conversation', ?, 'ConversationCreated', ?::jsonb, 'PROCESSING',
                        ?, NULL, 1, ?, 'dead-worker', ?)
                """, eventId, aggregateId, payload, Timestamp.from(now.minusSeconds(10)),
                Timestamp.from(now.minusSeconds(10)), Timestamp.from(now.minusSeconds(1)));

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("conversation.events"));
            ConsumerRecord<String, String> record = pollEvent(
                    consumer, eventId, Duration.ofSeconds(15));
            assertThat(header(record, "eventId")).isEqualTo(eventId.toString());
        }

        awaitStatus(eventId, "PUBLISHED", Duration.ofSeconds(10));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_events WHERE id = ?", Integer.class, eventId))
                .isEqualTo(2);
    }

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> pollEvent(KafkaConsumer<String, String> consumer,
                                                      UUID eventId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            var records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records) {
                if (eventId.toString().equals(header(record, "eventId"))) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka event " + eventId + " not received before timeout");
    }

    private void awaitStatus(UUID eventId, String expectedStatus, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);
            if (expectedStatus.equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Outbox event did not reach status " + expectedStatus);
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
