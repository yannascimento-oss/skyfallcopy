package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.empresajr.chatjr.service.ProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload de PDF, extração, processamento (com IA, sem IA e com falha da IA), limites e recuperação. */
class ProcessingTest extends AbstractIntegrationTest {

    private static final String PLAN_TEXT = "O mercado cresce dez por cento ao ano.";

    @Value("${chatjr.data-dir}")
    private String dataDir;
    @Autowired
    private ProcessingService processing;

    private MockHttpSession admin;
    private String aiKey;
    private long clientId;
    private long tabId;
    private String url;

    /** Instala com chave da IA, cria um cliente e escolhe a etapa Mercado. */
    /** Instala com chave da IA, cria um cliente e escolhe a etapa Mercado. */
    @BeforeEach
    void prepare() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        aiKey = "sk-ant-api03-" + UUID.randomUUID().toString().replace("-", "");
        installWithAiKey(email, password, aiKey);
        admin = loginOk(email, password);
        clientId = createClientAndLogin(admin, "Norte").id();
        JsonNode tabs = json.readTree(mvc.perform(get("/api/clients/" + clientId + "/tabs").session(admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode t : tabs) {
            if (t.get("name").asText().equals("Mercado")) {
                tabId = t.get("id").asLong();
            }
        }
        url = "/api/admin/clients/" + clientId + "/tabs/" + tabId + "/attachment";
    }

    // ---------- apoio ----------

    private static byte[] pdf(int pages, String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (p == 0 && lines.length > 0) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 700);
                        for (String line : lines) {
                            if (!line.isEmpty()) {
                                cs.showText(line);
                            }
                            cs.newLineAtOffset(0, -16);
                        }
                        cs.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private ResultActions upload(byte[] bytes, String name) throws Exception {
        return mvc.perform(withCsrf(multipart(url).file(new MockMultipartFile("file", name, "application/pdf", bytes)))
                .session(admin));
    }

    private void uploadPlan() throws Exception {
        upload(pdf(1, "Mercado", PLAN_TEXT), "plano.pdf").andExpect(status().isOk());
    }

    private ResultActions processRequest() throws Exception {
        return mvc.perform(withCsrf(post(url + "/process")).session(admin));
    }

    private String state() {
        return jdbc.queryForObject("SELECT state FROM attachment WHERE tab_id = ?", String.class, tabId);
    }

    private void awaitState(String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(150))
                .until(() -> expected.equals(state()));
    }

    private JsonNode attachmentView() throws Exception {
        return json.readTree(mvc.perform(get(url).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode tabView() throws Exception {
        return json.readTree(mvc.perform(get("/api/clients/" + clientId + "/tabs/" + tabId).session(admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------- upload ----------

    @Test
    void upload_extractsTextAndStoresTheOriginalFile() throws Exception {
        upload(pdf(2, "Mercado", PLAN_TEXT), "meu plano.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPdf").value(true))
                .andExpect(jsonPath("$.pdfName").value("meu plano.pdf"))
                .andExpect(jsonPath("$.pdfPages").value(2))
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.processed").value(false));
        String text = jdbc.queryForObject("SELECT pdf_text FROM attachment WHERE tab_id = ?", String.class, tabId);
        assertTrue(text.contains("dez por cento"));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM tab_chunk WHERE tab_id = ?", Integer.class, tabId) > 0,
                "o PDF já fica consultável pelo chat logo no upload, sem precisar processar");
        assertTrue(Files.exists(Path.of(dataDir, "attachments", String.valueOf(clientId), tabId + ".pdf")));
    }

    @Test
    void upload_neverUsesTheClientSuppliedNameAsAPath() throws Exception {
        upload(pdf(1, PLAN_TEXT), "../../etc/passwd.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfName").value("passwd.pdf"));
        assertTrue(Files.exists(Path.of(dataDir, "attachments", String.valueOf(clientId), tabId + ".pdf")));
    }

    @Test
    void upload_secondFileReplacesTheFirst() throws Exception {
        upload(pdf(1, "Texto curto"), "a.pdf").andExpect(status().isOk());
        upload(pdf(1, "Texto bem mais comprido do que o primeiro arquivo enviado"), "b.pdf").andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfName").value("b.pdf"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM attachment WHERE tab_id = ?", Integer.class, tabId));
    }

    @Test
    void upload_refusesWhatIsNotAPdf() throws Exception {
        upload("isto é só texto".getBytes(StandardCharsets.UTF_8), "plano.pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O arquivo não parece ser um PDF."));
    }

    @Test
    void upload_refusesFilesOverTheConfiguredLimit() throws Exception {
        jdbc.update("INSERT INTO app_setting(setting_key, setting_value, secret, updated_at) VALUES ('limits.max_upload_mb','1',false, now())");
        byte[] big = new byte[2 * 1024 * 1024];
        System.arraycopy("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 8);
        upload(big, "grande.pdf").andExpect(status().isPayloadTooLarge());
    }

    @Test
    void upload_refusesMoreThanTwoHundredPages() throws Exception {
        upload(pdf(201), "enorme.pdf").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("200")));
    }

