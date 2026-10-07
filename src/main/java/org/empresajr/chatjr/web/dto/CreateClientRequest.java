package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank(message = "Informe o nome do responsável.") @Size(max = 120, message = "Nome longo demais.") String name,
        @NotBlank(message = "Informe o e-mail de acesso.") @Email(message = "Informe um e-mail de acesso válido.") @Size(max = 200, message = "E-mail longo demais.") String email,
        @NotBlank(message = "Informe a empresa.") @Size(max = 160, message = "Nome da empresa longo demais.") String company,
        @Size(max = 120, message = "Segmento longo demais.") String segment) {
}
