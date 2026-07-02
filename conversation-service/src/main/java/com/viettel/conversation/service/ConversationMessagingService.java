package com.viettel.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettel.conversation.api.AgentConversationPageResponse;
import com.viettel.conversation.api.AgentConversationResponse;
import com.viettel.conversation.api.MessagePageResponse;
import com.viettel.conversation.api.MessageResponse;
import com.viettel.conversation.api.SendMessageRequest;
import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.ConversationStatus;
import com.viettel.conversation.domain.Message;
import com.viettel.conversation.domain.OutboxEvent;
import com.viettel.conversation.domain.SenderType;
import com.viettel.conversation.exception.BadRequestException;
import com.viettel.conversation.exception.ConversationConflictException;
import com.viettel.conversation.exception.ConversationNotFoundException;
import com.viettel.conversation.exception.ForbiddenException;
import com.viettel.conversation.exception.IdempotencyConflictException;
import com.viettel.conversation.repository.ConversationRepository;
import com.viettel.conversation.repository.MessageRepository;
import com.viettel.conversation.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationMessagingService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public ConversationMessagingService(ConversationRepository conversations, MessageRepository messages,
                                        OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageSendResult send(UUID conversationId, RequestIdentity identity, SendMessageRequest request) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        authorizeSend(conversation, identity);
        String clientId = request.clientMessageId().trim();
        String contentType = normalizeContentType(request.contentType());

        var existing = messages.findByConversationIdAndSenderIdAndClientMessageId(
                conversationId, Long.toString(identity.userId()), clientId);
        if (existing.isPresent()) {
            Message message = existing.get();
            if (message.getSenderType() != identity.senderType()
                    || !message.getContent().equals(request.content())
                    || !message.getContentType().equals(contentType)) {
                throw new IdempotencyConflictException(
                        "clientMessageId was already used by this sender with a different payload");
            }
            return new MessageSendResult(MessageResponse.from(message), true);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Message message = new Message(UUID.randomUUID(), conversationId, identity.senderType(),
                Long.toString(identity.userId()), clientId, request.content(), contentType,
                conversation.appendMessage(now), now);
        messages.save(message);
        conversations.save(conversation);
        UUID eventId = UUID.randomUUID();
        outbox.save(new OutboxEvent(eventId, "Message", message.getId(), "MessageCreated",
                serializeMessageEvent(eventId, message, now), now));
        outbox.flush();
        return new MessageSendResult(MessageResponse.from(message), false);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse listMessages(UUID conversationId, RequestIdentity identity,
                                            Long afterSeq, Long beforeSeq, int limit) {
        validateLimit(limit);
        if (afterSeq != null && beforeSeq != null) {
            throw new BadRequestException("afterSeq and beforeSeq cannot be used together");
        }
        if ((afterSeq != null && afterSeq < 0) || (beforeSeq != null && beforeSeq < 1)) {
            throw new BadRequestException("Message sequence cursor is invalid");
        }
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        authorizeParticipant(conversation, identity);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Message> found;
        boolean backwards = beforeSeq != null;
        if (afterSeq != null) {
            found = messages.findByConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                    conversationId, afterSeq, page);
        } else if (beforeSeq != null) {
            found = messages.findByConversationIdAndMessageSeqLessThanOrderByMessageSeqDesc(
                    conversationId, beforeSeq, page);
        } else {
            found = messages.findByConversationIdOrderByMessageSeqAsc(conversationId, page);
        }
        boolean hasMore = found.size() > limit;
        if (hasMore) found = new ArrayList<>(found.subList(0, limit));
        if (backwards) Collections.reverse(found);
        List<MessageResponse> response = found.stream().map(MessageResponse::from).toList();
        Long nextAfter = !backwards && hasMore && !found.isEmpty() ? found.get(found.size() - 1).getMessageSeq() : null;
        Long nextBefore = backwards && hasMore && !found.isEmpty() ? found.get(0).getMessageSeq() : null;
        return new MessagePageResponse(response, nextAfter, nextBefore, hasMore);
    }

    @Transactional(readOnly = true)
    public AgentConversationPageResponse agentInbox(RequestIdentity identity, String cursor, int limit) {
        validateLimit(limit);
        if (!"agent".equals(identity.role())) throw new ForbiddenException("Only agents can access the agent inbox");
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Conversation> found;
        if (cursor == null || cursor.isBlank()) {
            found = conversations.findAgentInbox(identity.userId(), page);
        } else {
            InboxCursor decoded = decodeCursor(cursor);
            found = conversations.findAgentInboxAfter(identity.userId(), decoded.time(), decoded.id(), page);
        }
        boolean hasMore = found.size() > limit;
        if (hasMore) found = found.subList(0, limit);
        String next = null;
        if (hasMore && !found.isEmpty()) {
            Conversation last = found.get(found.size() - 1);
            next = encodeCursor(last.getLastMessageAt() == null ? last.getCreatedAt() : last.getLastMessageAt(), last.getId());
        }
        return new AgentConversationPageResponse(found.stream().map(AgentConversationResponse::from).toList(), next, hasMore);
    }

    public void authorizeParticipant(Conversation conversation, RequestIdentity identity) {
        boolean allowed = ("customer".equals(identity.role()) && conversation.getCustomerId() == identity.userId())
                || ("agent".equals(identity.role()) && conversation.getAgentId() != null
                && conversation.getAgentId() == identity.userId());
        if (!allowed) throw new ForbiddenException("User is not a participant of this conversation");
    }

    private void authorizeSend(Conversation conversation, RequestIdentity identity) {
        authorizeParticipant(conversation, identity);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ConversationConflictException("Closed conversation does not accept messages");
        }
        if (identity.senderType() == SenderType.AGENT && conversation.getStatus() != ConversationStatus.ASSIGNED) {
            throw new ConversationConflictException("Agent can only send messages to an assigned conversation");
        }
    }

    private String normalizeContentType(String value) {
        String type = value == null || value.isBlank() ? "text/plain" : value.trim().toLowerCase();
        if (!"text/plain".equals(type)) throw new BadRequestException("Only text/plain contentType is supported");
        return type;
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) throw new BadRequestException("limit must be between 1 and 100");
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
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize MessageCreated event", exception); }
    }

    private String encodeCursor(Instant time, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private InboxCursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new InboxCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new BadRequestException("cursor is invalid");
        }
    }

    private ConversationNotFoundException notFound(UUID id) {
        return new ConversationNotFoundException("Conversation " + id + " does not exist");
    }

    private record InboxCursor(Instant time, UUID id) { }
}
