package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.AuditLog;

import java.time.Instant;
import java.util.List;

public record AuditPage(List<Item> items, long total, int page, int size) {

    public record Item(Long id, String actorEmail, Long clientId, String clientName, String action, Long tabId,
                       String tabName, String detail, Instant createdAt) {

        public static Item of(AuditLog a, String clientName) {
            return new Item(a.getId(), a.getActorEmail(), a.getClientId(), clientName, a.getAction(), a.getTabId(),
                    a.getTabName(), a.getDetail(), a.getCreatedAt());
        }
    }
}
