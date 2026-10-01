package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsLongPasswordWithLettersAndDigits() {
        assertTrue(PasswordPolicy.check("Boa-senha-2026", "a@b.test").isEmpty());
    }

    @Test
    void rejectsShortPassword() {
        assertTrue(PasswordPolicy.check("Ab1", "a@b.test").orElse("").contains("pelo menos"));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertEquals("A senha precisa misturar letras e números.",
                PasswordPolicy.check("somente-letras-aqui", "a@b.test").orElse(""));
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertEquals("A senha precisa misturar letras e números.",
                PasswordPolicy.check("1234567890123", "a@b.test").orElse(""));
    }

    @Test
    void rejectsPasswordEqualToEmail() {
        assertEquals("A senha não pode ser igual ao e-mail.",
                PasswordPolicy.check("pessoa1@empresa.test", "Pessoa1@Empresa.test").orElse(""));
    }

    @Test
    void rejectsNullAndTooLongPasswords() {
        assertTrue(PasswordPolicy.check(null, "a@b.test").isPresent());
        assertTrue(PasswordPolicy.check("a1".repeat(60), "a@b.test").orElse("").contains("no máximo"));
    }
}
