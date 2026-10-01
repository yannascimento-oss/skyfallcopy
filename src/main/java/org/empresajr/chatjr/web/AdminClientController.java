package org.empresajr.chatjr.web;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.AccountService;
import org.empresajr.chatjr.web.dto.ClientSummary;
import org.empresajr.chatjr.web.dto.CreateClientRequest;
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

/** Gestão de clientes pela consultoria. O acesso é restrito ao perfil ADMIN na SecurityConfig. */
@RestController
@RequestMapping("/api/admin/clients")
public class AdminClientController {

    private final AccountService accounts;

    public AdminClientController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<ClientSummary> list() {
        return accounts.listClients().stream().map(ClientSummary::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(@Valid @RequestBody CreateClientRequest body) {
        return InviteResponse.of(accounts.createClient(body.name(), body.email(), body.company(), body.segment()));
    }

    @PostMapping("/{id}/invite")
    public InviteResponse reissueInvite(@PathVariable Long id) {
        return InviteResponse.of(accounts.reissue(id));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable Long id) {
        accounts.setSuspended(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        accounts.setSuspended(id, false);
        return ResponseEntity.noContent().build();
    }
}
