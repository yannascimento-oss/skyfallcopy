package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.Attachment;
import org.empresajr.chatjr.domain.AttachmentState;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.TabChunk;
import org.empresajr.chatjr.domain.TextChunker;
import org.empresajr.chatjr.repository.AttachmentRepository;
import org.empresajr.chatjr.repository.TabChunkRepository;
import org.empresajr.chatjr.web.ApiException;
import org.empresajr.chatjr.web.dto.AttachmentView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/** PDF e link de slides de cada aba: validação, extração do texto e guarda do arquivo original. */
@Service
public class AttachmentService {

    public static final int DEFAULT_MAX_UPLOAD_MB = 25;

    private final PlanService plans;
    private final AttachmentRepository attachments;
    private final TabChunkRepository chunks;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;
    private final Path dataDir;

    public AttachmentService(PlanService plans, AttachmentRepository attachments, TabChunkRepository chunks,
                             SettingsService settings, AuditService audit, Clock clock,
                             @Value("${chatjr.data-dir:./data}") String dataDir) {
        this.plans = plans;
        this.attachments = attachments;
        this.chunks = chunks;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
        this.dataDir = Path.of(dataDir);
    }

    @Transactional(readOnly = true)
    public AttachmentView view(Long clientId, Long tabId) {
        plans.adminTab(clientId, tabId);
        return attachments.findByTabId(tabId).map(AttachmentView::of).orElse(AttachmentView.empty(tabId));
    }

    @Transactional
    public AttachmentView upload(Long clientId, Long tabId, MultipartFile file) {
        PlanTab tab = plans.adminTab(clientId, tabId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecione um arquivo PDF.");
        }
        int limitMb = Math.max(1, settings.getInt(SettingsService.LIMIT_UPLOAD_MB, DEFAULT_MAX_UPLOAD_MB));
        if (file.getSize() > limitMb * 1024L * 1024L) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "O arquivo passa do limite de " + limitMb + " MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado.");
        }
        if (!PdfTextExtractor.looksLikePdf(bytes)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O arquivo não parece ser um PDF.");
        }
        PdfTextExtractor.Extracted extracted = PdfTextExtractor.extract(bytes);

        Attachment attachment = attachments.findForUpdateByTabId(tabId).orElseGet(() -> new Attachment(tabId, clock.instant()));
        if (attachment.getState() == AttachmentState.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "Esta etapa está sendo processada. Aguarde terminar para trocar o PDF.");
        }
        String relative = store(clientId, tabId, bytes);
        attachment.replacePdf(displayName(file.getOriginalFilename()), extracted.text(), extracted.pages(),
                relative, bytes.length, clock.instant());
        attachments.save(attachment);
        rebuildChunks(tabId, extracted.text());
        audit.record("PDF_UPLOADED", clientId, tabId, tab.getName(), attachment.getPdfName());
        return AttachmentView.of(attachment);
    }

    @Transactional
    public AttachmentView setLink(Long clientId, Long tabId, String rawLink) {
        PlanTab tab = plans.adminTab(clientId, tabId);
        String link = rawLink == null ? "" : rawLink.trim();
        if (!link.isEmpty()) {
            validateHttpsLink(link);
        }
        Attachment attachment = attachments.findForUpdateByTabId(tabId).orElseGet(() -> new Attachment(tabId, clock.instant()));
        attachment.setSlideLink(link.isEmpty() ? null : link, clock.instant());
        attachments.save(attachment);
        audit.record("SLIDE_LINK_SET", clientId, tabId, tab.getName(), null);
        return AttachmentView.of(attachment);
    }

    /** Remove o PDF, o texto extraído e os trechos de busca. O conteúdo já publicado da aba permanece. */
    @Transactional
    public void remove(Long clientId, Long tabId) {
        PlanTab tab = plans.adminTab(clientId, tabId);
        Attachment attachment = attachments.findForUpdateByTabId(tabId).orElse(null);
        if (attachment == null) {
            return;
        }
        if (attachment.getState() == AttachmentState.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "Esta etapa está sendo processada. Aguarde terminar para remover o PDF.");
        }
        deleteStored(attachment.getStoredPath());
        chunks.deleteAllByTabId(tabId);
        attachments.delete(attachment);
        audit.record("ATTACHMENT_REMOVED", clientId, tabId, tab.getName(), null);
    }

    /** Refaz os trechos de busca do PDF da aba. O chat consulta estes trechos: o PDF é o entregável final. */
    @Transactional
    public void rebuildChunks(Long tabId, String pdfText) {
        chunks.deleteAllByTabId(tabId);
        int index = 0;
        for (String part : TextChunker.chunk(pdfText)) {
            chunks.save(new TabChunk(tabId, index++, part));
        }
    }

    /** Apaga a pasta de arquivos de um cliente excluído, só depois de a exclusão no banco ser confirmada. */
    public void deleteClientFilesAfterCommit(Long clientId) {
        Path dir = dataDir.resolve("attachments").resolve(String.valueOf(clientId)).normalize();
        Runnable delete = () -> {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // Sem a pasta, não há o que apagar.
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private String store(Long clientId, Long tabId, byte[] bytes) {
        String relative = "attachments/" + clientId + "/" + tabId + ".pdf";
        Path target = dataDir.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível guardar o arquivo no servidor.");
        }
        return relative;
    }

    private void deleteStored(String relative) {
        if (relative == null) {
            return;
        }
        try {
            Files.deleteIfExists(dataDir.resolve(relative).normalize());
        } catch (IOException ignored) {
            // O registro no banco é a fonte da verdade; um arquivo órfão não impede a remoção.
        }
    }

    private static void validateHttpsLink(String link) {
        try {
            URI uri = URI.create(link);
            boolean ok = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !link.matches(".*\\s.*");
            if (!ok) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe um link válido que comece com https://");
        }
    }

    /** Nome só para exibição: nunca é usado como caminho de arquivo. */
    static String displayName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "plano.pdf";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
