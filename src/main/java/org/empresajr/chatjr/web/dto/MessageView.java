package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.ChatMessage;

import java.time.Instant;

public record MessageView(Long id, String role, String html, String source, boolean inference, boolean degraded,
                          Instant createdAt) {

    public static MessageView of(ChatMessage m) {
        return new MessageView(m.getId(), m.getRole().name(), m.getHtml(), m.getSource(), m.isInference(),
                m.isDegraded(), m.getCreatedAt());
    }
}
