package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.Conversation;

import java.time.Instant;

public record ConversationView(Long id, String title, Instant updatedAt) {

    public static ConversationView of(Conversation c) {
        return new ConversationView(c.getId(), c.getTitle(), c.getUpdatedAt());
    }
}
