package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem enxerga o quê no plano: rascunho, escopo, separação entre clientes, sanitização e versões. */
class PlanAccessTest extends AbstractIntegrationTest {

    private MockHttpSession admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = installAndLoginAdmin();
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long tabId(MockHttpSession who, long clientId, String name) throws Exception {
        JsonNode tabs = body(mvc.perform(get("/api/clients/" + clientId + "/tabs").session(who)).andExpect(status().isOk()));
        for (JsonNode t : tabs) {
            if (t.get("name").asText().equals(name)) {
                return t.get("id").asLong();
            }
        }
        throw new AssertionError("etapa não encontrada: " + name);
    }

    private String updateJson(String title, String html, Boolean published) throws Exception {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("title", title);
        m.put("html", html);
        m.put("shortDescription", "Resumo curto");
        m.put("whatIsIt", "O que é");
        m.put("objective", "Objetivo");
        m.put("keyPoints", List.of("Ponto A", "Ponto B"));
        m.put("suggestedQuestions", List.of("Qual é a meta?"));
        m.put("source", "plano.pdf");
        if (published != null) {
            m.put("published", published);
        }
        return json.writeValueAsString(m);
    }

    private JsonNode update(long clientId, long tabId, String title, String html, Boolean published) throws Exception {
        return body(mvc.perform(withCsrf(put("/api/admin/clients/" + clientId + "/tabs/" + tabId)).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(updateJson(title, html, published)))
                .andExpect(status().isOk()));
    }

    @Test
    void newClientGetsFifteenDraftTabsThatTheClientCannotSeeYet() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");

        mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].name").value("Resumo Executivo"))
                .andExpect(jsonPath("$[0].published").value(false))
                .andExpect(jsonPath("$[0].allowed").value(true));

        mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(ana.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clientSeesOnlyPublishedTabsAndNeverTheAdminFlags() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long mercado = tabId(admin, ana.id(), "Mercado");
        update(ana.id(), mercado, "Mercado", "<p>O mercado cresce.</p>", true);

        JsonNode tabs = body(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(ana.session()))
                .andExpect(status().isOk()));
        assertEquals(1, tabs.size());
        assertEquals("Mercado", tabs.get(0).get("name").asText());
        assertFalse(tabs.get(0).has("published"), "o cliente não recebe campos de gestão");
        assertFalse(tabs.get(0).has("html"), "a lista não carrega o conteúdo");

        mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + mercado).session(ana.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<p>O mercado cresce.</p>"))
                .andExpect(jsonPath("$.keyPoints.length()").value(2));
    }

    @Test
    void draftTabIsNotFoundForTheClient() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long riscos = tabId(admin, ana.id(), "Riscos");
        mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + riscos).session(ana.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blockedScopeHidesAPublishedTabFromTheClientOnly() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Plano Financeiro");
        update(ana.id(), tab, "Plano Financeiro", "<p>Receita prevista.</p>", true);

        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab + "/scope")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(ana.session()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + tab).session(ana.session()))
                .andExpect(status().isNotFound());

        JsonNode adminView = body(mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + tab).session(admin))
                .andExpect(status().isOk()));
        assertFalse(adminView.get("allowed").asBoolean());

        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab + "/scope")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + tab).session(ana.session()))
                .andExpect(status().isOk());
    }

    @Test
    void oneClientNeverReachesAnotherClientsPlan() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        long biaTab = tabId(admin, bia.id(), "Empresa");
        update(bia.id(), biaTab, "Empresa", "<p>Segredo da Bia.</p>", true);

        // Pelo caminho do plano da Bia: 403.
        mvc.perform(get("/api/clients/" + bia.id() + "/tabs").session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/clients/" + bia.id() + "/tabs/" + biaTab).session(ana.session())).andExpect(status().isForbidden());
        // Pelo caminho do próprio plano, com o id de uma aba alheia: 404, sem vazar o conteúdo.
        String leaked = mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + biaTab).session(ana.session()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(leaked.contains("Segredo"));
        // A própria Bia lê normalmente.
        mvc.perform(get("/api/clients/" + bia.id() + "/tabs/" + biaTab).session(bia.session())).andExpect(status().isOk());
    }

    @Test
    void clientCannotUseAdminEndpoints() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Metas");
        String base = "/api/admin/clients/" + ana.id() + "/tabs/" + tab;

        mvc.perform(withCsrf(put(base)).session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Metas", "<p>hack</p>", true))).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put(base + "/scope")).session(ana.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowed\":true}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete(base)).session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/versions").session(ana.session())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/clients/1/tabs")).andExpect(status().isUnauthorized());
    }

    @Test
    void savedHtmlIsSanitized() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Concorrentes");
        JsonNode saved = update(ana.id(), tab, "Concorrentes",
                "<p onclick=\"x()\">Oi</p><script>alert(1)</script><img src=x onerror=alert(1)>", true);
        assertEquals("<p>Oi</p>", saved.get("html").asText());
    }

    @Test
    void editsAreVersionedAndCanBeRestored() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Riscos");
        String versionsUrl = "/api/admin/clients/" + ana.id() + "/tabs/" + tab + "/versions";

        update(ana.id(), tab, "Riscos", "<p>Primeira versão.</p>", true);
        JsonNode second = update(ana.id(), tab, "Riscos", "<p>Segunda versão.</p>", null);
        assertEquals(2, second.get("contentVersion").asInt());

        JsonNode versions = body(mvc.perform(get(versionsUrl).session(admin)).andExpect(status().isOk()));
        assertEquals(2, versions.size(), "uma versão guardada por alteração de conteúdo");
        assertEquals(1, versions.get(0).get("version").asInt());

        // Restaura o estado em que a aba tinha a "Primeira versão" (versão guardada nº 1).
        JsonNode restored = body(mvc.perform(withCsrf(post(versionsUrl + "/1/restore")).session(admin))
                .andExpect(status().isOk()));
        assertEquals("<p>Primeira versão.</p>", restored.get("html").asText());
        assertEquals(3, restored.get("contentVersion").asInt());

        // A restauração também guardou o estado anterior; dá para desfazê-la.
        assertEquals(3, body(mvc.perform(get(versionsUrl).session(admin))).size());
        mvc.perform(withCsrf(post(versionsUrl + "/99/restore")).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void changingOnlyPublicationDoesNotCreateAVersion() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Metas");
        JsonNode current = body(mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + tab).session(admin))
                .andExpect(status().isOk()));

        // Reenvia exatamente o conteúdo atual, mudando só a publicação.
        Map<String, Object> same = new java.util.HashMap<>();
        same.put("title", current.get("title").asText());
        same.put("html", current.get("html").asText());
        same.put("shortDescription", current.get("shortDescription").asText());
        same.put("whatIsIt", current.get("whatIsIt").asText());
        same.put("objective", current.get("objective").asText());
        same.put("keyPoints", List.of());
        same.put("suggestedQuestions", List.of());
        same.put("published", true);
        JsonNode result = body(mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab)).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(same)))
                .andExpect(status().isOk()));

        assertTrue(result.get("published").asBoolean());
        assertEquals(0, result.get("contentVersion").asInt());
        Integer versions = jdbc.queryForObject("SELECT count(*) FROM plan_version WHERE tab_id = ?", Integer.class, tab);
        assertEquals(0, versions);
    }

    @Test
    void createAndDeleteTabs() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        JsonNode created = body(mvc.perform(withCsrf(post("/api/admin/clients/" + ana.id() + "/tabs")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mercado\"}"))
                .andExpect(status().isCreated()));
        assertEquals("mercado-2", created.get("slug").asText(), "o slug repetido ganha sufixo");

        long id = created.get("id").asLong();
        mvc.perform(withCsrf(delete("/api/admin/clients/" + ana.id() + "/tabs/" + id)).session(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/clients/" + ana.id() + "/tabs/" + id).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void planActionsAreAudited() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        long tab = tabId(admin, ana.id(), "Metas");
        update(ana.id(), tab, "Metas", "<p>x</p>", true);
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab + "/scope")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}")).andExpect(status().isNoContent());

        for (String action : List.of("TAB_UPDATED", "TAB_PUBLISHED", "SCOPE_CHANGED")) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND tab_id = ?",
                    Integer.class, action, tab);
            assertTrue(n != null && n == 1, action);
        }
    }
}
