package com.viettel.agent.api;

public record AgentStateResponse(
        long agentId,
        String state,
        String status,
        int currentConversations,
        int maxConversations) {
}
