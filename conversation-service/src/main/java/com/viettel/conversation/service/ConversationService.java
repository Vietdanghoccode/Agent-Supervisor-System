package com.viettel.conversation.service;

import com.viettel.conversation.api.ConversationResponse;
import com.viettel.conversation.api.CreateConversationRequest;
import com.viettel.conversation.domain.Conversation;
import com.viettel.conversation.domain.Message;
import com.viettel.conversation.exception.BadRequestException;
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

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               ConversationCreationTransaction creationTransaction,
                               IdempotencyFingerprint fingerprint) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.creationTransaction = creationTransaction;
        this.fingerprint = fingerprint;
    }

    public CreateConversationResult create(String idempotencyKey, CreateConversationRequest request) {
        validateIdempotencyKey(idempotencyKey);
        NormalizedCreateRequest normalized = NormalizedCreateRequest.from(request);
        String requestHash = fingerprint.calculate(normalized);

        return conversationRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> createOrResolveRace(idempotencyKey, requestHash, normalized));
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

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Header Idempotency-Key is required");
        }
        if (key.length() > 255) {
            throw new BadRequestException("Header Idempotency-Key must not exceed 255 characters");
        }
    }
}
