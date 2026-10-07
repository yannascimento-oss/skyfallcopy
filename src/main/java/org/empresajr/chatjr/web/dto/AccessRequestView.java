package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.AccessRequest;

import java.time.Instant;

public record AccessRequestView(Long id, String name, String email, String company, String phone, String message, Instant createdAt) {

    public static AccessRequestView of(AccessRequest r) {
        return new AccessRequestView(r.getId(), r.getName(), r.getEmail(), r.getCompany(), r.getPhone(), r.getMessage(), r.getCreatedAt());
    }
}
