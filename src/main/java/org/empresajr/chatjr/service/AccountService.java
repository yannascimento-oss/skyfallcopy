package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.PasswordPolicy;
import org.empresajr.chatjr.domain.Role;
import org.empresajr.chatjr.domain.TokenPurpose;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Contas de cliente e administrador: criação, convite, senha e suspensão. */
@Service
public class AccountService {

    /** Validade do convite e do link de redefinição. */
    public static final Duration TOKEN_VALIDITY = Duration.ofHours(48);

    /** Convite recém-emitido. O texto do token só existe aqui: no banco fica apenas o hash. */
    public record Invite(ClientAccount account, String token, Instant expiresAt) {
    }

    private final ClientAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PlanService plans;
    private final AttachmentService attachmentService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountService(ClientAccountRepository accounts, PasswordEncoder encoder,
                          AuditService audit, PlanService plans, AttachmentService attachmentService, Clock clock) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.plans = plans;
        this.attachmentService = attachmentService;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<ClientAccount> listClients() {
        return accounts.findByRoleOrderByCompanyAsc(Role.CLIENT);
    }

    @Transactional(readOnly = true)
    public ClientAccount requireClient(Long id) {
        ClientAccount account = accounts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
        if (account.getRole() != Role.CLIENT) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        }
        return account;
    }

    @Transactional
    public Invite createClient(String name, String email, String company, String segment) {
        String normalized = normalizeEmail(email);
        if (accounts.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um acesso com esse e-mail.");
        }
        ClientAccount account = new ClientAccount(Role.CLIENT, name.trim(), normalized, clock.instant());
        account.setCompany(company.trim());
        account.setSegment(segment == null || segment.isBlank() ? null : segment.trim());
        accounts.save(account);
        plans.createDefaultTabs(account);
        Invite invite = newInvite(account, TokenPurpose.INVITE);
        audit.record("CLIENT_CREATED", account.getId(), null, null, "Acesso criado para " + normalized);
        return invite;
    }

    /** Novo link de primeiro acesso ou de redefinição; o anterior deixa de valer. */
    @Transactional
    public Invite reissue(Long clientId) {
        ClientAccount account = requireClient(clientId);
        TokenPurpose purpose = account.hasPassword() ? TokenPurpose.RESET : TokenPurpose.INVITE;
        Invite invite = newInvite(account, purpose);
        audit.record(purpose == TokenPurpose.RESET ? "PASSWORD_RESET_ISSUED" : "INVITE_ISSUED",
                account.getId(), null, null, "Link emitido para " + account.getEmail());
        return invite;
    }

    /** Define a senha a partir do token de convite ou de redefinição. O token vale uma única vez. */
    @Transactional
    public void acceptToken(String rawToken, String newPassword) {
        ClientAccount account = accounts.findByTokenHash(hash(rawToken == null ? "" : rawToken.trim()))
                .filter(a -> a.hasValidTokenAt(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Este link é inválido ou expirou. Peça um novo à consultoria da Empresa JR."));
        PasswordPolicy.check(newPassword, account.getEmail()).ifPresent(msg -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, msg);
        });
        account.setNewPassword(encoder.encode(newPassword));
        accounts.save(account);
        audit.recordAs(account.getId(), account.getEmail(), "PASSWORD_SET", account.getId(), null, null, null);
    }

    @Transactional
    public void setSuspended(Long clientId, boolean suspended) {
        ClientAccount account = requireClient(clientId);
        account.setSuspended(suspended);
        accounts.save(account);
        audit.record(suspended ? "ACCESS_SUSPENDED" : "ACCESS_RESTORED", account.getId(), null, null,
                account.getEmail());
    }

    @Transactional
    public ClientAccount createAdmin(String name, String email, String password) {
        String normalized = normalizeEmail(email);
        if (accounts.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um acesso com esse e-mail.");
        }
        PasswordPolicy.check(password, normalized).ifPresent(msg -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, msg);
        });
        ClientAccount admin = new ClientAccount(Role.ADMIN, name.trim(), normalized, clock.instant());
        admin.setNewPassword(encoder.encode(password));
        return accounts.save(admin);
    }

    // ---------- dados do cliente ----------

    @Transactional
    public ClientAccount updateClient(Long clientId, String name, String company, String segment) {
        ClientAccount account = requireClient(clientId);
        account.setName(name.trim());
        account.setCompany(company.trim());
        account.setSegment(segment == null || segment.isBlank() ? null : segment.trim());
        accounts.save(account);
        audit.record("CLIENT_UPDATED", account.getId(), null, null, account.getEmail());
        return account;
    }

    /** Exclui o cliente e tudo dele (plano, conversas, anexos). Exige digitar o e-mail do cliente. */
    @Transactional
    public void deleteClient(Long clientId, String confirmEmail) {
        ClientAccount account = requireClient(clientId);
        if (!normalizeEmail(confirmEmail).equals(account.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Para excluir, confirme digitando o e-mail do cliente: " + account.getEmail());
        }
        audit.record("CLIENT_DELETED", account.getId(), null, null, account.getEmail() + " (" + account.getCompany() + ")");
        accounts.delete(account);
        attachmentService.deleteClientFilesAfterCommit(clientId);
    }

    /** Promove um cliente a administrador. Quem já é administrador não passa por aqui (404). */
    @Transactional
    public ClientAccount promoteToAdmin(Long clientId) {
        ClientAccount account = requireClient(clientId);
        account.promoteToAdmin();
        accounts.save(account);
        audit.record("ADMIN_PROMOTED", clientId, null, null, account.getEmail());
        return account;
    }

    // ---------- administradores ----------

    @Transactional(readOnly = true)
    public List<ClientAccount> listAdmins() {
        return accounts.findByRoleOrderByNameAsc(Role.ADMIN);
    }

    /** Cria um administrador sem senha; ele define a senha pelo link de convite. */
    @Transactional
    public Invite createAdminInvite(String name, String email) {
        String normalized = normalizeEmail(email);
        if (accounts.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um acesso com esse e-mail.");
        }
        ClientAccount admin = accounts.save(new ClientAccount(Role.ADMIN, name.trim(), normalized, clock.instant()));
        Invite invite = newInvite(admin, TokenPurpose.INVITE);
        audit.record("ADMIN_CREATED", null, null, null, "Administrador " + normalized);
        return invite;
    }

    @Transactional
    public Invite reissueAdminInvite(Long adminId) {
        ClientAccount admin = requireAdmin(adminId);
        TokenPurpose purpose = admin.hasPassword() ? TokenPurpose.RESET : TokenPurpose.INVITE;
        Invite invite = newInvite(admin, purpose);
        audit.record(purpose == TokenPurpose.RESET ? "PASSWORD_RESET_ISSUED" : "INVITE_ISSUED", null, null, null,
                "Link emitido para " + admin.getEmail());
        return invite;
    }

    /** Ninguém suspende o próprio acesso: assim sempre sobra pelo menos um administrador ativo. */
    @Transactional
    public void setAdminSuspended(Long adminId, boolean suspended, Long actorId) {
        ClientAccount admin = requireAdmin(adminId);
        if (suspended && admin.getId().equals(actorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Você não pode suspender o seu próprio acesso.");
        }
        admin.setSuspended(suspended);
        accounts.save(admin);
        audit.record(suspended ? "ACCESS_SUSPENDED" : "ACCESS_RESTORED", null, null, null, admin.getEmail());
    }

    private ClientAccount requireAdmin(Long id) {
        return accounts.findById(id).filter(a -> a.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ClientAccount> findAccount(Long id) {
        return accounts.findById(id);
    }

    @Transactional(readOnly = true)
    public ClientAccount getAccount(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
    }

    private Invite newInvite(ClientAccount account, TokenPurpose purpose) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(TOKEN_VALIDITY);
        account.issueToken(hash(token), purpose, expires);
        accounts.save(account);
        return new Invite(account, token, expires);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
