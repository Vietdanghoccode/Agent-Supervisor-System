package com.viettel.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettel.conversation.api.ConversationResponse;
import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.Message;
import com.viettel.conversation.domain.SenderType;
import com.viettel.conversation.domain.OutboxEvent;
import com.viettel.conversation.repository.ConversationRepository;
import com.viettel.conversation.repository.MessageRepository;
import com.viettel.conversation.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
class ConversationCreationTransaction {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ConversationCreationTransaction(ConversationRepository conversationRepository,
                                    MessageRepository messageRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public ConversationResponse create(String idempotencyKey, String requestHash,
                                       NormalizedCreateRequest request) {
        // PostgreSQL stores timestamptz with microsecond precision. Normalize before
        // building the first response so an idempotent replay is byte-for-byte stable.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID conversationEventId = UUID.randomUUID();
        UUID messageEventId = UUID.randomUUID();

        Conversation conversation = new Conversation(conversationId, request.customerId(),
                request.channel(), request.skill(), idempotencyKey, requestHash, now);
        conversationRepository.saveAndFlush(conversation);

        long sequence = conversation.appendMessage(now);
        conversationRepository.save(conversation);
        Message message = new Message(messageId, conversationId, SenderType.CUSTOMER,
                request.customerId().toString(), request.clientMessageId(), request.message(),
                "text/plain", sequence, now);
        messageRepository.save(message);

        outboxEventRepository.save(new OutboxEvent(conversationEventId, "Conversation", conversationId,
                "ConversationCreated", serializeConversationEvent(conversationEventId, conversationId, request, now), now));
        outboxEventRepository.save(new OutboxEvent(messageEventId, "Message", messageId,
                "MessageCreated", serializeMessageEvent(messageEventId, message, now), now));
        outboxEventRepository.flush();

        return ConversationResponse.from(conversation, message);
    }

    private String serializeConversationEvent(UUID eventId, UUID conversationId,
                                              NormalizedCreateRequest request, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", "ConversationCreated");
        payload.put("conversationId", conversationId);
        payload.put("customerId", request.customerId());
        payload.put("channel", request.channel());
        payload.put("skill", request.skill());
        payload.put("occurredAt", occurredAt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize ConversationCreated event", exception);
        }
    }

    private String serializeMessageEvent(UUID eventId, Message message, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", "MessageCreated");
        payload.put("messageId", message.getId());
        payload.put("conversationId", message.getConversationId());
        payload.put("senderType", message.getSenderType());
        payload.put("senderId", message.getSenderId());
        payload.put("clientMessageId", message.getClientMessageId());
        payload.put("contentType", message.getContentType());
        payload.put("content", message.getContent());
        payload.put("messageSeq", message.getMessageSeq());
        payload.put("status", message.getStatus());
        payload.put("occurredAt", occurredAt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize MessageCreated event", exception);
        }
    }
}
