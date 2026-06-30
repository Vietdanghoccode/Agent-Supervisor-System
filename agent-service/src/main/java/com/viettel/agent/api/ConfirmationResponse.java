package com.viettel.agent.api;

import java.util.UUID;

public record ConfirmationResponse(UUID conversationId, long agentId, String status) {
}
