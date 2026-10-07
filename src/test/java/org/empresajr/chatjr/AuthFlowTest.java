package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Instalação inicial, login, bloqueio, convite, suspensão e separação entre administrador e cliente. */
class AuthFlowTest extends AbstractIntegrationTest {

    @Test
    void beforeInstall_statusSaysSetupIsNeeded() throws Exception {
        mvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsSetup").value(true));
    }

    @Test
    void beforeInstall_loginIsRefused() throws Exception {
        tryLogin(randomEmail(), randomPassword()).andExpect(status().isConflict());
    }

    @Test
    void install_createsAdminAndAllowsLogin() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        install(email, password);

        mvc.perform(get("/api/setup/status"))
                .andExpect(jsonPath("$.needsSetup").value(false))
                .andExpect(jsonPath("$.orgName").value("Empresa JR (teste)"));

        MockHttpSession session = loginOk(email, password);
        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void install_canOnlyRunOnce() throws Exception {
        install(randomEmail(), randomPassword());
        String body = json.writeValueAsString(Map.of("orgName", "Outra", "adminName", "Outro",
                "adminEmail", randomEmail(), "password", randomPassword()));
        mvc.perform(withCsrf(post("/api/setup")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void install_rejectsWeakPassword() throws Exception {
        String body = json.writeValueAsString(Map.of("orgName", "X", "adminName", "Y",
                "adminEmail", randomEmail(), "password", "curta"));
        mvc.perform(withCsrf(post("/api/setup")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/setup/status")).andExpect(jsonPath("$.needsSetup").value(true));
    }

    @Test
    void post_withoutCsrfToken_isForbidden() throws Exception {
        install(randomEmail(), randomPassword());
        String body = json.writeValueAsString(Map.of("email", "a@b.test", "password", "qualquer"));
        ResultActions result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertTrue(messageOf(result).contains("Recarregue"));
    }

    @Test
    void wrongPasswordAndUnknownEmail_giveTheSameMessage() throws Exception {
        String email = randomEmail();
        install(email, randomPassword());
        String wrongPassword = messageOf(tryLogin(email, randomPassword()).andExpect(status().isUnauthorized()));
        String unknownEmail = messageOf(tryLogin(randomEmail(), randomPassword()).andExpect(status().isUnauthorized()));
        assertEquals(wrongPassword, unknownEmail);
    }

    @Test
    void fiveWrongPasswords_lockTheAccountEvenForTheRightPassword() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        install(email, password);

        for (int i = 0; i < 5; i++) {
            tryLogin(email, randomPassword()).andExpect(status().isUnauthorized());
        }
        tryLogin(email, password).andExpect(status().isTooManyRequests());
        Object lockedUntil = jdbc.queryForObject(
                "SELECT locked_until FROM client_account WHERE email = ?", Object.class, email);
        assertNotNull(lockedUntil);
    }

    @Test
    void successfulLogin_resetsFailedAttempts() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        install(email, password);
        for (int i = 0; i < 3; i++) {
            tryLogin(email, randomPassword()).andExpect(status().isUnauthorized());
        }
        loginOk(email, password);
        Integer failed = jdbc.queryForObject(
                "SELECT failed_attempts FROM client_account WHERE email = ?", Integer.class, email);
        assertEquals(0, failed);
    }

    @Test
    void unauthenticatedRequests_getJson401() throws Exception {
        install(randomEmail(), randomPassword());
        ResultActions result = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertFalse(messageOf(result).isBlank());
    }

    @Test
    void inviteFlow_clientSetsPasswordAndIsKeptOutOfAdminArea() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);

        String clientEmail = randomEmail();
        JsonNode created = createClient(admin, "Ana Cliente", clientEmail.toUpperCase(), "Norte Fit");
        String token = created.get("inviteToken").asText();
        assertEquals(clientEmail, created.get("client").get("email").asText(), "o e-mail é normalizado em minúsculas");
        assertFalse(created.get("client").get("passwordSet").asBoolean());

        // Antes de aceitar o convite não há senha que funcione.
        tryLogin(clientEmail, randomPassword()).andExpect(status().isUnauthorized());

        // O banco guarda só o hash do token, nunca o token.
        String stored = jdbc.queryForObject("SELECT token_hash FROM client_account WHERE email = ?", String.class, clientEmail);
        assertNotEquals(token, stored);

        String password = randomPassword();
        acceptInvite(token, password);

        MockHttpSession client = loginOk(clientEmail, password);
        mvc.perform(get("/api/me").session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.company").value("Norte Fit"));
        mvc.perform(get("/api/admin/clients").session(client)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/clients")).session(client).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/clients").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(clientEmail))
                .andExpect(jsonPath("$[0].passwordSet").value(true));
    }

    @Test
    void invite_isSingleUse() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);
        String token = createClient(admin, "Bia", randomEmail(), "Bia Doces").get("inviteToken").asText();

