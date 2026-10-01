package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Formatos das telas de configuração. A chave da IA nunca sai daqui em texto puro: só a versão mascarada. */
public final class SettingsDto {

    private SettingsDto() {
    }

    public record Organization(String name) {
    }

    public record Ai(String keyMasked, String keySource, String model, String defaultModel, int maxTokens,
                     double temperature) {
    }

    public record Limits(int questionsPerHour, int processingsPerDay, int maxUploadMb) {
    }

    public record All(Organization organization, Ai ai, Limits limits) {
    }

    public record UpdateAi(
            @Size(max = 300, message = "Chave da IA longa demais.") String apiKey,
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,80}$", message = "Nome de modelo inválido.") String model,
            @Min(value = 256, message = "O mínimo de tokens por resposta é 256.")
            @Max(value = 16000, message = "O máximo de tokens por resposta é 16000.") Integer maxTokens,
            @DecimalMin(value = "0.0", message = "A temperatura vai de 0 a 1.")
            @DecimalMax(value = "1.0", message = "A temperatura vai de 0 a 1.") Double temperature) {
    }

    public record UpdateLimits(
            @NotNull(message = "Informe o limite de perguntas por hora.")
            @Min(value = 1, message = "O limite de perguntas por hora deve ser pelo menos 1.")
            @Max(value = 1000, message = "O limite de perguntas por hora é no máximo 1000.") Integer questionsPerHour,
            @NotNull(message = "Informe o limite de processamentos por dia.")
            @Min(value = 1, message = "O limite de processamentos por dia deve ser pelo menos 1.")
            @Max(value = 200, message = "O limite de processamentos por dia é no máximo 200.") Integer processingsPerDay,
            @NotNull(message = "Informe o tamanho máximo de upload.")
            @Min(value = 1, message = "O tamanho máximo de upload deve ser pelo menos 1 MB.")
            @Max(value = 25, message = "O tamanho máximo de upload é 25 MB.") Integer maxUploadMb) {
    }

    public record UpdateOrganization(
            @NotBlank(message = "Informe o nome da consultoria.")
            @Size(max = 160, message = "Nome da consultoria longo demais.") String name) {
    }

    public record AiTest(boolean ok, String message, int durationMs, String model) {
    }
}
