package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** Conta de acesso: a consultoria (ADMIN) ou um cliente (CLIENT) com o plano dele. */
@Entity
@Table(name = "client_account")
public class ClientAccount {

    /** Tentativas erradas permitidas dentro da janela, antes do bloqueio. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /** Janela de contagem das tentativas e duração do bloqueio. */
    public static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String company;
    private String segment;

    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Column(name = "plan_file")
    private String planFile;

    @Column(name = "plan_updated_at")
    private Instant planUpdatedAt;

    @Column(nullable = false)
    private boolean suspended;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "first_failed_at")
    private Instant firstFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "token_hash")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_purpose", length = 10)
    private TokenPurpose tokenPurpose;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClientAccount() {
    }

    public ClientAccount(Role role, String name, String email, Instant createdAt) {
        this.role = role;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
    }

    // ---------- regras de acesso ----------

    /** Conta bloqueada por excesso de tentativas? */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Registra uma senha errada; bloqueia ao atingir o limite dentro da janela. */
    public void registerFailedLogin(Instant now) {
        boolean windowExpired = firstFailedAt == null
                || Duration.between(firstFailedAt, now).compareTo(LOCK_WINDOW) > 0;
        if (windowExpired) {
            failedAttempts = 1;
            firstFailedAt = now;
        } else {
            failedAttempts++;
        }
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_WINDOW);
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        firstFailedAt = null;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public void issueToken(String hash, TokenPurpose purpose, Instant expiresAt) {
        this.tokenHash = hash;
        this.tokenPurpose = purpose;
        this.tokenExpiresAt = expiresAt;
    }

    public boolean hasValidTokenAt(Instant now) {
        return tokenHash != null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
    }

    public void clearToken() {
        this.tokenHash = null;
        this.tokenPurpose = null;
        this.tokenExpiresAt = null;
    }

    /** Define a nova senha (já cifrada) e libera a conta de bloqueios anteriores. */
    public void setNewPassword(String hash) {
        this.passwordHash = hash;
        this.failedAttempts = 0;
        this.firstFailedAt = null;
        this.lockedUntil = null;
        clearToken();
    }

    public AccountPrincipal toPrincipal() {
        return new AccountPrincipal(id, email, name, role);
    }

    // ---------- acessores ----------

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getCompany() { return company; }
    public String getSegment() { return segment; }
    public int getPlanVersion() { return planVersion; }
    public String getPlanFile() { return planFile; }
    public Instant getPlanUpdatedAt() { return planUpdatedAt; }
    public boolean isSuspended() { return suspended; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public TokenPurpose getTokenPurpose() { return tokenPurpose; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setCompany(String company) { this.company = company; }
    public void setSegment(String segment) { this.segment = segment; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public void setPlanFile(String planFile) { this.planFile = planFile; }
    public void setPlanUpdatedAt(Instant planUpdatedAt) { this.planUpdatedAt = planUpdatedAt; }
}
