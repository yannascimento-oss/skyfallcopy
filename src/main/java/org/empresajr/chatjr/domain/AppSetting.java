package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Configuração operacional gravada no banco (editável pela tela de administração). */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 4000)
    private String value;

    @Column(nullable = false)
    private boolean secret;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppSetting() {
    }

    public AppSetting(String key, String value, boolean secret, Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.secret = secret;
        this.updatedAt = updatedAt;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public boolean isSecret() { return secret; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String value, boolean secret, Instant updatedAt) {
        this.value = value;
        this.secret = secret;
        this.updatedAt = updatedAt;
    }
}
