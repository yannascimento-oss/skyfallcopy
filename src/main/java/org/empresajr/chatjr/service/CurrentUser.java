package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Acesso à identidade da requisição atual. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AccountPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
