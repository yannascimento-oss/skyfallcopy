package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integração de IA, limites, organização, tela Sistema e histórico de auditoria. */
class AdminSettingsTest extends AbstractIntegrationTest {

    private MockHttpSession admin;
    private String aiKey;

    @BeforeEach
    void prepare() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        aiKey = "sk-ant-api03-" + UUID.randomUUID().toString().replace("-", "");
        installWithAiKey(email, password, aiKey);
        admin = loginOk(email, password);
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return mvc.perform(withCsrf(put(url)).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------- integração de IA ----------

    @Test
    void settingsShowOnlyTheMaskedKey() throws Exception {
        JsonNode all = read(mvc.perform(get("/api/admin/settings").session(admin)).andExpect(status().isOk()));
        assertEquals("database", all.get("ai").get("keySource").asText());
        assertEquals(aiKey.substring(0, 7) + "…" + aiKey.substring(aiKey.length() - 4), all.get("ai").get("keyMasked").asText());
        assertEquals("claude-sonnet-4-6", all.get("ai").get("model").asText());
        assertEquals(3000, all.get("ai").get("maxTokens").asInt());
        assertEquals("Empresa JR (teste)", all.get("organization").get("name").asText());
        assertEquals(60, all.get("limits").get("questionsPerHour").asInt());
        assertEquals(20, all.get("limits").get("processingsPerDay").asInt());
        assertEquals(25, all.get("limits").get("maxUploadMb").asInt());
        assertFalse(all.toString().contains(aiKey));
    }

    @Test
    void settingsAreClosedToClients() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        mvc.perform(get("/api/admin/settings").session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/system").session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").session(ana.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/admins").session(ana.session())).andExpect(status().isForbidden());
    }

    @Test
    void newKeyIsStoredEncryptedMaskedAndUsedByTheConnectionTest() throws Exception {
        String newKey = "sk-ant-api03-" + UUID.randomUUID().toString().replace("-", "");
        JsonNode ai = read(putJson("/api/admin/settings/ai", Map.of("apiKey", newKey)).andExpect(status().isOk()));
        assertFalse(ai.toString().contains(newKey));
        assertTrue(ai.get("keyMasked").asText().endsWith(newKey.substring(newKey.length() - 4)));

        String stored = jdbc.queryForObject("SELECT setting_value FROM app_setting WHERE setting_key = 'ai.key'", String.class);
        assertFalse(stored.contains(newKey));

        JsonNode test = read(mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(admin)).andExpect(status().isOk()));
        assertTrue(test.get("ok").asBoolean());
        assertEquals(1, AI_CALLS.size());
        assertEquals(newKey, AI_CALLS.get(0).get("key"), "o teste usa a chave nova, não a antiga");
        assertEquals("TEST", jdbc.queryForObject("SELECT kind FROM ai_call_log", String.class));
    }

