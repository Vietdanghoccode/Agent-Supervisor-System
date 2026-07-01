package com.viettel.conversation.assignment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(AssignmentWorkerProperties.class)
class AssignmentPropertiesConfiguration {
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.assignment.worker.enabled", havingValue = "true")
class AssignmentWorkerConfiguration {
}
