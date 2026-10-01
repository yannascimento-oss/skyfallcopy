package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.PlanVersion;

import java.time.Instant;

public record VersionView(int version, String reason, String createdBy, Instant createdAt, String title) {

    public static VersionView of(PlanVersion v) {
        return new VersionView(v.getVersion(), v.getReason(), v.getCreatedBy(), v.getCreatedAt(), v.getTitle());
    }
}