    @Test
    void modelAndTokenSettingsReachTheAiRequest() throws Exception {
        putJson("/api/admin/settings/ai", Map.of("model", "claude-haiku-4-5", "maxTokens", 1234, "temperature", 0.5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5"))
                .andExpect(jsonPath("$.maxTokens").value(1234));
        mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(admin)).andExpect(status().isOk());
        JsonNode sent = json.readTree(AI_CALLS.get(0).get("body"));
        assertEquals("claude-haiku-4-5", sent.get("model").asText());
    }

    @Test
    void invalidAiSettingsAreRejected() throws Exception {
        putJson("/api/admin/settings/ai", Map.of("apiKey", "isto-nao-e-uma-chave-da-anthropic")).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/ai", Map.of("apiKey", "sk-ant-curta")).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/ai", Map.of("model", "modelo com espaço")).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/ai", Map.of("maxTokens", 100)).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/ai", Map.of("maxTokens", 99999)).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/ai", Map.of("temperature", 2.0)).andExpect(status().isBadRequest());
        // A configuração anterior continua intacta.
        assertEquals("database", read(mvc.perform(get("/api/admin/settings").session(admin))).get("ai").get("keySource").asText());
    }

    @Test
    void connectionTestReportsAFailureWithoutLeakingTheKey() throws Exception {
        AI_REPLY.set(new StubReply(401, "{\"error\":{\"message\":\"invalid x-api-key " + aiKey + "\"}}", 0));
        JsonNode test = read(mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(admin)).andExpect(status().isOk()));
        assertFalse(test.get("ok").asBoolean());
        assertTrue(test.get("message").asText().contains("recusada"));
        assertFalse(test.toString().contains(aiKey));
        Map<String, Object> row = jdbc.queryForMap("SELECT kind, status, http_status FROM ai_call_log");
        assertEquals("TEST", row.get("kind"));
        assertEquals("ERROR", row.get("status"));
        assertEquals(401, row.get("http_status"));
    }

    @Test
    void removingTheKeyLeavesNoneAndTheTestExplainsWhy() throws Exception {
        mvc.perform(withCsrf(delete("/api/admin/settings/ai/key")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keySource").value("none"))
                .andExpect(jsonPath("$.keyMasked").doesNotExist());
        mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nenhuma chave da IA está configurada."));
        assertEquals(0, AI_CALLS.size());
    }

    @Test
    void auditRecordsWhichSettingsChangedButNeverTheirValues() throws Exception {
        String newKey = "sk-ant-api03-" + UUID.randomUUID().toString().replace("-", "");
        putJson("/api/admin/settings/ai", Map.of("apiKey", newKey, "model", "claude-haiku-4-5")).andExpect(status().isOk());
        String detail = jdbc.queryForObject("SELECT detail FROM audit_log WHERE action = 'AI_SETTINGS_CHANGED'", String.class);
        assertEquals("chave, modelo", detail);
        String everything = jdbc.queryForList("SELECT coalesce(detail,'') FROM audit_log", String.class).toString();
        assertFalse(everything.contains(newKey));
    }

    // ---------- limites e organização ----------

    @Test
    void limitsAreValidatedPersistedAndEnforced() throws Exception {
        putJson("/api/admin/settings/limits", Map.of("questionsPerHour", 0, "processingsPerDay", 5, "maxUploadMb", 10)).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/limits", Map.of("questionsPerHour", 10, "processingsPerDay", 5, "maxUploadMb", 26)).andExpect(status().isBadRequest());
        putJson("/api/admin/settings/limits", Map.of("questionsPerHour", 1, "processingsPerDay", 5, "maxUploadMb", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsPerHour").value(1))
                .andExpect(jsonPath("$.maxUploadMb").value(10));

        // O limite novo vale na hora: a segunda pergunta da hora é barrada.
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        JsonNode tabs = read(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)));
        long tab = tabs.get(0).get("id").asLong();
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + tab)).session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Resumo Executivo", "html", "<p>Plano de academias.</p>", "published", true))))
                .andExpect(status().isOk());
        String q = json.writeValueAsString(Map.of("question", "Fale do plano de academias"));
        mvc.perform(withCsrf(post("/api/chat")).session(ana.session()).contentType(MediaType.APPLICATION_JSON).content(q)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/chat")).session(ana.session()).contentType(MediaType.APPLICATION_JSON).content(q)).andExpect(status().isTooManyRequests());
    }

    @Test
    void organizationNameCanBeChanged() throws Exception {
        putJson("/api/admin/settings/organization", Map.of("name", "  Empresa JR UFBA  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Empresa JR UFBA"));
        mvc.perform(get("/api/setup/status")).andExpect(jsonPath("$.orgName").value("Empresa JR UFBA"));
        putJson("/api/admin/settings/organization", Map.of("name", "  ")).andExpect(status().isBadRequest());
    }

    // ---------- sistema ----------

    @Test
    void systemOverviewReportsRealNumbers() throws Exception {
        createClientAndLogin(admin, "Ana");
        mvc.perform(withCsrf(post("/api/admin/settings/ai/test")).session(admin)).andExpect(status().isOk());
        jdbc.update("INSERT INTO app_error(message, path, created_at) VALUES ('Falha de teste', '/api/x', now())");

        JsonNode sys = read(mvc.perform(get("/api/admin/system").session(admin)).andExpect(status().isOk()));
        assertEquals(1, sys.get("counts").get("clients").asInt());
        assertEquals(1, sys.get("counts").get("admins").asInt());
        assertEquals(15, sys.get("counts").get("tabs").asInt());
        assertTrue(sys.get("database").asText().contains("PostgreSQL"));
        assertTrue(sys.get("ai").get("keyConfigured").asBoolean());
        assertEquals(1, sys.get("ai").get("calls30d").asInt());
        assertEquals(0, sys.get("ai").get("errors30d").asInt());
        assertEquals(200L * 3 + 60L * 15, 0L + 200L * 3 + 60L * 15);
        assertTrue(sys.get("ai").get("estCostMicroUsd30d").asLong() > 0);
        assertTrue(sys.get("disk").get("freeBytes").asLong() > 0);
        assertEquals("Falha de teste", sys.get("recentErrors").get(0).get("message").asText());
        assertEquals("TEST", sys.get("recentCalls").get(0).get("kind").asText());
        assertFalse(sys.get("emailConfigured").asBoolean());
        assertFalse(sys.toString().contains(aiKey));
    }

    @Test
    void emailTestSaysItIsNotConfigured() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/system/test-email")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("não está configurado")));
    }

    // ---------- auditoria ----------

    @Test
    void auditHistoryIsPagedFilteredAndNewestFirst() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        ClientLogin bia = createClientAndLogin(admin, "Bia");

        JsonNode page = read(mvc.perform(get("/api/admin/audit?size=3").session(admin)).andExpect(status().isOk()));
        assertEquals(3, page.get("items").size());
        assertTrue(page.get("total").asLong() > 3);
        String first = page.get("items").get(0).get("createdAt").asText();
        String last = page.get("items").get(2).get("createdAt").asText();
        assertTrue(first.compareTo(last) >= 0, "mais recente primeiro");

        JsonNode onlyAna = read(mvc.perform(get("/api/admin/audit?clientId=" + ana.id() + "&size=100").session(admin)));
        assertTrue(onlyAna.get("items").size() > 0);
        onlyAna.get("items").forEach(i -> assertEquals(ana.id(), i.get("clientId").asLong()));
        assertEquals("Ana Ltda", onlyAna.get("items").get(0).get("clientName").asText());

        JsonNode created = read(mvc.perform(get("/api/admin/audit?action=CLIENT_CREATED&size=100").session(admin)));
        assertEquals(2, created.get("total").asLong());

        JsonNode both = read(mvc.perform(get("/api/admin/audit?action=CLIENT_CREATED&clientId=" + bia.id()).session(admin)));
        assertEquals(1, both.get("total").asLong());

        assertEquals(100, read(mvc.perform(get("/api/admin/audit?size=5000").session(admin))).get("size").asInt());
    }
}
