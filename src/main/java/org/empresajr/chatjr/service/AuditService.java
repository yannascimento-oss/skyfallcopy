package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.AuditLog;
import org.empresajr.chatjr.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Grava a trilha de auditoria: autor, cliente, ação, aba e data. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Registra usando o usuário logado como autor. */
    @Transactional
    public void record(String action, Long clientId, Long tabId, String tabName, String detail) {
        AccountPrincipal actor = CurrentUser.get().orElse(null);
        recordAs(actor == null ? null : actor.id(), actor == null ? null : actor.email(),
                action, clientId, tabId, tabName, detail);
    }

    /** Registra com um autor explícito (login, instalação inicial). */
    @Transactional
    public void recordAs(Long actorId, String actorEmail, String action, Long clientId,
                         Long tabId, String tabName, String detail) {
        repository.save(new AuditLog(actorId, actorEmail, clientId, action, tabId,
                cut(tabName, 160), cut(detail, 1000), clock.instant()));
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
