package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exportação do plano em JSON e PDF. */
class ExportTest extends AbstractIntegrationTest {

    private MockHttpSession admin;
    private ClientLogin ana;

    @BeforeEach
    void prepare() throws Exception {
        admin = installAndLoginAdmin();
        ana = createClientAndLogin(admin, "Ana");
        publish("Mercado", "<h4>Mercado local</h4><p>O mercado cresce dez por cento ao ano.</p>", true);
        publish("Riscos", "<p>Perda de alunos para concorrentes.</p>", true);
        long financeiro = publish("Plano Financeiro", "<p>Receita prevista de duzentos mil reais.</p>", true);
        publish("Metas", "<p>Meta secreta de mil alunos.</p>", false);
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + financeiro + "/scope")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}")).andExpect(status().isNoContent());
    }

    private long publish(String tabName, String html, boolean published) throws Exception {
        JsonNode tabs = json.readTree(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long id = -1;
        for (JsonNode t : tabs) {
            if (t.get("name").asText().equals(tabName)) {
                id = t.get("id").asLong();
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("title", tabName);
        body.put("html", html);
        body.put("published", published);
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + id)).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
        return id;
    }

    private static String pdfText(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void clientJsonExportHasOnlyWhatTheClientCanRead() throws Exception {
        MvcResult result = mvc.perform(get("/api/clients/" + ana.id() + "/export.json").session(ana.session()))
                .andExpect(status().isOk()).andReturn();
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment; filename=\"plano-ana-ltda.json\""));
        JsonNode export = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("Ana Ltda", export.get("company").asText());
        assertEquals(2, export.get("tabs").size());
        assertEquals("Mercado", export.get("tabs").get(0).get("name").asText());
        assertTrue(export.get("tabs").get(0).get("html").asText().contains("dez por cento"));
        String all = export.toString();
        assertFalse(all.contains("duzentos") || all.contains("Meta secreta"));
        assertFalse(export.get("tabs").get(0).has("published"), "o cliente não recebe campos de gestão");
    }

    @Test
    void adminJsonExportHasEveryTab() throws Exception {
        JsonNode export = json.readTree(mvc.perform(get("/api/clients/" + ana.id() + "/export.json").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(15, export.get("tabs").size());
        assertTrue(export.toString().contains("duzentos"));
    }

    @Test
    void clientPdfExportContainsTheReadableTextOnly() throws Exception {
        MvcResult result = mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf").session(ana.session()))
                .andExpect(status().isOk()).andReturn();
        assertEquals("application/pdf", result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("plano-ana-ltda.pdf"));
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        String text = pdfText(bytes);
        assertTrue(text.contains("Ana Ltda") && text.contains("Mercado local") && text.contains("dez por cento"), text);
        assertTrue(text.contains("Perda de alunos"));
        assertFalse(text.contains("duzentos") || text.contains("Meta secreta"), "conteúdo bloqueado ou em rascunho fica de fora");
    }

    @Test
    void longContentBreaksAcrossPagesWithoutLosingText() throws Exception {
        StringBuilder html = new StringBuilder("<p>PRIMEIRAPALAVRA ");
        for (int i = 0; i < 1500; i++) {
            html.append("texto").append(i).append(' ');
        }
        html.append("ULTIMAPALAVRA</p><p>").append("x".repeat(400)).append("</p>");
        publish("Concorrentes", html.toString(), true);

        byte[] bytes = mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf").session(ana.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertTrue(doc.getNumberOfPages() >= 2);
        }
        String text = pdfText(bytes);
        assertTrue(text.contains("PRIMEIRAPALAVRA") && text.contains("ULTIMAPALAVRA") && text.contains("texto1499"));
        assertTrue(text.contains("xxxxxxxxxx"), "palavra enorme foi quebrada em vez de estourar a margem");
    }

    @Test
    void charactersThePdfFontCannotDrawDoNotBreakTheExport() throws Exception {
        publish("Concorrentes", "<p>Café com leite → ação ★ rápida 🚀 e “aspas”.</p>", true);
        byte[] bytes = mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf").session(ana.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(bytes);
        assertTrue(text.contains("Café com leite") && text.contains("ação"), text);
    }

    @Test
    void exportIsClosedToOtherClientsAndStrangers() throws Exception {
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        mvc.perform(get("/api/clients/" + ana.id() + "/export.json").session(bia.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf").session(bia.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clients/999999/export.json").session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void exportsAreAudited() throws Exception {
        mvc.perform(get("/api/clients/" + ana.id() + "/export.json").session(ana.session())).andExpect(status().isOk());
        mvc.perform(get("/api/clients/" + ana.id() + "/export.pdf").session(ana.session())).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'EXPORT_JSON'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'EXPORT_PDF'", Integer.class));
    }
}
