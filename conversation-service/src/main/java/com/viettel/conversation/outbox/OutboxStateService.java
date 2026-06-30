package com.viettel.conversation.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true")
class OutboxStateService {
    private static final int MAX_ERROR_LENGTH = 4_000;

    private final OutboxJdbcRepository repository;
    private final OutboxPublisherProperties properties;
    private final Clock clock = Clock.systemUTC();

    OutboxStateService(OutboxJdbcRepository repository, OutboxPublisherProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claim(String workerId) {
        return repository.claim(properties.supportedEventTypes(), properties.batchSize(), workerId,
                clock.instant(), properties.leaseDuration());
    }

    @Transactional
    public boolean markPublished(UUID eventId, String workerId) {
        return repository.markPublished(eventId, workerId, clock.instant()) == 1;
    }

    @Transactional
    public boolean markForRetry(ClaimedOutboxEvent event, String workerId, Throwable failure) {
        Instant nextAttemptAt = clock.instant().plus(retryDelay(event.attemptCount()));
        String error = failure.toString();
        if (error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        return repository.markForRetry(event.id(), workerId, nextAttemptAt, error) == 1;
    }

    private Duration retryDelay(int attemptCount) {
        long initialMillis = properties.retryInitialDelay().toMillis();
        long maximumMillis = properties.retryMaxDelay().toMillis();
        int exponent = Math.min(Math.max(0, attemptCount - 1), 30);
        long delay;
        try {
            delay = Math.multiplyExact(initialMillis, 1L << exponent);
        } catch (ArithmeticException exception) {
            delay = maximumMillis;
        }
        return Duration.ofMillis(Math.min(delay, maximumMillis));
    }
}
