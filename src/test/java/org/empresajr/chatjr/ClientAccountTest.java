package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.Role;
import org.empresajr.chatjr.domain.TokenPurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAccountTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private ClientAccount account() {
        return new ClientAccount(Role.CLIENT, "Ana", "ana@exemplo.test", T0);
    }

    @Test
    void locksOnTheFifthFailureInsideTheWindow() {
        ClientAccount a = account();
        for (int i = 0; i < 4; i++) {
            a.registerFailedLogin(T0.plusSeconds(i));
        }
        assertFalse(a.isLockedAt(T0.plusSeconds(10)));
        a.registerFailedLogin(T0.plusSeconds(5));
        assertTrue(a.isLockedAt(T0.plusSeconds(10)));
        assertFalse(a.isLockedAt(T0.plus(ClientAccount.LOCK_WINDOW).plusSeconds(60)), "o bloqueio termina sozinho");
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        ClientAccount a = account();
        for (int i = 0; i < 4; i++) {
            a.registerFailedLogin(T0.plusSeconds(i));
        }
        Instant later = T0.plus(ClientAccount.LOCK_WINDOW).plusSeconds(60);
        a.registerFailedLogin(later);
        assertEquals(1, a.getFailedAttempts());
        assertFalse(a.isLockedAt(later));
    }

    @Test
    void successfulLoginClearsCountersAndLock() {
        ClientAccount a = account();
        for (int i = 0; i < 5; i++) {
            a.registerFailedLogin(T0.plusSeconds(i));
        }
        a.registerSuccessfulLogin(T0.plusSeconds(60));
        assertEquals(0, a.getFailedAttempts());
        assertNull(a.getLockedUntil());
        assertEquals(T0.plusSeconds(60), a.getLastLoginAt());
    }

    @Test
    void tokenIsValidOnlyBeforeItExpires() {
        ClientAccount a = account();
        assertFalse(a.hasValidTokenAt(T0));
        a.issueToken("hash", TokenPurpose.INVITE, T0.plusSeconds(100));
        assertTrue(a.hasValidTokenAt(T0.plusSeconds(99)));
        assertFalse(a.hasValidTokenAt(T0.plusSeconds(100)));
        a.clearToken();
        assertFalse(a.hasValidTokenAt(T0));
    }

    @Test
    void settingANewPasswordClearsTokenAndLock() {
        ClientAccount a = account();
        a.issueToken("hash", TokenPurpose.RESET, T0.plusSeconds(100));
        for (int i = 0; i < 5; i++) {
            a.registerFailedLogin(T0.plusSeconds(i));
        }
        assertFalse(a.hasPassword());
        a.setNewPassword("$2a$12$hash");
        assertTrue(a.hasPassword());
        assertFalse(a.hasValidTokenAt(T0));
        assertFalse(a.isLockedAt(T0.plusSeconds(10)));
        assertNull(a.getTokenPurpose());
    }

    @Test
    void principalCarriesIdentityAndRole() {
        var principal = account().toPrincipal();
        assertEquals("ana@exemplo.test", principal.email());
        assertFalse(principal.isAdmin());
    }
}
