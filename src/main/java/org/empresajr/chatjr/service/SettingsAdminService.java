package org.empresajr.chatjr.service;

import org.empresajr.chatjr.ai.AiClient;
import org.empresajr.chatjr.ai.AiException;
import org.empresajr.chatjr.ai.AiRequest;
import org.empresajr.chatjr.ai.AiResult;
import org.empresajr.chatjr.domain.AiCallLog;
import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.web.ApiException;
import org.empresajr.chatjr.web.dto.SettingsDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Telas de configuração da consultoria: integração de IA, limites e dados da organização. */
@Service
public class SettingsAdminService {

    private final SettingsService settings;
    private final AiClient ai;
    private final AiCallLogRepository callLogs;
    private final AuditService audit;
    private final Clock clock;

    public SettingsAdminService(SettingsService settings, AiClient ai, AiCallLogRepository callLogs,
                                AuditService audit, Clock clock) {
        this.settings = settings;
        this.ai = ai;
        this.callLogs = callLogs;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsDto.All view() {
        return new SettingsDto.All(organization(), aiView(), limits());
    }

    @Transactional
    public SettingsDto.Ai updateAi(SettingsDto.UpdateAi request) {
        List<String> changed = new ArrayList<>();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String key = request.apiKey().trim();
            if (!key.startsWith("sk-ant-") || key.length() < 20 || key.matches(".*\\s.*")) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Essa não parece ser uma chave da Anthropic (ela começa com sk-ant-).");
            }
            settings.putSecret(SettingsService.AI_KEY, key);
            changed.add("chave");
        }
        if (request.model() != null && !request.model().isBlank()) {
            settings.put(SettingsService.AI_MODEL, request.model().trim());
            changed.add("modelo");
        }
        if (request.maxTokens() != null) {
            settings.put(SettingsService.AI_MAX_TOKENS, String.valueOf(request.maxTokens()));
            changed.add("tokens");
        }
        if (request.temperature() != null) {
            settings.put(SettingsService.AI_TEMPERATURE, String.valueOf(request.temperature()));
            changed.add("temperatura");
        }
        // O registro diz o que mudou, nunca o valor.
        audit.record("AI_SETTINGS_CHANGED", null, null, null, changed.isEmpty() ? "nada alterado" : String.join(", ", changed));
        return aiView();
    }

    @Transactional
    public SettingsDto.Ai removeAiKey() {
        settings.remove(SettingsService.AI_KEY);
        audit.record("AI_KEY_REMOVED", null, null, null, null);
        return aiView();
    }

    @Transactional
    public SettingsDto.Limits updateLimits(SettingsDto.UpdateLimits request) {
        settings.put(SettingsService.LIMIT_QUESTIONS_PER_HOUR, String.valueOf(request.questionsPerHour()));
        settings.put(SettingsService.LIMIT_PROCESSINGS_PER_DAY, String.valueOf(request.processingsPerDay()));
        settings.put(SettingsService.LIMIT_UPLOAD_MB, String.valueOf(request.maxUploadMb()));
        audit.record("LIMITS_CHANGED", null, null, null, request.questionsPerHour() + " perguntas/h, "
                + request.processingsPerDay() + " processamentos/dia, " + request.maxUploadMb() + " MB");
        return limits();
    }

    @Transactional
    public SettingsDto.Organization updateOrganization(SettingsDto.UpdateOrganization request) {
        settings.put(SettingsService.ORG_NAME, request.name().trim());
        audit.record("ORGANIZATION_CHANGED", null, null, null, null);
        return organization();
    }

    /** Faz uma chamada real e mínima à IA com a configuração atual. Sempre responde 200; "ok" diz se funcionou. */
    public SettingsDto.AiTest testAi() {
        String key = settings.aiKey().orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "Nenhuma chave da IA está configurada."));
        String model = settings.aiModel();
        long started = System.nanoTime();
        try {
            AiResult result = ai.complete(new AiRequest(key, model, "Responda apenas com a palavra OK.",
                    "Teste de conexão.", 16, 0));
            int ms = elapsed(started);
            log(model, "OK", 200, ms, result, null);
            audit.record("AI_TESTED", null, null, null, "conexão ok");
            return new SettingsDto.AiTest(true, "Conexão com a IA funcionando.", ms, model);
        } catch (AiException e) {
            int ms = elapsed(started);
            log(model, "ERROR", e.getHttpStatus(), ms, null, e.getMessage());
            audit.record("AI_TESTED", null, null, null, "falhou: " + e.getMessage());
            return new SettingsDto.AiTest(false, "Não foi possível usar a IA: " + e.getMessage() + ".", ms, model);
        }
    }

    private void log(String model, String status, int http, int ms, AiResult result, String error) {
        Integer in = result == null ? null : result.inputTokens();
        Integer out = result == null ? null : result.outputTokens();
        callLogs.save(new AiCallLog(null, "TEST", model, status, http, ms, in, out,
                result == null ? null : in * 3L + out * 15L, error, clock.instant()));
    }

    private static int elapsed(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private SettingsDto.Organization organization() {
        return new SettingsDto.Organization(settings.get(SettingsService.ORG_NAME).orElse(null));
    }

    private SettingsDto.Ai aiView() {
        return new SettingsDto.Ai(settings.maskedAiKey().orElse(null), settings.aiKeySource(), settings.aiModel(),
                settings.defaultModel(), settings.aiMaxTokens(), settings.aiTemperature());
    }

    private SettingsDto.Limits limits() {
        return new SettingsDto.Limits(
                settings.getInt(SettingsService.LIMIT_QUESTIONS_PER_HOUR, LimitService.DEFAULT_QUESTIONS_PER_HOUR),
                settings.getInt(SettingsService.LIMIT_PROCESSINGS_PER_DAY, LimitService.DEFAULT_PROCESSINGS_PER_DAY),
                settings.getInt(SettingsService.LIMIT_UPLOAD_MB, AttachmentService.DEFAULT_MAX_UPLOAD_MB));
    }
}
