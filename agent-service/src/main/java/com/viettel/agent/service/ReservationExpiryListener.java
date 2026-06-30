package com.viettel.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ReservationExpiryListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryListener.class);
    private static final String PREFIX = "reservation:";
    private final AgentService agentService;

    public ReservationExpiryListener(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!key.startsWith(PREFIX)) {
            return;
        }
        String conversationId = key.substring(PREFIX.length());
        try {
            UUID.fromString(conversationId);
            agentService.releaseExpired(conversationId);
        } catch (RuntimeException exception) {
            log.error("Failed to release expired reservation {}", conversationId, exception);
        }
    }
}
