package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o e-mail de acesso.") @Size(max = 200, message = "E-mail longo demais.") String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "Senha longa demais.") String password) {
}
