package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Pedido de acesso deixado na landing. Não cria conta: a consultoria analisa e cria o acesso, se for o caso. */
@Entity
@Table(name = "access_request")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 160)
    private String company;

    @Column(length = 40)
    private String phone;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by", length = 200)
    private String handledBy;

    protected AccessRequest() {
    }

    public AccessRequest(String name, String email, String company, String phone, String message, Instant createdAt) {
        this.name = name;
        this.email = email;
        this.company = company;
        this.phone = phone;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void markHandled(String by, Instant at) {
        this.handledBy = by;
        this.handledAt = at;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCompany() { return company; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHandledAt() { return handledAt; }
    public boolean isOpen() { return handledAt == null; }
}
