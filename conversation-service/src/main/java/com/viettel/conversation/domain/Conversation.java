package com.viettel.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(length = 100)
    private String skill;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(UUID id, Long customerId, String channel, String skill,
                        String idempotencyKey, String requestHash, Instant now) {
        this.id = id;
        this.customerId = customerId;
        this.status = ConversationStatus.WAITING;
        this.agentId = null;
        this.channel = channel;
        this.skill = skill;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public ConversationStatus getStatus() { return status; }
    public Long getAgentId() { return agentId; }
    public String getChannel() { return channel; }
    public String getSkill() { return skill; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}
