package com.viettel.conversation.repository;

import com.viettel.conversation.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findFirstByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
}
