package com.viettel.conversation.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.outbox.publisher")
public record OutboxPublisherProperties(
        String topic,
        List<String> supportedEventTypes,
        int batchSize,
        Duration leaseDuration,
        Duration retryInitialDelay,
        Duration retryMaxDelay) {
}
