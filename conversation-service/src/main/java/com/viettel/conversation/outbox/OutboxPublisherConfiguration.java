package com.viettel.conversation.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxPublisherProperties.class)
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true")
class OutboxPublisherConfiguration {
}
