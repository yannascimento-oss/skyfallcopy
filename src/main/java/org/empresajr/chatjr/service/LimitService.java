package org.empresajr.chatjr.service;

import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.repository.QueryLogRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** Limites de uso configuráveis na tela de administração. Estourar um limite devolve 429. */
@Service
public class LimitService {

    public static final int DEFAULT_PROCESSINGS_PER_DAY = 20;
    public static final int DEFAULT_QUESTIONS_PER_HOUR = 60;

    private final AiCallLogRepository calls;
    private final QueryLogRepository queries;
    private final SettingsService settings;
    private final Clock clock;

    public LimitService(AiCallLogRepository calls, QueryLogRepository queries, SettingsService settings, Clock clock) {
        this.calls = calls;
        this.queries = queries;
        this.settings = settings;
        this.clock = clock;
    }

    public void checkProcessing(Long clientId) {
        int limit = settings.getInt(SettingsService.LIMIT_PROCESSINGS_PER_DAY, DEFAULT_PROCESSINGS_PER_DAY);
        long used = calls.countByClientIdAndKindAndCreatedAtAfter(clientId, "PROCESS",
                clock.instant().minus(Duration.ofHours(24)));
        if (used >= limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de " + limit + " processamentos por dia atingido para este cliente. Tente novamente amanhã.");
        }
    }

    public void checkQuestion(Long clientId) {
        int limit = settings.getInt(SettingsService.LIMIT_QUESTIONS_PER_HOUR, DEFAULT_QUESTIONS_PER_HOUR);
        long used = queries.countByClientIdAndCreatedAtAfter(clientId, clock.instant().minus(Duration.ofHours(1)));
        if (used >= limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Você atingiu o limite de " + limit + " perguntas por hora. Tente de novo em alguns minutos.");
        }
    }
}
