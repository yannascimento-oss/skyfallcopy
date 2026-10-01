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

/** Uma pergunta feita no chat. É a base dos indicadores e do limite de perguntas por hora. */
@Entity
@Table(name = "query_log")
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false, length = 500)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "query_type", nullable = false, length = 14)
    private QueryType queryType;

    @Column(nullable = false)
    private boolean answered;

    @Column(name = "source_tab_id")
    private Long sourceTabId;

    @Column(length = 160)
    private String theme;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueryLog() {
    }

    public QueryLog(Long clientId, Long conversationId, String question, QueryType queryType, boolean answered,
                    Long sourceTabId, String theme, boolean degraded, Instant createdAt) {
        this.clientId = clientId;
        this.conversationId = conversationId;
        this.question = question.length() > 500 ? question.substring(0, 500) : question;
        this.queryType = queryType;
        this.answered = answered;
        this.sourceTabId = sourceTabId;
        this.theme = theme == null ? null : (theme.length() > 160 ? theme.substring(0, 160) : theme);
        this.degraded = degraded;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public Long getConversationId() { return conversationId; }
    public String getQuestion() { return question; }
    public QueryType getQueryType() { return queryType; }
    public boolean isAnswered() { return answered; }
    public Long getSourceTabId() { return sourceTabId; }
    public String getTheme() { return theme; }
    public boolean isDegraded() { return degraded; }
    public Instant getCreatedAt() { return createdAt; }
}
