package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank(message = "Informe o nome do responsável.") @Size(max = 120, message = "Nome longo demais.") String name,
        @NotBlank(message = "Informe a empresa.") @Size(max = 160, message = "Nome da empresa longo demais.") String company,
        @Size(max = 120, message = "Segmento longo demais.") String segment) {
}
