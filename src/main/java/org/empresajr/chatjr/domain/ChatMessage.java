package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private MessageRole role;

    @Column(nullable = false)
    private String html;

    @Column(length = 200)
    private String source;

    @Column(nullable = false)
    private boolean inference;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long conversationId, MessageRole role, String html, String source, boolean inference,
                       boolean degraded, Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.html = html;
        this.source = source;
        this.inference = inference;
        this.degraded = degraded;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public MessageRole getRole() { return role; }
    public String getHtml() { return html; }
    public String getSource() { return source; }
    public boolean isInference() { return inference; }
    public boolean isDegraded() { return degraded; }
    public Instant getCreatedAt() { return createdAt; }
}
