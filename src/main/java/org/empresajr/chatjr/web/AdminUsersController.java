package org.empresajr.chatjr.web;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.AccountService;
import org.empresajr.chatjr.web.dto.ClientSummary;
import org.empresajr.chatjr.web.dto.CreateAdminRequest;
import org.empresajr.chatjr.web.dto.InviteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Usuários e acessos da consultoria (restrito ao perfil ADMIN na SecurityConfig). */
@RestController
@RequestMapping("/api/admin/admins")
public class AdminUsersController {

    private final AccountService accounts;

    public AdminUsersController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<ClientSummary> list() {
        return accounts.listAdmins().stream().map(ClientSummary::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(@Valid @RequestBody CreateAdminRequest body) {
        return InviteResponse.of(accounts.createAdminInvite(body.name(), body.email()));
    }

    @PostMapping("/{id}/invite")
    public InviteResponse reissue(@PathVariable Long id) {
        return InviteResponse.of(accounts.reissueAdminInvite(id));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable Long id) {
        accounts.setAdminSuspended(id, true, PlanController.caller().id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        accounts.setAdminSuspended(id, false, PlanController.caller().id());
        return ResponseEntity.noContent().build();
    }
}
