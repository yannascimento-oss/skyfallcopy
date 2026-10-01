package org.empresajr.chatjr.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.PasswordPolicy;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Login, sessão e logout.
 *
 * Esta classe NÃO é transacional de propósito: a contagem de tentativas erradas precisa ser gravada
 * mesmo quando em seguida lançamos o erro 401 (uma transação externa desfaria o registro).
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "E-mail ou senha incorretos.";

    private final ClientAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;
    private final SecurityContextRepository contextRepository;
    /** Hash falso, usado para gastar o mesmo tempo quando o e-mail não existe (evita descobrir contas pelo tempo de resposta). */
    private final String dummyHash;

    public AuthService(ClientAccountRepository accounts, PasswordEncoder encoder, SettingsService settings,
                       AuditService audit, Clock clock, SecurityContextRepository contextRepository) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
        this.contextRepository = contextRepository;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public AccountPrincipal login(String rawEmail, String password, HttpServletRequest request,
                                  HttpServletResponse response) {
        if (!settings.isSetupDone()) {
            throw new ApiException(HttpStatus.CONFLICT, "A instalação inicial ainda não foi concluída.");
        }
        String email = AccountService.normalizeEmail(rawEmail);
        Instant now = clock.instant();

        ClientAccount account = accounts.findByEmail(email).orElse(null);
        if (account == null) {
            encoder.matches(password, dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (account.isLockedAt(now)) {
            long minutes = Duration.between(now, account.getLockedUntil()).toMinutes() + 1;
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas incorretas. Tente novamente em " + minutes + " minuto(s).");
        }

        boolean passwordOk;
        if (account.hasPassword()) {
            passwordOk = encoder.matches(password, account.getPasswordHash());
        } else {
            // Convite ainda não aceito: gasta o mesmo tempo e recusa.
            encoder.matches(password, dummyHash);
            passwordOk = false;
        }
        if (!passwordOk) {
            account.registerFailedLogin(now);
            accounts.save(account);
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        // O aviso de suspensão só aparece para quem acertou a senha, para não revelar quais contas existem.
        if (account.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Este acesso está suspenso. Fale com a consultoria da Empresa JR.");
        }

        account.registerSuccessfulLogin(now);
        accounts.save(account);
        AccountPrincipal principal = account.toPrincipal();
        startSession(principal, request, response);
        audit.recordAs(principal.id(), principal.email(), "LOGIN", account.getId(), null, null, null);
        return principal;
    }

    /**
     * Troca de senha com a sessão aberta. Errar a senha atual conta como tentativa de login (mesmo bloqueio),
     * para a sessão aberta não servir de atalho para adivinhar a senha. Sem @Transactional, pelo mesmo motivo do login.
     */
    public void changePassword(Long accountId, String currentPassword, String newPassword) {
        ClientAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
        Instant now = clock.instant();
        if (account.isLockedAt(now)) {
            long minutes = Duration.between(now, account.getLockedUntil()).toMinutes() + 1;
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas incorretas. Tente novamente em " + minutes + " minuto(s).");
        }
        if (!account.hasPassword() || !encoder.matches(currentPassword, account.getPasswordHash())) {
            account.registerFailedLogin(now);
            accounts.save(account);
            throw new ApiException(HttpStatus.BAD_REQUEST, "A senha atual está incorreta.");
        }
        PasswordPolicy.check(newPassword, account.getEmail()).ifPresent(message -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        });
        if (encoder.matches(newPassword, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A nova senha precisa ser diferente da atual.");
        }
        account.setNewPassword(encoder.encode(newPassword));
        accounts.save(account);
        audit.recordAs(account.getId(), account.getEmail(), "PASSWORD_CHANGED", account.getId(), null, null, null);
    }

    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void startSession(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        // Troca o id da sessão no login (proteção contra fixação de sessão).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
