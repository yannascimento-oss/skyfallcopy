package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Informe a senha atual.") @Size(max = 200, message = "Senha longa demais.") String currentPassword,
        @NotBlank(message = "Informe a nova senha.") @Size(max = 100, message = "A senha pode ter no máximo 100 caracteres.") String newPassword) {
}
