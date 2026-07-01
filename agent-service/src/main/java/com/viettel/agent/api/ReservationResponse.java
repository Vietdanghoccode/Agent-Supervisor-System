package com.viettel.agent.api;

import java.util.UUID;

public record ReservationResponse(
        UUID conversationId,
        Long agentId,
        String status,
        long reservationTtlSeconds) {
}
