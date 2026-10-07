package org.empresajr.chatjr.service;

import org.empresajr.chatjr.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Logo da consultoria, gravado no diretório de dados (não cabe em app_setting). PNG, JPEG ou WebP, até 1 MB. */
@Service
public class BrandingService {

    public static final int MAX_BYTES = 1024 * 1024;

    public record Logo(byte[] bytes, String contentType) {
    }

    private final Path file;
    private final AuditService audit;

    public BrandingService(@Value("${chatjr.data-dir:./data}") String dataDir, AuditService audit) {
        this.file = Path.of(dataDir).resolve("branding").resolve("logo");
        this.audit = audit;
    }

    public boolean hasLogo() {
        return Files.isRegularFile(file);
    }

    public Optional<Logo> logo() {
        if (!hasLogo()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(new Logo(bytes, contentType(bytes)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void save(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escolha um arquivo de imagem.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "O logo pode ter no máximo 1 MB.");
        }
        if (contentType(bytes) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Envie o logo em PNG, JPEG ou WebP.");
        }
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "logo", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o logo.", e);
        }
        audit.record("LOGO_CHANGED", null, null, null, bytes.length + " bytes");
    }

    public void remove() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível remover o logo.", e);
        }
        audit.record("LOGO_CHANGED", null, null, null, "removido");
    }

    /** Tipo pelo conteúdo (assinatura do arquivo), nunca pelo nome ou pelo cabeçalho enviado. */
    static String contentType(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
