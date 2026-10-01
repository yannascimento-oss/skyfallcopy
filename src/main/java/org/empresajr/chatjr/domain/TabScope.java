package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bloqueio de uma aba para um cliente. Só existem linhas de abas bloqueadas: ausência significa liberada. */
@Entity
@Table(name = "tab_scope")
public class TabScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "tab_id", nullable = false)
    private Long tabId;

    @Column(nullable = false)
    private boolean allowed;

    protected TabScope() {
    }

    public TabScope(Long clientId, Long tabId, boolean allowed) {
        this.clientId = clientId;
        this.tabId = tabId;
        this.allowed = allowed;
    }

    public Long getClientId() { return clientId; }
    public Long getTabId() { return tabId; }
    public boolean isAllowed() { return allowed; }
}
