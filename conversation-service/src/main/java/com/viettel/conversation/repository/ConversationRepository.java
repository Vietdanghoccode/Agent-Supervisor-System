package com.viettel.conversation.repository;

import com.viettel.conversation.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select c from Conversation c
            where c.status = com.viettel.conversation.domain.ConversationStatus.ASSIGNED
              and c.agentId = :agentId
            order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc
            """)
    List<Conversation> findAgentInbox(@Param("agentId") long agentId, Pageable pageable);

    @Query("""
            select c from Conversation c
            where c.status = com.viettel.conversation.domain.ConversationStatus.ASSIGNED
              and c.agentId = :agentId
              and (coalesce(c.lastMessageAt, c.createdAt) < :cursorTime
                or (coalesce(c.lastMessageAt, c.createdAt) = :cursorTime and c.id < :cursorId))
            order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc
            """)
    List<Conversation> findAgentInboxAfter(@Param("agentId") long agentId,
                                           @Param("cursorTime") Instant cursorTime,
                                           @Param("cursorId") UUID cursorId,
                                           Pageable pageable);
}
