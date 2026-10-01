package org.empresajr.chatjr.domain;

/** Motivo de um token de uso único: primeiro acesso (convite) ou redefinição de senha. */
public enum TokenPurpose {
    INVITE,
    RESET
}
