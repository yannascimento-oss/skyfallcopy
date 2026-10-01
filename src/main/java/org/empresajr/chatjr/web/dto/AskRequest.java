package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        Long conversationId,
        @NotBlank(message = "Digite sua pergunta.") @Size(max = 500, message = "A pergunta pode ter no máximo 500 caracteres.") String question) {
}
