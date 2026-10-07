package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTabRequest(
        @NotBlank(message = "Informe o nome da etapa.") @Size(max = 160, message = "Nome longo demais.") String name) {
}
