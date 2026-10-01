package org.empresajr.chatjr;

import org.empresajr.chatjr.service.SecretCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    private static String secret() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    @Test
    void roundTripReturnsTheOriginalText() {
        SecretCipher cipher = SecretCipher.of(secret());
        String plain = "sk-ant-api03-" + UUID.randomUUID();
        assertEquals(plain, cipher.decrypt(cipher.encrypt(plain)));
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        SecretCipher cipher = SecretCipher.of(secret());
        assertNotEquals(cipher.encrypt("abc"), cipher.encrypt("abc"));
    }

    @Test
    void encryptedTextDoesNotContainThePlainText() {
        SecretCipher cipher = SecretCipher.of(secret());
        String plain = "valor-bem-reconhecivel-123";
        assertTrue(!cipher.encrypt(plain).contains(plain));
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String encrypted = SecretCipher.of(secret()).encrypt("segredo");
        SecretCipher other = SecretCipher.of(secret());
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void tamperedTextIsRejected() {
        SecretCipher cipher = SecretCipher.of(secret());
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("segredo"));
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void garbageAndTruncatedInputAreRejected() {
        SecretCipher cipher = SecretCipher.of(secret());
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("isto não é base64 válido!!"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[5])));
    }

    @Test
    void productionRefusesToStartWithoutSecret() {
        MockEnvironment production = new MockEnvironment();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new SecretCipher("", production));
        assertTrue(e.getMessage().contains("CHATJR_SECRET"));
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalStateException.class, () -> new SecretCipher("curta", new MockEnvironment()));
    }

    @Test
    void devProfileAcceptsMissingSecretWithATemporaryKey() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        SecretCipher cipher = new SecretCipher("", dev);
        assertEquals("ok", cipher.decrypt(cipher.encrypt("ok")));
    }
}
