package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AppSetting;
import org.empresajr.chatjr.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Configurações operacionais guardadas no banco. Segredos são cifrados e nunca saem em claro pela API. */
@Service
public class SettingsService {

    public static final String SETUP_DONE = "setup.done";
    public static final String ORG_NAME = "org.name";
    public static final String AI_KEY = "ai.key";
    public static final String AI_MODEL = "ai.model";
    public static final String AI_MAX_TOKENS = "ai.max_tokens";
    public static final String AI_TEMPERATURE = "ai.temperature";
    public static final String LIMIT_QUESTIONS_PER_HOUR = "limits.questions_per_hour";
    public static final String LIMIT_PROCESSINGS_PER_DAY = "limits.processings_per_day";
    public static final String LIMIT_UPLOAD_MB = "limits.max_upload_mb";

    private final AppSettingRepository repository;
    private final SecretCipher cipher;
    private final Clock clock;
    private final String envAiKey;
    private final String defaultModel;

    public SettingsService(AppSettingRepository repository, SecretCipher cipher, Clock clock,
                           @Value("${chatjr.ai.default-key:}") String envAiKey,
                           @Value("${chatjr.ai.default-model:claude-sonnet-4-6}") String defaultModel) {
        this.repository = repository;
        this.cipher = cipher;
        this.clock = clock;
        this.envAiKey = envAiKey;
        this.defaultModel = defaultModel;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank());
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return get(key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional
    public void put(String key, String value) {
        store(key, value, false);
    }

    @Transactional
    public void putSecret(String key, String plain) {
        store(key, cipher.encrypt(plain), true);
    }

    @Transactional(readOnly = true)
    public Optional<String> getSecret(String key) {
        return repository.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank()).map(cipher::decrypt);
    }

    @Transactional
    public void remove(String key) {
        repository.findById(key).ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isSetupDone() {
        return repository.existsById(SETUP_DONE);
    }

    /** Chave da IA em uso: a gravada pela tela de administração, ou a variável de ambiente inicial. */
    @Transactional(readOnly = true)
    public Optional<String> aiKey() {
        Optional<String> stored = getSecret(AI_KEY);
        if (stored.isPresent()) {
            return stored;
        }
        return envAiKey == null || envAiKey.isBlank() ? Optional.empty() : Optional.of(envAiKey.trim());
    }

    /** Versão mascarada para exibir na interface, por exemplo "sk-ant-…4f2a". */
    @Transactional(readOnly = true)
    public Optional<String> maskedAiKey() {
        return aiKey().map(SettingsService::mask);
    }

    @Transactional(readOnly = true)
    public String aiModel() {
        return get(AI_MODEL, defaultModel);
    }

    @Transactional(readOnly = true)
    public int aiMaxTokens() {
        return Math.max(256, Math.min(16000, getInt(AI_MAX_TOKENS, 3000)));
    }

    @Transactional(readOnly = true)
    public double aiTemperature() {
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(get(AI_TEMPERATURE, "0.2").trim())));
        } catch (NumberFormatException e) {
            return 0.2;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasStoredAiKey() {
        return repository.findById(AI_KEY).map(AppSetting::getValue).filter(v -> !v.isBlank()).isPresent();
    }

    /** De onde vem a chave em uso: "database" (tela de administração), "environment" (variável inicial) ou "none". */
    @Transactional(readOnly = true)
    public String aiKeySource() {
        if (hasStoredAiKey()) {
            return "database";
        }
        return envAiKey == null || envAiKey.isBlank() ? "none" : "environment";
    }

    public String defaultModel() {
        return defaultModel;
    }

    public static String mask(String key) {
        if (key == null || key.length() <= 11) {
            return "••••";
        }
        return key.substring(0, 7) + "…" + key.substring(key.length() - 4);
    }

    private void store(String key, String value, boolean secret) {
        Instant now = clock.instant();
        repository.findById(key).ifPresentOrElse(
                existing -> existing.update(value, secret, now),
                () -> repository.save(new AppSetting(key, value, secret, now)));
    }
}
