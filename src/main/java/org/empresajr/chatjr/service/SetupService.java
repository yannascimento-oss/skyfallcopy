package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.Role;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Instalação inicial: cria o primeiro administrador e grava os dados da consultoria. Só roda uma vez. */
@Service
public class SetupService {

    private final Object lock = new Object();
    private final AccountService accountService;
    private final ClientAccountRepository accounts;
    private final SettingsService settings;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public SetupService(AccountService accountService, ClientAccountRepository accounts, SettingsService settings,
                        AuditService audit, TransactionTemplate transaction) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.settings = settings;
        this.audit = audit;
        this.transaction = transaction;
    }

    public void complete(String orgName, String adminName, String adminEmail, String password, String aiKey) {
        // O bloqueio fica FORA da transação: assim a segunda chamada só entra depois do commit da primeira.
        synchronized (lock) {
            transaction.executeWithoutResult(status -> {
                if (settings.isSetupDone() || accounts.existsByRole(Role.ADMIN)) {
                    throw new ApiException(HttpStatus.CONFLICT, "A instalação inicial já foi concluída.");
                }
                ClientAccount admin = accountService.createAdmin(adminName, adminEmail, password);
                settings.put(SettingsService.ORG_NAME, orgName.trim());
                if (aiKey != null && !aiKey.isBlank()) {
                    settings.putSecret(SettingsService.AI_KEY, aiKey.trim());
                }
                settings.put(SettingsService.SETUP_DONE, "true");
                audit.recordAs(admin.getId(), admin.getEmail(), "SETUP", null, null, null,
                        "Instalação inicial concluída");
            });
        }
    }
}
