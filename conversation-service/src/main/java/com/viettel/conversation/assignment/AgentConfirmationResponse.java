package com.viettel.conversation.assignment;

import java.util.UUID;

public record AgentConfirmationResponse(UUID conversationId, long agentId, String status) {
}
