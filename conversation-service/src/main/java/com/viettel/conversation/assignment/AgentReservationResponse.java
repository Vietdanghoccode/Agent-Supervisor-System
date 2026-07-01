package com.viettel.conversation.assignment;

import java.util.UUID;

public record AgentReservationResponse(
        UUID conversationId,
        Long agentId,
        String status,
        long reservationTtlSeconds) {
}
