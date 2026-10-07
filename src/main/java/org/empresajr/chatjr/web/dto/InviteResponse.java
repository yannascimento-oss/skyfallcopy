package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.service.AccountService;

import java.time.Instant;

/** O token aparece só nesta resposta; depois dela, só o hash existe no servidor. */
public record InviteResponse(ClientSummary client, String inviteToken, String invitePath, Instant expiresAt) {

    public static InviteResponse of(AccountService.Invite invite) {
        return new InviteResponse(ClientSummary.of(invite.account()), invite.token(),
                "/?convite=" + invite.token(), invite.expiresAt());
    }
}
