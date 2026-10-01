package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.ClientAccount;

import java.time.Instant;

public record ClientSummary(Long id, String name, String email, String company, String segment,
                            boolean suspended, boolean passwordSet, Instant lastLoginAt, int planVersion) {

    public static ClientSummary of(ClientAccount a) {
        return new ClientSummary(a.getId(), a.getName(), a.getEmail(), a.getCompany(), a.getSegment(),
                a.isSuspended(), a.hasPassword(), a.getLastLoginAt(), a.getPlanVersion());
    }
}
