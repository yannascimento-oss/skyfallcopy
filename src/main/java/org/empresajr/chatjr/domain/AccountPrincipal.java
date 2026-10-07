package org.empresajr.chatjr.domain;

import java.io.Serializable;

/** Identidade guardada na sessão. Só dados que não mudam durante a sessão. */
public record AccountPrincipal(Long id, String email, String name, Role role) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
