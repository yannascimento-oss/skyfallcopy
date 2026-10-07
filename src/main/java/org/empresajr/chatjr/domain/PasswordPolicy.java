package org.empresajr.chatjr.domain;

import java.util.Locale;
import java.util.Optional;

/** Regras de senha. Devolve a mensagem de erro em português, ou vazio quando a senha é aceita. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 100;

    private PasswordPolicy() {
    }

    public static Optional<String> check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("A senha precisa ter pelo menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("A senha pode ter no máximo " + MAX_LENGTH + " caracteres.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return Optional.of("A senha precisa misturar letras e números.");
        }
        if (email != null && password.toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))) {
            return Optional.of("A senha não pode ser igual ao e-mail.");
        }
        return Optional.empty();
    }
}
