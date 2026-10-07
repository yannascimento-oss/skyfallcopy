package org.empresajr.chatjr.web.dto;

import jakarta.validation.constraints.Size;

public record LinkRequest(@Size(max = 1000, message = "O link é longo demais.") String slideLink) {
}
