package org.empresajr.chatjr.service;

import org.empresajr.chatjr.ai.AiClient;
import org.empresajr.chatjr.ai.AiContentParser;
import org.empresajr.chatjr.ai.AiException;
import org.empresajr.chatjr.ai.AiRequest;
import org.empresajr.chatjr.ai.AiResult;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.AiCallLog;
import org.empresajr.chatjr.domain.Attachment;
import org.empresajr.chatjr.domain.AttachmentState;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.ExtractiveFormatter;
import org.empresajr.chatjr.domain.GeneratedContent;
import org.empresajr.chatjr.domain.HtmlSanitizer;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.PromptBuilder;
import org.empresajr.chatjr.domain.TabChunk;
import org.empresajr.chatjr.domain.TextChunker;
import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.repository.AttachmentRepository;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.repository.TabChunkRepository;
import org.empresajr.chatjr.web.ApiException;
import org.empresajr.chatjr.web.dto.AttachmentView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Transforma o PDF de uma aba em conteúdo do plano. Roda em segundo plano: a requisição só marca a aba como
 * "processando" e a tela acompanha o estado. Sem chave da IA, ou se a IA falhar, usa o formatador sem IA.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);
    private static final int CHUNK_TARGET = 900;
    private static final int CHUNK_OVERLAP = 120;
    private static final String TAIL = " O conteúdo foi extraído do PDF sem reescrita.";

    private final PlanService plans;
    private final AttachmentService attachmentService;
    private final AttachmentRepository attachments;
    private final TabChunkRepository chunks;
    private final ClientAccountRepository accounts;
    private final AiCallLogRepository callLogs;
    private final AiClient ai;
    private final SettingsService settings;
    private final LimitService limits;
    private final AuditService audit;
    private final AppErrorService appErrors;
    private final TransactionTemplate tx;
    private final ExecutorService executor;
    private final Clock clock;

    public ProcessingService(PlanService plans, AttachmentService attachmentService, AttachmentRepository attachments,
                             TabChunkRepository chunks, ClientAccountRepository accounts, AiCallLogRepository callLogs,
                             AiClient ai, SettingsService settings, LimitService limits, AuditService audit,
                             AppErrorService appErrors, TransactionTemplate tx,
                             @Qualifier("processingExecutor") ExecutorService executor, Clock clock) {
        this.plans = plans;
        this.attachmentService = attachmentService;
        this.attachments = attachments;
        this.chunks = chunks;
        this.accounts = accounts;
        this.callLogs = callLogs;
        this.ai = ai;
        this.settings = settings;
        this.limits = limits;
        this.audit = audit;
        this.appErrors = appErrors;
        this.tx = tx;
        this.executor = executor;
        this.clock = clock;
    }

    /** Marca a aba como "processando" e dispara o trabalho em segundo plano. */
    public AttachmentView start(Long clientId, Long tabId, AccountPrincipal actor) {
        PlanTab tab = plans.adminTab(clientId, tabId);
        limits.checkProcessing(clientId);
        tx.executeWithoutResult(status -> {
            Attachment attachment = attachments.findForUpdateByTabId(tabId)
                    .filter(Attachment::hasPdf)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Envie um PDF antes de processar."));
            if (attachment.getState() == AttachmentState.PROCESSING) {
                throw new ApiException(HttpStatus.CONFLICT, "Esta etapa já está sendo processada.");
            }
            attachment.markProcessing(clock.instant());
            attachments.save(attachment);
        });
        audit.record("PROCESS_STARTED", clientId, tabId, tab.getName(), null);
        executor.submit(() -> run(clientId, tabId, actor.id(), actor.email()));
        return attachmentService.view(clientId, tabId);
    }

    /** Ao subir o servidor, qualquer processamento que ficou pela metade é marcado como interrompido. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterrupted() {
        int n = attachments.failInterrupted(AttachmentState.FAILED, AttachmentState.PROCESSING,
                "O processamento foi interrompido (o servidor reiniciou). Processe de novo.", clock.instant());
        if (n > 0) {
            log.warn("{} processamento(s) interrompido(s) foram marcados como falhos.", n);
        }
    }

    // ---------- trabalho em segundo plano ----------

    private record Input(String stageName, String company, String pdfText, String pdfName) {
    }

    void run(Long clientId, Long tabId, Long actorId, String actorEmail) {
        try {
            Input input = tx.execute(status -> {
                PlanTab tab = plans.adminTab(clientId, tabId);
                ClientAccount client = accounts.findById(clientId).orElseThrow();
                Attachment attachment = attachments.findByTabId(tabId).orElseThrow();
                return new Input(tab.getName(), client.getCompany(), attachment.getPdfText(), attachment.getPdfName());
            });

            GeneratedContent content;
            String note = null;
            Optional<String> key = settings.aiKey();
            if (key.isEmpty()) {
                content = extractive(input);
                note = "Processado sem IA: nenhuma chave da IA está configurada." + TAIL;
            } else {
                AiOutcome outcome = callAi(key.get(), clientId, input);
                content = outcome.content();
                note = outcome.note();
                if (content == null) {
                    content = extractive(input);
                }
            }

            GeneratedContent finalContent = content;
            String finalNote = note;
            tx.executeWithoutResult(status -> {
                plans.applyGenerated(clientId, tabId, finalContent, input.pdfName(), actorEmail, actorId);
                chunks.deleteAllByTabId(tabId);
                int index = 0;
                for (String part : TextChunker.chunk(input.pdfText(), CHUNK_TARGET, CHUNK_OVERLAP)) {
                    chunks.save(new TabChunk(tabId, index++, part));
                }
                Attachment attachment = attachments.findByTabId(tabId).orElseThrow();
                attachment.markDone(finalContent.sections(), finalNote, clock.instant());
                attachments.save(attachment);
            });
        } catch (Exception e) {
            log.error("Falha ao processar a aba {} do cliente {}", tabId, clientId, e);
            appErrors.record("Falha ao processar PDF: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "/api/admin/clients/" + clientId + "/tabs/" + tabId + "/attachment/process");
            markFailed(tabId);
        }
    }

    private record AiOutcome(GeneratedContent content, String note) {
    }

    private AiOutcome callAi(String apiKey, Long clientId, Input input) {
        String model = settings.aiModel();
        AiRequest request = new AiRequest(apiKey, model, PromptBuilder.processingSystem(),
                PromptBuilder.processingUser(input.stageName(), input.company(), input.pdfText()),
                settings.aiMaxTokens(), settings.aiTemperature());
        long started = System.nanoTime();
        try {
            AiResult result = ai.complete(request);
            int ms = elapsedMs(started);
            Optional<GeneratedContent> parsed = AiContentParser.parse(result.text())
                    .filter(c -> !HtmlSanitizer.sanitize(c.html()).isBlank());
            if (parsed.isPresent()) {
                logCall(clientId, model, "OK", 200, ms, result, null);
                return new AiOutcome(parsed.get(), null);
            }
            logCall(clientId, model, "FALLBACK", 200, ms, result, "resposta fora do formato");
            String why = result.truncated()
                    ? "A resposta da IA foi cortada pelo limite de tokens; aumente o limite na tela de Integração de IA."
                    : "A resposta da IA veio fora do formato esperado.";
            return new AiOutcome(null, why + TAIL);
        } catch (AiException e) {
            logCall(clientId, model, "ERROR", e.getHttpStatus(), elapsedMs(started), null, e.getMessage());
            return new AiOutcome(null, "A IA não respondeu (" + e.getMessage() + ")." + TAIL);
        }
    }

    private GeneratedContent extractive(Input input) {
        ExtractiveFormatter.Result result = ExtractiveFormatter.format(input.pdfText(), 120_000);
        return new GeneratedContent(null, result.html(), null, null, null, List.of(), List.of(), result.sections());
    }

    private void logCall(Long clientId, String model, String status, int httpStatus, int ms, AiResult result, String error) {
        Integer in = result == null ? null : result.inputTokens();
        Integer out = result == null ? null : result.outputTokens();
        callLogs.save(new AiCallLog(clientId, "PROCESS", model, status, httpStatus, ms, in, out,
                result == null ? null : estimateCostMicroUsd(result.inputTokens(), result.outputTokens()),
                error, clock.instant()));
    }

    /** Estimativa em micro-dólares por token (US$ 3 por milhão de entrada, US$ 15 por milhão de saída). */
    static long estimateCostMicroUsd(int inputTokens, int outputTokens) {
        return inputTokens * 3L + outputTokens * 15L;
    }

    private static int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private void markFailed(Long tabId) {
        try {
            tx.executeWithoutResult(status -> attachments.findByTabId(tabId).ifPresent(a -> {
                a.markFailed("Não foi possível concluir o processamento. Tente de novo; se repetir, avise o suporte.",
                        clock.instant());
                attachments.save(a);
            }));
        } catch (RuntimeException e) {
            log.error("Não foi possível marcar a aba {} como falha", tabId, e);
        }
    }
}
