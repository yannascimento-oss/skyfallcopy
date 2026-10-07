package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Registro de cada chamada à IA: base do custo estimado, dos limites diários e da tela Sistema. */
@Entity
@Table(name = "ai_call_log")
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private Long clientId;

    @Column(nullable = false, length = 10)
    private String kind;

    private String model;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "est_cost_microusd")
    private Long estCostMicroUsd;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCallLog() {
    }

    public AiCallLog(Long clientId, String kind, String model, String status, Integer httpStatus,
                     Integer durationMs, Integer inputTokens, Integer outputTokens, Long estCostMicroUsd,
                     String errorMessage, Instant createdAt) {
        this.clientId = clientId;
        this.kind = kind;
        this.model = model;
        this.status = status;
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estCostMicroUsd = estCostMicroUsd;
        this.errorMessage = errorMessage == null ? null : (errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public String getKind() { return kind; }
    public String getModel() { return model; }
    public String getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public Integer getDurationMs() { return durationMs; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Long getEstCostMicroUsd() { return estCostMicroUsd; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
