package com.viettel.conversation.service;

import com.viettel.conversation.api.ConversationResponse;
import com.viettel.conversation.api.CreateConversationRequest;
import com.viettel.conversation.assignment.AgentConfirmationResponse;
import com.viettel.conversation.assignment.AgentReservationResponse;
import com.viettel.conversation.assignment.AgentServiceClient;
import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.ConversationStatus;
import com.viettel.conversation.domain.Message;
import com.viettel.conversation.exception.AgentServiceException;
import com.viettel.conversation.exception.BadRequestException;
import com.viettel.conversation.exception.ConversationConflictException;
import com.viettel.conversation.exception.ConversationNotFoundException;
import com.viettel.conversation.exception.IdempotencyConflictException;
import com.viettel.conversation.repository.ConversationRepository;
import com.viettel.conversation.repository.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationCreationTransaction creationTransaction;
    private final IdempotencyFingerprint fingerprint;
    private final AgentServiceClient agentServiceClient;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               ConversationCreationTransaction creationTransaction,
                               IdempotencyFingerprint fingerprint,
                               AgentServiceClient agentServiceClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.creationTransaction = creationTransaction;
        this.fingerprint = fingerprint;
        this.agentServiceClient = agentServiceClient;
    }

    public CreateConversationResult create(String idempotencyKey, CreateConversationRequest request) {
        validateIdempotencyKey(idempotencyKey);
        NormalizedCreateRequest normalized = NormalizedCreateRequest.from(request);
        String requestHash = fingerprint.calculate(normalized);

        return conversationRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> createOrResolveRace(idempotencyKey, requestHash, normalized));
    }

    public ConversationResponse close(java.util.UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation " + conversationId + " does not exist"));
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            return response(conversation);
        }

        cleanupAgentState(conversation);
        conversation.markClosed(java.time.Instant.now());
        Conversation saved = conversationRepository.save(conversation);
        return response(saved);
    }

    private void cleanupAgentState(Conversation conversation) {
        switch (conversation.getStatus()) {
            case WAITING -> {
            }
            case QUEUED -> agentServiceClient.cancel(conversation.getId());
            case ASSIGNED -> releaseAssigned(conversation);
            case ASSIGNING -> cleanupAssigning(conversation);
            case CLOSED -> {
            }
        }
    }

    private void cleanupAssigning(Conversation conversation) {
        AgentReservationResponse reservation = agentServiceClient.reservation(conversation.getId());
        if ("WAITING".equals(reservation.status())) {
            agentServiceClient.cancel(conversation.getId());
            return;
        }
        if ("RESERVED".equals(reservation.status())) {
            AgentConfirmationResponse confirmation = agentServiceClient.confirm(conversation.getId());
            agentServiceClient.release(confirmation.agentId(), conversation.getId());
            return;
        }
        if ("CONFIRMED".equals(reservation.status()) && reservation.agentId() != null) {
            agentServiceClient.release(reservation.agentId(), conversation.getId());
            return;
        }
        throw new AgentServiceException("Cannot close conversation with Agent reservation status " + reservation.status());
    }

    private void releaseAssigned(Conversation conversation) {
        if (conversation.getAgentId() == null) {
            throw new ConversationConflictException("Assigned conversation is missing agentId");
        }
        agentServiceClient.release(conversation.getAgentId(), conversation.getId());
    }

    private CreateConversationResult createOrResolveRace(String idempotencyKey, String requestHash,
                                                          NormalizedCreateRequest request) {
        try {
            return new CreateConversationResult(
                    creationTransaction.create(idempotencyKey, requestHash, request), false);
        } catch (DataIntegrityViolationException exception) {
            Conversation existing = conversationRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            return replay(existing, requestHash);
        }
    }

    private CreateConversationResult replay(Conversation conversation, String requestHash) {
        if (!conversation.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        Message message = messageRepository
                .findFirstByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId())
                .orElseThrow(() -> new IllegalStateException("Conversation is missing its initial message"));
        return new CreateConversationResult(ConversationResponse.from(conversation, message), true);
    }

    private ConversationResponse response(Conversation conversation) {
        Message message = messageRepository
                .findFirstByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId())
                .orElseThrow(() -> new IllegalStateException("Conversation is missing its initial message"));
        return ConversationResponse.from(conversation, message);
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Header Idempotency-Key is required");
        }
        if (key.length() > 255) {
            throw new BadRequestException("Header Idempotency-Key must not exceed 255 characters");
        }
    }
}
