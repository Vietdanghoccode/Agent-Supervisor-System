package com.viettel.conversation.api;

import com.viettel.conversation.domain.Message;
import com.viettel.conversation.domain.SenderType;
import com.viettel.conversation.domain.MessageStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        SenderType senderType,
        String senderId,
        String clientMessageId,
        String content,
        String contentType,
        long messageSeq,
        MessageStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getSenderType(), message.getSenderId(),
                message.getClientMessageId(), message.getContent(), message.getContentType(),
                message.getMessageSeq(), message.getStatus(), message.getCreatedAt(), message.getUpdatedAt());
    }
}
