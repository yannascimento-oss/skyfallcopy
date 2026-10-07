package org.empresajr.chatjr.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.service.AccountService;
import org.empresajr.chatjr.service.CurrentUser;
import org.empresajr.chatjr.web.JsonErrors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/** A cada chamada da API, confere se a conta da sessão ainda existe e não foi suspensa. */
@Component
public class ActiveAccountInterceptor implements HandlerInterceptor {

    private final AccountService accounts;

    public ActiveAccountInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Optional<AccountPrincipal> principal = CurrentUser.get();
        if (principal.isEmpty()) {
            return true;
        }
        ClientAccount account = accounts.findAccount(principal.get().id()).orElse(null);
        if (account == null || account.isSuspended()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            JsonErrors.write(response, 401, account == null
                    ? "Sessão inválida. Entre novamente."
                    : "Este acesso está suspenso. Fale com a consultoria da Empresa JR.");
            return false;
        }
        return true;
    }
}
