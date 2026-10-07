package org.empresajr.chatjr.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.service.AccountService;
import org.empresajr.chatjr.service.AuthService;
import org.empresajr.chatjr.service.CurrentUser;
import org.empresajr.chatjr.service.SettingsService;
import org.empresajr.chatjr.web.dto.AcceptInviteRequest;
import org.empresajr.chatjr.web.dto.ChangePasswordRequest;
import org.empresajr.chatjr.web.dto.LoginRequest;
import org.empresajr.chatjr.web.dto.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final AccountService accounts;
    private final SettingsService settings;

    public AuthController(AuthService auth, AccountService accounts, SettingsService settings) {
        this.auth = auth;
        this.accounts = accounts;
        this.settings = settings;
    }

    /** Só existe para o navegador receber o cookie XSRF-TOKEN antes do primeiro POST. */
    @GetMapping("/auth/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                            HttpServletResponse response) {
        AccountPrincipal principal = auth.login(body.email(), body.password(), request, response);
        return meOf(accounts.getAccount(principal.id()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest body) {
        accounts.acceptToken(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        AccountPrincipal principal = CurrentUser.get()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
        return meOf(accounts.getAccount(principal.id()));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        AccountPrincipal principal = CurrentUser.get()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
        auth.changePassword(principal.id(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private MeResponse meOf(ClientAccount account) {
        return new MeResponse(account.getId(), account.getName(), account.getEmail(), account.getRole().name(),
                account.getCompany(), settings.get(SettingsService.ORG_NAME).orElse(null));
    }
}
