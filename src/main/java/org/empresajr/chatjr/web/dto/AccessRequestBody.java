package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formulário "Solicitar acesso". O campo website é uma isca: pessoas não o veem nem preenchem. */
public record AccessRequestBody(
        @NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "Nome longo demais.") String name,
        @NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 200, message = "E-mail longo demais.") String email,
        @NotBlank(message = "Informe o nome da empresa.") @Size(max = 160, message = "Nome da empresa longo demais.") String company,
        @Size(max = 40, message = "Telefone longo demais.") String phone,
        @Size(max = 1000, message = "Mensagem longa demais.") String message,
        @Size(max = 200) String website) {
}
