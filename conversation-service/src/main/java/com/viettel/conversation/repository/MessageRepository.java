package com.viettel.conversation.repository;

import com.viettel.conversation.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findFirstByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(
            UUID conversationId, String senderId, String clientMessageId);
    List<Message> findByConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
            UUID conversationId, long afterSeq, Pageable pageable);
    List<Message> findByConversationIdAndMessageSeqLessThanOrderByMessageSeqDesc(
            UUID conversationId, long beforeSeq, Pageable pageable);
    List<Message> findByConversationIdOrderByMessageSeqAsc(UUID conversationId, Pageable pageable);
}
