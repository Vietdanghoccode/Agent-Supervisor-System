package com.viettel.conversation.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true")
class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final byte[] CONTENT_TYPE = "application/json".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_VERSION = "1".getBytes(StandardCharsets.UTF_8);

    private final OutboxStateService stateService;
    private final OutboxPublisherProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublisherMetrics metrics;
    private final String workerId;
    private final AtomicBoolean batchInFlight = new AtomicBoolean();

    OutboxPublisher(OutboxStateService stateService,
                    OutboxPublisherProperties properties,
                    KafkaTemplate<String, String> kafkaTemplate,
                    OutboxPublisherMetrics metrics) {
        this.stateService = stateService;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.workerId = createWorkerId();
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-interval:500ms}")
    void publishNextBatch() {
        if (!batchInFlight.compareAndSet(false, true)) {
            return;
        }

        List<ClaimedOutboxEvent> events;
        try {
            events = stateService.claim(workerId);
        } catch (RuntimeException exception) {
            batchInFlight.set(false);
            log.error("Outbox claim failed workerId={}", workerId, exception);
            return;
        }
        if (events.isEmpty()) {
            batchInFlight.set(false);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(events.size());
        for (ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(record(event)).whenComplete((result, failure) -> {
                    try {
                        if (failure == null) {
                            completePublished(event);
                        } else {
                            completeFailure(event, unwrap(failure));
                        }
                    } catch (RuntimeException exception) {
                        log.error("Could not persist outbox publish result eventId={} workerId={}",
                                event.id(), workerId, exception);
                    } finally {
                        finishOne(remaining);
                    }
                });
            } catch (RuntimeException exception) {
                try {
                    completeFailure(event, exception);
                } catch (RuntimeException persistenceException) {
                    log.error("Could not persist outbox send failure eventId={} workerId={}",
                            event.id(), workerId, persistenceException);
                } finally {
                    finishOne(remaining);
                }
            }
        }
    }

    private ProducerRecord<String, String> record(ClaimedOutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(properties.topic(),
                event.aggregateId().toString(), event.payload());
        Headers headers = record.headers();
        headers.add("eventId", event.id().toString().getBytes(StandardCharsets.UTF_8));
        headers.add("eventType", event.eventType().getBytes(StandardCharsets.UTF_8));
        headers.add("contentType", CONTENT_TYPE);
        headers.add("eventVersion", EVENT_VERSION);
        return record;
    }

    private void completePublished(ClaimedOutboxEvent event) {
        if (stateService.markPublished(event.id(), workerId)) {
            metrics.published();
            log.info("Outbox event published eventId={} eventType={} attempt={} workerId={}",
                    event.id(), event.eventType(), event.attemptCount(), workerId);
        } else {
            log.warn("Ignored stale outbox success eventId={} workerId={}", event.id(), workerId);
        }
    }

    private void completeFailure(ClaimedOutboxEvent event, Throwable failure) {
        if (stateService.markForRetry(event, workerId, failure)) {
            metrics.retried();
            log.warn("Outbox event scheduled for retry eventId={} eventType={} attempt={} workerId={} error={}",
                    event.id(), event.eventType(), event.attemptCount(), workerId, failure.toString());
        } else {
            log.warn("Ignored stale outbox failure eventId={} workerId={}", event.id(), workerId);
        }
    }

    private void finishOne(AtomicInteger remaining) {
        if (remaining.decrementAndGet() == 0) {
            batchInFlight.set(false);
        }
    }

    private Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    private String createWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID();
    }
}
