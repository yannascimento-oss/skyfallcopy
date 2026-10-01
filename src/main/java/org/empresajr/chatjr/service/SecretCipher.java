package org.empresajr.chatjr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra segredos gravados no banco (como a chave da IA) com AES-256-GCM.
 * A chave vem de CHATJR_SECRET; cada texto cifrado usa um IV aleatório próprio.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_SECRET_CHARS = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecretCipher(@Value("${chatjr.secret:}") String secret, Environment env) {
        this.key = resolveKey(secret, env);
    }

    private SecretCipher(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Fábrica para testes e usos fora do Spring. */
    public static SecretCipher of(String secret) {
        return new SecretCipher(sha256(secret));
    }

    private static SecretKeySpec resolveKey(String secret, Environment env) {
        if (secret == null || secret.isBlank()) {
            if (env.acceptsProfiles(Profiles.of("dev"))) {
                byte[] ephemeral = new byte[32];
                new SecureRandom().nextBytes(ephemeral);
                log.warn("CHATJR_SECRET não definida: usando uma chave temporária (perfil dev). "
                        + "Segredos gravados agora não poderão ser lidos após reiniciar.");
                return new SecretKeySpec(ephemeral, "AES");
            }
            throw new IllegalStateException("Defina a variável de ambiente CHATJR_SECRET com um texto longo e "
                    + "aleatório (mínimo " + MIN_SECRET_CHARS + " caracteres). Ela protege a chave da IA no banco.");
        }
        if (secret.length() < MIN_SECRET_CHARS) {
            throw new IllegalStateException("CHATJR_SECRET é curta demais: use pelo menos "
                    + MIN_SECRET_CHARS + " caracteres.");
        }
        return new SecretKeySpec(sha256(secret), "AES");
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Devolve base64(IV + texto cifrado + etiqueta de autenticação). */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o segredo.", e);
        }
    }

    /** Falha se o texto foi alterado ou se a chave é outra. */
    public String decrypt(String payload) {
        try {
            byte[] in = Base64.getDecoder().decode(payload);
            if (in.length <= IV_BYTES) {
                throw new IllegalStateException("Segredo cifrado inválido.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível decifrar o segredo (chave diferente ou dado alterado).", e);
        }
    }
}
