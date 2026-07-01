package com.viettel.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.waiting-queue")
public record WaitingQueueProperties(Duration retention) {
    public WaitingQueueProperties {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            retention = Duration.ofHours(24);
        }
    }
}
