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
@Table(name = "messages")
public class Message {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 32)
    private SenderType senderType;

    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "message_seq", nullable = false)
    private long messageSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Message() {
    }

    public Message(UUID id, UUID conversationId, SenderType senderType, String senderId,
                   String clientMessageId, String content, String contentType,
                   long messageSeq, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.contentType = contentType;
        this.messageSeq = messageSeq;
        this.status = MessageStatus.SENT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public SenderType getSenderType() { return senderType; }
    public String getSenderId() { return senderId; }
    public String getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public String getContentType() { return contentType; }
    public long getMessageSeq() { return messageSeq; }
    public MessageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
