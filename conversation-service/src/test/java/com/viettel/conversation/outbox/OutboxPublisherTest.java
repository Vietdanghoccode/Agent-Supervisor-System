package com.viettel.conversation.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {
    @Test
    void releasesBatchAfterKafkaFailureSoPollingCanContinue() {
        OutboxStateService stateService = mock(OutboxStateService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxPublisherMetrics metrics = mock(OutboxPublisherMetrics.class);
        ClaimedOutboxEvent event = event();
        when(stateService.claim(anyString())).thenReturn(List.of(event), List.of());
        when(stateService.markForRetry(any(), anyString(), any())).thenReturn(true);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        OutboxPublisher publisher = new OutboxPublisher(
                stateService, properties(), kafkaTemplate, metrics);
        publisher.publishNextBatch();
        publisher.publishNextBatch();

        verify(stateService, times(2)).claim(anyString());
        verify(stateService).markForRetry(any(), anyString(), any());
        verify(metrics).retried();
    }

    @Test
    void releasesBatchWhenKafkaAckCannotBePersisted() {
        OutboxStateService stateService = mock(OutboxStateService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxPublisherMetrics metrics = mock(OutboxPublisherMetrics.class);
        ClaimedOutboxEvent event = event();
        when(stateService.claim(anyString())).thenReturn(List.of(event), List.of());
        when(stateService.markPublished(any(), anyString()))
                .thenThrow(new RuntimeException("database unavailable"));
        @SuppressWarnings("unchecked")
        SendResult<String, String> result = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        OutboxPublisher publisher = new OutboxPublisher(
                stateService, properties(), kafkaTemplate, metrics);
        publisher.publishNextBatch();
        publisher.publishNextBatch();

        verify(stateService, times(2)).claim(anyString());
        verify(stateService).markPublished(any(), anyString());
    }

    private ClaimedOutboxEvent event() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        return new ClaimedOutboxEvent(eventId, aggregateId, "ConversationCreated",
                "{\"eventId\":\"" + eventId + "\",\"conversationId\":\"" + aggregateId + "\"}", 1);
    }

    private OutboxPublisherProperties properties() {
        return new OutboxPublisherProperties("conversation.events",
                List.of("ConversationCreated"), 100, Duration.ofSeconds(60),
                Duration.ofSeconds(1), Duration.ofSeconds(60));
    }
}
