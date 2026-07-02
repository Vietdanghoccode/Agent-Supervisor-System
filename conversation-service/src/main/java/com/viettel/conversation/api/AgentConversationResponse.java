package com.viettel.conversation.api;

import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.ConversationStatus;

import java.time.Instant;
import java.util.UUID;

public record AgentConversationResponse(
        UUID id,
        Long customerId,
        ConversationStatus status,
        Long agentId,
        String channel,
        String skill,
        long lastMessageSeq,
        Instant lastMessageAt,
        Instant createdAt
) {
    public static AgentConversationResponse from(Conversation conversation) {
        return new AgentConversationResponse(conversation.getId(), conversation.getCustomerId(),
                conversation.getStatus(), conversation.getAgentId(), conversation.getChannel(),
                conversation.getSkill(), conversation.getLastMessageSeq(),
                conversation.getLastMessageAt(), conversation.getCreatedAt());
    }
}
