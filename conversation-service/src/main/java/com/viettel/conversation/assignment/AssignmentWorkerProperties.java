package com.viettel.conversation.assignment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.assignment.worker")
public record AssignmentWorkerProperties(
        String agentServiceUrl,
        String defaultSkill,
        Duration pollInterval,
        int batchSize,
        Duration assigningTimeout,
        Duration connectTimeout,
        Duration readTimeout) {
    public AssignmentWorkerProperties {
        if (agentServiceUrl == null || agentServiceUrl.isBlank()) {
            agentServiceUrl = "http://agent-service";
        }
        if (defaultSkill == null || defaultSkill.isBlank()) {
            defaultSkill = "support";
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (batchSize < 1) {
            batchSize = 50;
        }
        if (assigningTimeout == null) {
            assigningTimeout = Duration.ofSeconds(30);
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
