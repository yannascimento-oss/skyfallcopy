package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateTabRequest(
        @NotBlank(message = "Informe o título da etapa.") @Size(max = 200, message = "Título longo demais.") String title,
        @Size(max = 200000, message = "O conteúdo da etapa é grande demais.") String html,
        @Size(max = 500, message = "Descrição curta longa demais.") String shortDescription,
        @Size(max = 2000, message = "O texto \"o que é\" é longo demais.") String whatIsIt,
        @Size(max = 2000, message = "O objetivo é longo demais.") String objective,
        @Size(max = 8, message = "Use no máximo 8 pontos principais.") List<@Size(max = 300, message = "Ponto principal longo demais.") String> keyPoints,
        @Size(max = 6, message = "Use no máximo 6 perguntas sugeridas.") List<@Size(max = 300, message = "Pergunta sugerida longa demais.") String> suggestedQuestions,
        @Size(max = 500, message = "Fonte longa demais.") String source,
        Boolean published) {
}
