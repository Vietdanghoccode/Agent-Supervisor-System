package com.viettel.conversation.repository;

import com.viettel.conversation.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByIdempotencyKey(String idempotencyKey);
}
