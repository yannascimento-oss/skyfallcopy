package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetupRequest(
        @NotBlank(message = "Informe o nome da consultoria.") @Size(max = 160, message = "Nome da consultoria longo demais.") String orgName,
        @NotBlank(message = "Informe o nome do administrador.") @Size(max = 120, message = "Nome do administrador longo demais.") String adminName,
        @NotBlank(message = "Informe o e-mail do administrador.") @Email(message = "Informe um e-mail válido.") @Size(max = 200, message = "E-mail longo demais.") String adminEmail,
        @NotBlank(message = "Informe a senha do administrador.") @Size(max = 100, message = "A senha pode ter no máximo 100 caracteres.") String password,
        @Size(max = 300, message = "Chave da IA longa demais.") String aiKey) {
}
