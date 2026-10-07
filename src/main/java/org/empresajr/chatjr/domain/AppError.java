package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Erro inesperado da aplicação, para a consultoria ver na tela Sistema sem abrir log de servidor. */
@Entity
@Table(name = "app_error")
public class AppError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 300)
    private String path;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppError() {
    }

    public AppError(String message, String path, Instant createdAt) {
        this.message = message.length() > 1000 ? message.substring(0, 1000) : message;
        this.path = path == null ? null : (path.length() > 300 ? path.substring(0, 300) : path);
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Instant getCreatedAt() { return createdAt; }
}
