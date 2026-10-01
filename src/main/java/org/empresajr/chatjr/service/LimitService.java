package org.empresajr.chatjr.service;

import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** Limites de uso configuráveis na tela de administração. Estourar um limite devolve 429. */
@Service
public class LimitService {

    public static final int DEFAULT_PROCESSINGS_PER_DAY = 20;

    private final AiCallLogRepository calls;
    private final SettingsService settings;
    private final Clock clock;

    public LimitService(AiCallLogRepository calls, SettingsService settings, Clock clock) {
        this.calls = calls;
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
}
