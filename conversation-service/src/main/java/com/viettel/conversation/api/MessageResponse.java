package com.viettel.conversation.api;

import com.viettel.conversation.domain.Message;
import com.viettel.conversation.domain.SenderType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        SenderType senderType,
        String content,
        Instant createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getSenderType(),
                message.getContent(), message.getCreatedAt());
    }
}
