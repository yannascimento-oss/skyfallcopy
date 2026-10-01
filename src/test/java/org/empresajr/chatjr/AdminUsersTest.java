package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Administradores, edição e exclusão de clientes. */
class AdminUsersTest extends AbstractIntegrationTest {

    @Value("${chatjr.data-dir}")
    private String dataDir;

    private MockHttpSession admin;
    private String adminEmail;

    @BeforeEach
    void prepare() throws Exception {
        adminEmail = randomEmail();
        String password = randomPassword();
        install(adminEmail, password);
        admin = loginOk(adminEmail, password);
    }

    private JsonNode createAdmin(String name, String email) throws Exception {
        return json.readTree(mvc.perform(withCsrf(post("/api/admin/admins")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name, "email", email))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void newAdminSetsPasswordThroughTheInviteAndCanManage() throws Exception {
        String email = randomEmail();
        JsonNode invite = createAdmin("Bruno", email);
        assertFalse(invite.get("client").get("passwordSet").asBoolean());
        tryLogin(email, randomPassword()).andExpect(status().isUnauthorized());

        String password = randomPassword();
        acceptInvite(invite.get("inviteToken").asText(), password);
        MockHttpSession bruno = loginOk(email, password);
        mvc.perform(get("/api/me").session(bruno)).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admin/clients").session(bruno)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/admins").session(admin)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void duplicateAdminEmailIsRefused() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/admins")).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Outro", "email", adminEmail.toUpperCase()))))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotSuspendTheirOwnAccessButCanSuspendAnother() throws Exception {
        String email = randomEmail();
        JsonNode invite = createAdmin("Bruno", email);
        String password = randomPassword();
        acceptInvite(invite.get("inviteToken").asText(), password);
        MockHttpSession bruno = loginOk(email, password);
        long brunoId = invite.get("client").get("id").asLong();
        long myId = json.readTree(mvc.perform(get("/api/me").session(admin)).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(withCsrf(post("/api/admin/admins/" + myId + "/suspend")).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você não pode suspender o seu próprio acesso."));

        mvc.perform(withCsrf(post("/api/admin/admins/" + brunoId + "/suspend")).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(bruno)).andExpect(status().isUnauthorized());
        tryLogin(email, password).andExpect(status().isForbidden());

        mvc.perform(withCsrf(post("/api/admin/admins/" + brunoId + "/activate")).session(admin)).andExpect(status().isNoContent());
        loginOk(email, password);
    }

    @Test
    void adminEndpointsDoNotTouchClientAccounts() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        mvc.perform(withCsrf(post("/api/admin/admins/" + ana.id() + "/suspend")).session(admin)).andExpect(status().isNotFound());
        mvc.perform(withCsrf(post("/api/admin/admins/" + ana.id() + "/invite")).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void adminCanReissueTheirInviteLink() throws Exception {
        JsonNode first = createAdmin("Bruno", randomEmail());
        long id = first.get("client").get("id").asLong();
        JsonNode second = json.readTree(mvc.perform(withCsrf(post("/api/admin/admins/" + id + "/invite")).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(!first.get("inviteToken").asText().equals(second.get("inviteToken").asText()));
        // O link anterior deixa de valer.
        String body = json.writeValueAsString(Map.of("token", first.get("inviteToken").asText(), "password", randomPassword()));
        mvc.perform(withCsrf(post("/api/auth/accept-invite")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        acceptInvite(second.get("inviteToken").asText(), randomPassword());
    }

    @Test
    void clientDataCanBeEditedButNotTheEmail() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id())).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Ana Souza", "company", "Norte Fit", "segment", "Academias"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Norte Fit"))
                .andExpect(jsonPath("$.segment").value("Academias"))
                .andExpect(jsonPath("$.email").value(ana.email()));
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id())).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"company\":\"X\"}")).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(put("/api/admin/clients/999999")).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"company\":\"B\"}")).andExpect(status().isNotFound());
    }

    @Test
    void deletingAClientRequiresTheirEmailAndRemovesEverything() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        JsonNode tabs = json.readTree(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)).andReturn().getResponse().getContentAsString());
        long tab = tabs.get(0).get("id").asLong();
        mvc.perform(withCsrf(multipart("/api/admin/clients/" + ana.id() + "/tabs/" + tab + "/attachment")
                .file(new MockMultipartFile("file", "p.pdf", "application/pdf", buildPdf("Resumo", "Texto do plano da Ana.")))).session(admin))
                .andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab)).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Resumo", "html", "<p>Texto do plano da Ana.</p>", "published", true)))).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/chat")).session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"texto do plano\"}")).andExpect(status().isOk());
        Path folder = Path.of(dataDir, "attachments", String.valueOf(ana.id()));
        assertTrue(Files.exists(folder));

        // Sem confirmação, ou com o e-mail errado, nada é apagado.
        mvc.perform(withCsrf(delete("/api/admin/clients/" + ana.id())).session(admin)).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(delete("/api/admin/clients/" + ana.id() + "?confirmEmail=outro@x.test")).session(admin)).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM client_account WHERE role = 'CLIENT'", Integer.class));

        mvc.perform(withCsrf(delete("/api/admin/clients/" + ana.id() + "?confirmEmail=" + ana.email().toUpperCase())).session(admin))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM client_account WHERE role = 'CLIENT'", Integer.class));
        for (String table : new String[]{"plan_tab", "attachment", "tab_chunk", "conversation", "chat_message", "query_log", "plan_version"}) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class), table);
        }
        assertFalse(Files.exists(folder), "a pasta de arquivos do cliente foi removida");
        // A trilha de auditoria sobrevive à exclusão.
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'CLIENT_DELETED'", Integer.class));
        // O acesso do cliente excluído deixa de existir.
        mvc.perform(get("/api/me").session(ana.session())).andExpect(status().isUnauthorized());
        tryLogin(ana.email(), randomPassword()).andExpect(status().isUnauthorized());
    }

    @Test
    void clientsCannotEditOrDeleteClients() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        mvc.perform(withCsrf(put("/api/admin/clients/" + bia.id())).session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"company\":\"B\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/clients/" + bia.id() + "?confirmEmail=" + bia.email())).session(ana.session()))
                .andExpect(status().isForbidden());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM client_account WHERE role = 'CLIENT'", Integer.class));
    }
}
