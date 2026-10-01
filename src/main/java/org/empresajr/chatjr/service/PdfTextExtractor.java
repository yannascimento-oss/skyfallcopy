package org.empresajr.chatjr.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Valida o PDF e extrai o texto. Toda recusa vira uma mensagem clara em português. */
public final class PdfTextExtractor {

    public static final int MAX_PAGES = 200;
    public static final int MAX_TEXT_CHARS = 500_000;

    public record Extracted(String text, int pages) {
    }

    private PdfTextExtractor() {
    }

    public static boolean looksLikePdf(byte[] bytes) {
        byte[] magic = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    public static Extracted extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw bad("O PDF não tem páginas.");
            }
            if (pages > MAX_PAGES) {
                throw bad("O PDF tem " + pages + " páginas; o máximo aceito é " + MAX_PAGES + ".");
            }
            String text = new PDFTextStripper().getText(document)
                    .replace("\u0000", "")
                    .replaceAll("[ \\t]+\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .strip();
            if (text.isEmpty()) {
                throw bad("Não encontramos texto neste PDF. Se ele for um documento escaneado (imagem), "
                        + "envie uma versão com texto selecionável.");
            }
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS);
            }
            return new Extracted(text, pages);
        } catch (InvalidPasswordException e) {
            throw bad("O PDF está protegido por senha. Envie uma versão sem senha.");
        } catch (IOException e) {
            throw bad("Não foi possível ler o PDF. O arquivo pode estar corrompido.");
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
