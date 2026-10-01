package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Trilha de auditoria: quem fez o quê, em qual cliente e em qual aba. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 200)
    private String actorEmail;

    @Column(name = "client_id")
    private Long clientId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "tab_id")
    private Long tabId;

    @Column(name = "tab_name", length = 160)
    private String tabName;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long actorId, String actorEmail, Long clientId, String action,
                    Long tabId, String tabName, String detail, Instant createdAt) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.clientId = clientId;
        this.action = action;
        this.tabId = tabId;
        this.tabName = tabName;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public Long getClientId() { return clientId; }
    public String getAction() { return action; }
    public Long getTabId() { return tabId; }
    public String getTabName() { return tabName; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