    @Test
    void upload_refusesPdfWithoutText() throws Exception {
        upload(pdf(1), "escaneado.pdf").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("texto")));
    }

    @Test
    void attachmentEndpoints_areClosedToClientsAndStrangers() throws Exception {
        ClientLogin other = createClientAndLogin(admin, "Outro");
        mvc.perform(withCsrf(multipart(url).file(new MockMultipartFile("file", "p.pdf", "application/pdf", pdf(1, PLAN_TEXT))))
                .session(other.session())).andExpect(status().isForbidden());
        mvc.perform(get(url).session(other.session())).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(url + "/process")).session(other.session())).andExpect(status().isForbidden());
        mvc.perform(withCsrf(multipart(url).file(new MockMultipartFile("file", "p.pdf", "application/pdf", pdf(1, PLAN_TEXT)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void slideLink_acceptsOnlyHttps() throws Exception {
        mvc.perform(withCsrf(put(url + "/link")).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slideLink\":\"https://docs.google.com/presentation/d/abc\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slideLink").value("https://docs.google.com/presentation/d/abc"));
        for (String bad : new String[]{"javascript:alert(1)", "http://inseguro.test/x", "https://com espaço.test", "ftp://x.test"}) {
            mvc.perform(withCsrf(put(url + "/link")).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("slideLink", bad)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void remove_deletesFileTextAndSearchChunksButKeepsTheTabContent() throws Exception {
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM tab_chunk WHERE tab_id = ?", Integer.class, tabId) > 0);

        mvc.perform(withCsrf(delete(url)).session(admin)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM attachment WHERE tab_id = ?", Integer.class, tabId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tab_chunk WHERE tab_id = ?", Integer.class, tabId));
        assertFalse(Files.exists(Path.of(dataDir, "attachments", String.valueOf(clientId), tabId + ".pdf")));
        assertTrue(tabView().get("html").asText().contains("dez por cento"), "o conteúdo da aba permanece");
    }

    // ---------- processamento ----------

    @Test
    void process_withAi_sendsTheRightRequestAndStoresTheResult() throws Exception {
        uploadPlan();
        processRequest().andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("PROCESSING"));
        awaitState("DONE");

        // O que foi enviado à IA.
        assertEquals(1, AI_CALLS.size());
        Map<String, String> call = AI_CALLS.get(0);
        assertEquals(aiKey, call.get("key"));
        assertEquals("2023-06-01", call.get("version"));
        assertEquals("/v1/messages", call.get("path"));
        JsonNode sent = json.readTree(call.get("body"));
        assertEquals("claude-sonnet-4-6", sent.get("model").asText());
        assertEquals(3000, sent.get("max_tokens").asInt());
        assertTrue(sent.get("system").asText().contains("SOMENTE o que está no documento"));
        String userMessage = sent.get("messages").get(0).get("content").asText();
        assertTrue(userMessage.contains("Etapa: Mercado") && userMessage.contains("dez por cento"));

        // O que foi gravado.
        JsonNode tab = tabView();
        assertEquals("<p>O mercado cresce dez por cento ao ano.</p>", tab.get("html").asText(), "o script da IA foi removido");
        assertEquals("Mercado em crescimento.", tab.get("shortDescription").asText());
        assertEquals(2, tab.get("keyPoints").size());
        assertEquals("plano.pdf", tab.get("source").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM plan_version WHERE tab_id = ? AND reason = 'PROCESS'", Integer.class, tabId));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM tab_chunk WHERE tab_id = ?", Integer.class, tabId) >= 1);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'TAB_PROCESSED' AND tab_id = ?", Integer.class, tabId));

        JsonNode view = attachmentView();
        assertEquals("DONE", view.get("state").asText());
        assertTrue(view.get("processed").asBoolean());
        assertEquals(2, view.get("extractedSections").asInt());
        assertTrue(view.get("message").isNull());
        assertFalse(view.toString().contains(aiKey), "a chave nunca aparece na API");

        // Registro da chamada: tokens e custo estimado.
        Map<String, Object> row = jdbc.queryForMap("SELECT kind, status, input_tokens, output_tokens, est_cost_microusd FROM ai_call_log");
        assertEquals("PROCESS", row.get("kind"));
        assertEquals("OK", row.get("status"));
        assertEquals(120, row.get("input_tokens"));
        assertEquals(80, row.get("output_tokens"));
        assertEquals(120L * 3 + 80L * 15, ((Number) row.get("est_cost_microusd")).longValue());
    }

    @Test
    void process_withoutAiKey_usesTheExtractiveFallback() throws Exception {
        jdbc.update("DELETE FROM app_setting WHERE setting_key = 'ai.key'");
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");

        assertEquals(0, AI_CALLS.size(), "sem chave não há chamada à IA");
        assertTrue(attachmentView().get("message").asText().contains("sem IA"));
        String html = tabView().get("html").asText();
        assertTrue(html.contains("dez por cento") && html.contains("<h4>Mercado</h4>"), html);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_call_log", Integer.class));
    }

    @Test
    void process_whenAiRejectsTheKey_fallsBackAndNeverLeaksTheKey() throws Exception {
        AI_REPLY.set(new StubReply(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key " + aiKey + "\"}}", 0));
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");

        String message = attachmentView().get("message").asText();
        assertTrue(message.contains("recusada"), message);
        assertFalse(message.contains(aiKey));
        assertTrue(tabView().get("html").asText().contains("dez por cento"), "o plano B gerou o conteúdo");
        Map<String, Object> row = jdbc.queryForMap("SELECT status, http_status, error_message FROM ai_call_log");
        assertEquals("ERROR", row.get("status"));
        assertEquals(401, row.get("http_status"));
        assertFalse(String.valueOf(row.get("error_message")).contains("sk-ant"));
    }

    @Test
    void process_whenAiAnswersOutsideTheFormat_fallsBack() throws Exception {
        AI_REPLY.set(new StubReply(200, aiEnvelope("Desculpe, não consegui ler o documento.", 50, 10), 0));
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");

        assertTrue(attachmentView().get("message").asText().contains("fora do formato"));
        assertEquals("FALLBACK", jdbc.queryForObject("SELECT status FROM ai_call_log", String.class));
        assertTrue(tabView().get("html").asText().contains("dez por cento"));
    }

    @Test
    void process_whenAiHtmlIsOnlyDangerousMarkup_fallsBack() throws Exception {
        AI_REPLY.set(new StubReply(200, aiEnvelope("{\"html\":\"<script>alert(1)</script>\",\"title\":\"X\"}", 50, 10), 0));
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");
        assertTrue(tabView().get("html").asText().contains("dez por cento"));
    }

    @Test
    void process_requiresAPdfFirst() throws Exception {
        processRequest().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Envie um PDF antes de processar."));
    }

    @Test
    void process_cannotStartTwiceAtTheSameTime() throws Exception {
        AI_REPLY.set(new StubReply(200, aiEnvelope(goodAiContent(), 120, 80), 1500));
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        processRequest().andExpect(status().isConflict());
        upload(pdf(1, "outro"), "outro.pdf").andExpect(status().isConflict());
        awaitState("DONE");
        assertEquals(1, AI_CALLS.size(), "só uma chamada à IA aconteceu");
    }

    @Test
    void process_respectsTheDailyLimit() throws Exception {
        jdbc.update("INSERT INTO app_setting(setting_key, setting_value, secret, updated_at) VALUES ('limits.processings_per_day','1',false, now())");
        uploadPlan();
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");
        processRequest().andExpect(status().isTooManyRequests());
    }

    @Test
    void interruptedProcessing_isMarkedAsFailedAtStartup() throws Exception {
        uploadPlan();
        jdbc.update("UPDATE attachment SET state = 'PROCESSING' WHERE tab_id = ?", tabId);
        processing.recoverInterrupted();
        assertEquals("FAILED", state());
        assertTrue(attachmentView().get("message").asText().contains("interrompido"));
        // E dá para processar de novo.
        processRequest().andExpect(status().isAccepted());
        awaitState("DONE");
        assertNotNull(tabView().get("html"));
    }
}
