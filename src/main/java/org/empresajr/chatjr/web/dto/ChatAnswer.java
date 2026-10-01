package org.empresajr.chatjr.web.dto;

import java.time.Instant;

/**
 * Resposta do chat. {@code answered} é falso quando a informação não consta no plano; {@code inference} é verdadeiro
 * quando a resposta é uma dedução e não o texto literal; {@code degraded} indica que a IA não pôde ser usada.
 */
public record ChatAnswer(Long conversationId, Long messageId, String html, String source, Long sourceTabId,
                         boolean inference, boolean degraded, boolean answered, Instant createdAt) {
}