        acceptInvite(token, randomPassword());
        String body = json.writeValueAsString(Map.of("token", token, "password", randomPassword()));
        mvc.perform(withCsrf(post("/api/auth/accept-invite")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_expiresAfterItsValidity() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);
        String clientEmail = randomEmail();
        String token = createClient(admin, "Caio", clientEmail, "Caio SA").get("inviteToken").asText();

        jdbc.update("UPDATE client_account SET token_expires_at = now() - interval '1 hour' WHERE email = ?", clientEmail);
        String body = json.writeValueAsString(Map.of("token", token, "password", randomPassword()));
        mvc.perform(withCsrf(post("/api/auth/accept-invite")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_rejectsWeakPasswordAndKeepsTokenUsable() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);
        String token = createClient(admin, "Duda", randomEmail(), "Duda Ltda").get("inviteToken").asText();

        String weak = json.writeValueAsString(Map.of("token", token, "password", "12345"));
        mvc.perform(withCsrf(post("/api/auth/accept-invite")).contentType(MediaType.APPLICATION_JSON).content(weak))
                .andExpect(status().isBadRequest());
        acceptInvite(token, randomPassword());
    }

    @Test
    void createClient_refusesDuplicateEmail() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);
        String clientEmail = randomEmail();
        createClient(admin, "Eva", clientEmail, "Eva SA");

        String body = json.writeValueAsString(Map.of("name", "Outra Eva", "email", clientEmail, "company", "Outra"));
        mvc.perform(withCsrf(post("/api/admin/clients")).session(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void suspension_blocksNewLoginsAndEndsOpenSessions() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);

        String clientEmail = randomEmail();
        JsonNode created = createClient(admin, "Fábio", clientEmail, "Fábio ME");
        String clientPassword = randomPassword();
        acceptInvite(created.get("inviteToken").asText(), clientPassword);
        long clientId = created.get("client").get("id").asLong();
        MockHttpSession client = loginOk(clientEmail, clientPassword);

        mvc.perform(withCsrf(post("/api/admin/clients/" + clientId + "/suspend")).session(admin))
                .andExpect(status().isNoContent());

        // Sessão que já estava aberta deixa de funcionar.
        mvc.perform(get("/api/me").session(client)).andExpect(status().isUnauthorized());
        // Novo login: com a senha certa vem o aviso de suspensão; com a errada, a mensagem genérica.
        tryLogin(clientEmail, clientPassword).andExpect(status().isForbidden());
        tryLogin(clientEmail, randomPassword()).andExpect(status().isUnauthorized());

        mvc.perform(withCsrf(post("/api/admin/clients/" + clientId + "/activate")).session(admin))
                .andExpect(status().isNoContent());
        loginOk(clientEmail, clientPassword);
    }

    @Test
    void logout_endsTheSession() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        install(email, password);
        MockHttpSession session = loginOk(email, password);
        mvc.perform(withCsrf(post("/api/auth/logout")).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void auditTrail_recordsSetupLoginAndClientCreation() throws Exception {
        String adminEmail = randomEmail();
        String adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        MockHttpSession admin = loginOk(adminEmail, adminPassword);
        createClient(admin, "Gabi", randomEmail(), "Gabi SA");

        for (String action : new String[]{"SETUP", "LOGIN", "CLIENT_CREATED"}) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ?", Integer.class, action);
            assertEquals(1, count, "ação " + action);
        }
    }

    @Test
    void aiKey_isStoredEncryptedAndNeverReturnedByTheApi() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        String aiKey = "sk-ant-api03-" + java.util.UUID.randomUUID().toString().replace("-", "");
        installWithAiKey(email, password, aiKey);

        String stored = jdbc.queryForObject(
                "SELECT setting_value FROM app_setting WHERE setting_key = 'ai.key'", String.class);
        assertNotNull(stored);
        assertFalse(stored.contains(aiKey), "a chave não pode ficar em texto puro no banco");
        Boolean secret = jdbc.queryForObject(
                "SELECT secret FROM app_setting WHERE setting_key = 'ai.key'", Boolean.class);
        assertTrue(secret);

        MockHttpSession session = loginOk(email, password);
        for (String path : new String[]{"/api/me", "/api/setup/status", "/api/admin/clients"}) {
            String body = mvc.perform(get(path).session(session)).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertFalse(body.contains(aiKey), "a chave vazou em " + path);
        }
    }
}
