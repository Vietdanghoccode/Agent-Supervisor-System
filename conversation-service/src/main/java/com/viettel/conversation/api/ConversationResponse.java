package com.viettel.conversation.api;

import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.ConversationStatus;
import com.viettel.conversation.domain.Message;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        Long customerId,
        ConversationStatus status,
        Long agentId,
        String channel,
        String skill,
        MessageResponse initialMessage,
        Instant createdAt
) {
    public static ConversationResponse from(Conversation conversation, Message message) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getCustomerId(),
                conversation.getStatus(),
                conversation.getAgentId(),
                conversation.getChannel(),
                conversation.getSkill(),
                MessageResponse.from(message),
                conversation.getCreatedAt()
        );
    }
}
