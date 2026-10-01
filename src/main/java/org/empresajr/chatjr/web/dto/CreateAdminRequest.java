package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank(message = "Informe o nome do administrador.") @Size(max = 120, message = "Nome longo demais.") String name,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 200, message = "E-mail longo demais.") String email) {
}
