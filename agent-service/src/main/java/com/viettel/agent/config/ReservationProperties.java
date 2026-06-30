package com.viettel.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.reservation")
public record ReservationProperties(Duration ttl) {
    public ReservationProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofSeconds(30);
        }
    }
}
