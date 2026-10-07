package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank(message = "O link de acesso está incompleto.") @Size(max = 200, message = "O link de acesso é inválido.") String token,
        @NotBlank(message = "Informe a nova senha.") @Size(max = 100, message = "A senha pode ter no máximo 100 caracteres.") String password) {
}
