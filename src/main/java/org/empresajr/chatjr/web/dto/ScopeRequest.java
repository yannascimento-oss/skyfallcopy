package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.NotNull;

public record ScopeRequest(@NotNull(message = "Informe se a etapa fica liberada.") Boolean allowed) {
}
