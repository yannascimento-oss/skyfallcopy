package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Troca de senha pelo próprio usuário e indicadores do cliente. */
class AccountSelfServiceTest extends AbstractIntegrationTest {

    private MockHttpSession admin;
    private ClientLogin ana;
    private String anaPassword;

    @BeforeEach
    void prepare() throws Exception {
        admin = installAndLoginAdmin();
        anaPassword = randomPassword();
        String email = randomEmail();
        JsonNode created = createClient(admin, "Ana", email, "Ana Ltda");
        acceptInvite(created.get("inviteToken").asText(), anaPassword);
        ana = new ClientLogin(loginOk(email, anaPassword), created.get("client").get("id").asLong(), email);
    }

    private ResultActions changePassword(MockHttpSession who, String current, String next) throws Exception {
        return mvc.perform(withCsrf(post("/api/me/password")).session(who).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
    }

    @Test
    void changesThePasswordAndTheOldOneStopsWorking() throws Exception {
        String next = randomPassword();
        changePassword(ana.session(), anaPassword, next).andExpect(status().isNoContent());

        tryLogin(ana.email(), anaPassword).andExpect(status().isUnauthorized());
        loginOk(ana.email(), next);
        mvc.perform(get("/api/me").session(ana.session())).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_CHANGED'", Integer.class));
    }

    @Test
    void worksForAdministratorsToo() throws Exception {
        String next = randomPassword();
        changePassword(admin, adminPassword, next).andExpect(status().isNoContent());
        tryLogin(adminEmail, adminPassword).andExpect(status().isUnauthorized());
        loginOk(adminEmail, next);
    }

    @Test
    void wrongCurrentPasswordIsRefusedAndCountsTowardsTheLock() throws Exception {
        String next = randomPassword();
        for (int i = 0; i < 5; i++) {
            changePassword(ana.session(), randomPassword(), next).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A senha atual está incorreta."));
        }
        // A sexta tentativa, mesmo com a senha certa, já está bloqueada; o login também.
        changePassword(ana.session(), anaPassword, next).andExpect(status().isTooManyRequests());
        tryLogin(ana.email(), anaPassword).andExpect(status().isTooManyRequests());
        assertNotNull(jdbc.queryForObject("SELECT locked_until FROM client_account WHERE email = ?", Object.class, ana.email()));
    }

    @Test
    void refusesWeakOrUnchangedNewPassword() throws Exception {
        changePassword(ana.session(), anaPassword, "curta1").andExpect(status().isBadRequest());
        changePassword(ana.session(), anaPassword, "somente-letras-aqui").andExpect(status().isBadRequest());
        changePassword(ana.session(), anaPassword, anaPassword).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha precisa ser diferente da atual."));
        loginOk(ana.email(), anaPassword);
    }

    @Test
    void requiresASessionAndACsrfToken() throws Exception {
        mvc.perform(withCsrf(post("/api/me/password")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/password").session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}")).andExpect(status().isForbidden());
    }

    @Test
    void clientSeesOnlyTheirOwnIndicators() throws Exception {
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        long tab = json.readTree(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0).get("id").asLong();
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab)).session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Resumo", "html", "<p>Plano de academias.</p>", "published", true))))
                .andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/chat")).session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Fale do plano de academias\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/chat/indicators").session(ana.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/chat/indicators").session(bia.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/chat/indicators").session(admin)).andExpect(status().isForbidden());
    }
}
