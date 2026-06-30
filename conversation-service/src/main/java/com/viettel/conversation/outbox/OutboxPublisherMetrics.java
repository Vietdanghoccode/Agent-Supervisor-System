package com.viettel.conversation.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true")
class OutboxPublisherMetrics {
    private final OutboxJdbcRepository repository;
    private final OutboxPublisherProperties properties;
    private final Counter published;
    private final Counter retries;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final Clock clock = Clock.systemUTC();

    OutboxPublisherMetrics(OutboxJdbcRepository repository,
                           OutboxPublisherProperties properties,
                           MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.published = Counter.builder("outbox.publisher.published")
                .description("Outbox events acknowledged by Kafka and marked published")
                .register(meterRegistry);
        this.retries = Counter.builder("outbox.publisher.retries")
                .description("Outbox publish attempts returned to pending")
                .register(meterRegistry);
        Gauge.builder("outbox.publisher.backlog", backlog, AtomicLong::get)
                .description("Supported outbox events awaiting completion")
                .register(meterRegistry);
        Gauge.builder("outbox.publisher.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest supported unfinished outbox event")
                .register(meterRegistry);
    }

    void published() {
        published.increment();
    }

    void retried() {
        retries.increment();
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.metrics-interval:10s}")
    void refreshBacklog() {
        backlog.set(repository.pendingCount(properties.supportedEventTypes()));
        oldestAgeSeconds.set(repository.oldestPendingAgeSeconds(
                properties.supportedEventTypes(), clock.instant()));
    }
}
