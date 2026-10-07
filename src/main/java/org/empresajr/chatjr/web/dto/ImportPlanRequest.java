package org.empresajr.chatjr.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Plano importado. Aceita o mesmo JSON que a exportação gera (campos a mais são ignorados). */
public record ImportPlanRequest(
        @NotEmpty(message = "O arquivo não tem nenhuma etapa.") @Size(max = 60, message = "O arquivo tem etapas demais (máximo 60).")
        List<@Valid Tab> tabs) {

    public record Tab(
            @NotBlank(message = "Toda etapa do arquivo precisa de nome.") @Size(max = 120, message = "Nome de etapa longo demais.") String name,
            @Size(max = 200, message = "Título longo demais.") String title,
            @Size(max = 200000, message = "O conteúdo de uma etapa é grande demais.") String html,
            @Size(max = 500) String shortDescription,
            @Size(max = 2000) String whatIsIt,
            @Size(max = 2000) String objective,
            @Size(max = 8, message = "Use no máximo 8 pontos principais por etapa.") List<@Size(max = 300) String> keyPoints,
            @Size(max = 6, message = "Use no máximo 6 perguntas sugeridas por etapa.") List<@Size(max = 300) String> suggestedQuestions,
            @Size(max = 500) String source) {

        public UpdateTabRequest toUpdate() {
            return new UpdateTabRequest(title, html == null ? "" : html, shortDescription, whatIsIt, objective,
                    keyPoints, suggestedQuestions, source, null);
        }
    }
}
