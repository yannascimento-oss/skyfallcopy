package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedidos de acesso, promoção, progresso do plano, importação, logo e limite de corpo. */
class AccessRequestsAndDataTest extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    private MockHttpSession admin;

    @BeforeEach
    void prepare() throws Exception {
        admin = installAndLoginAdmin();
    }

    private ResultActions request(String origin, Map<String, String> body) throws Exception {
        return mvc.perform(withCsrf(post("/api/access-requests")).with(r -> { r.setRemoteAddr(origin); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static String origin() {
        return "10." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200);
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void visitorRequestsAccessAndTheConsultancySeesAndClosesIt() throws Exception {
        String origin = origin();
        String email = randomEmail();
        request(origin, Map.of("name", "Carla", "email", email.toUpperCase(), "company", "Padaria Sol", "message", "Quero o plano"))
                .andExpect(status().isAccepted());
        // repetir o mesmo e-mail não duplica o pedido
        request(origin, Map.of("name", "Carla", "email", email, "company", "Padaria Sol")).andExpect(status().isAccepted());
        // o campo-isca preenchido é descartado em silêncio
        request(origin, Map.of("name", "Robô", "email", randomEmail(), "company", "X", "website", "http://spam")).andExpect(status().isAccepted());

        JsonNode open = read(mvc.perform(get("/api/admin/access-requests").session(admin)).andExpect(status().isOk()));
        assertEquals(1, open.size());
        assertEquals(email, open.get(0).get("email").asText(), "e-mail guardado em minúsculas");
        mvc.perform(get("/api/admin/access-requests/count").session(admin)).andExpect(jsonPath("$.open").value(1));

        mvc.perform(withCsrf(post("/api/admin/access-requests/" + open.get(0).get("id").asLong() + "/close?accessCreated=true")).session(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/access-requests/count").session(admin)).andExpect(jsonPath("$.open").value(0));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ACCESS_REQUEST_CLOSED'", Integer.class));
    }

    @Test
    void accessRequestIsValidatedRateLimitedAndHiddenFromClients() throws Exception {
        request(origin(), Map.of("name", "A", "email", "isto-nao-e-email", "company", "B")).andExpect(status().isBadRequest());
        request(origin(), Map.of("name", "", "email", randomEmail(), "company", "B")).andExpect(status().isBadRequest());
        String origin = origin();
        for (int i = 0; i < 5; i++) {
            request(origin, Map.of("name", "A", "email", randomEmail(), "company", "B")).andExpect(status().isAccepted());
        }
        request(origin, Map.of("name", "A", "email", randomEmail(), "company", "B")).andExpect(status().isTooManyRequests());

        ClientLogin client = createClientAndLogin(admin, "Cliente");
        mvc.perform(get("/api/admin/access-requests").session(client.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/access-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void promotedClientBecomesAnAdministrator() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        mvc.perform(withCsrf(post("/api/admin/clients/" + ana.id() + "/promote")).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ana.email()));
        JsonNode admins = read(mvc.perform(get("/api/admin/admins").session(admin)));
        assertTrue(admins.toString().contains(ana.email()));
        assertTrue(!read(mvc.perform(get("/api/admin/clients").session(admin))).toString().contains(ana.email()));
        mvc.perform(withCsrf(post("/api/admin/clients/" + ana.id() + "/promote")).session(admin)).andExpect(status().isNotFound());
        // a sessão aberta antes da promoção continua com o papel antigo até a pessoa entrar de novo
        mvc.perform(withCsrf(post("/api/admin/clients/" + ana.id() + "/promote")).session(ana.session())).andExpect(status().isForbidden());
    }

    @Test
    void progressCountsPublishedOverAllowedStages() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        JsonNode tabs = read(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)));
        long first = tabs.get(0).get("id").asLong();
        long second = tabs.get(1).get("id").asLong();
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + first)).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Resumo", "html", "<p>x</p>", "published", true)))).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + second + "/scope")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}")).andExpect(status().isNoContent());

        mvc.perform(get("/api/clients/" + ana.id() + "/progress").session(ana.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(14)).andExpect(jsonPath("$.available").value(1));
        mvc.perform(get("/api/clients/" + ana.id() + "/progress").session(admin)).andExpect(jsonPath("$.available").value(1));
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        mvc.perform(get("/api/clients/" + ana.id() + "/progress").session(bia.session())).andExpect(status().isForbidden());
    }

    @Test
    void importsAnExportedPlanIntoAnotherClient() throws Exception {
        ClientLogin ana = createClientAndLogin(admin, "Ana");
        ClientLogin bia = createClientAndLogin(admin, "Bia");
        long mercado = 0;
        for (JsonNode t : read(mvc.perform(get("/api/clients/" + ana.id() + "/tabs").session(admin)))) {
            if (t.get("name").asText().equals("Mercado")) {
                mercado = t.get("id").asLong();
            }
        }
        mvc.perform(withCsrf(put("/api/admin/clients/" + ana.id() + "/tabs/" + mercado)).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Mercado", "html", "<p>O mercado cresce 10% ao ano.</p>", "published", true))))
                .andExpect(status().isOk());
        ObjectNode exported = (ObjectNode) read(mvc.perform(get("/api/clients/" + ana.id() + "/export.json").session(admin)).andExpect(status().isOk()));
        ObjectNode extra = json.createObjectNode().put("name", "Parcerias").put("html", "<p>Parceria com nutricionistas.</p>");
        ((com.fasterxml.jackson.databind.node.ArrayNode) exported.get("tabs")).add(extra);

        mvc.perform(withCsrf(post("/api/admin/clients/" + bia.id() + "/tabs/import")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(exported.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        JsonNode biaTabs = read(mvc.perform(get("/api/clients/" + bia.id() + "/tabs").session(admin)));
        assertEquals(16, biaTabs.size(), "15 etapas padrão + 1 nova");
        long biaMercado = 0;
        for (JsonNode t : biaTabs) {
            if (t.get("name").asText().equals("Mercado")) {
                biaMercado = t.get("id").asLong();
                assertEquals(false, t.get("published").asBoolean(), "importar não publica");
            }
        }
        mvc.perform(get("/api/clients/" + bia.id() + "/tabs/" + biaMercado).session(admin))
                .andExpect(jsonPath("$.html").value("<p>O mercado cresce 10% ao ano.</p>"));

        mvc.perform(withCsrf(post("/api/admin/clients/" + bia.id() + "/tabs/import")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tabs\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(post("/api/admin/clients/" + bia.id() + "/tabs/import")).session(bia.session())
                .contentType(MediaType.APPLICATION_JSON).content(exported.toString())).andExpect(status().isForbidden());
    }

    @Test
    void consultancyLogoIsUploadedServedAndRemoved() throws Exception {
        mvc.perform(get("/api/public/logo")).andExpect(status().isNotFound());
        mvc.perform(withCsrf(multipart("/api/admin/settings/logo").file(new MockMultipartFile("file", "logo.txt", "image/png",
                "nao e imagem".getBytes(StandardCharsets.UTF_8)))).session(admin)).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(multipart("/api/admin/settings/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG)))
                .session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/logo")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));
        mvc.perform(get("/api/setup/status")).andExpect(jsonPath("$.hasLogo").value(true));
        mvc.perform(withCsrf(delete("/api/admin/settings/logo")).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/logo")).andExpect(status().isNotFound());

        ClientLogin client = createClientAndLogin(admin, "Cliente");
        mvc.perform(withCsrf(multipart("/api/admin/settings/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG)))
                .session(client.session())).andExpect(status().isForbidden());
    }

    @Test
    void oversizedJsonBodyIsRefusedWith413() throws Exception {
        ClientLogin client = createClientAndLogin(admin, "Cliente");
        String huge = "{\"question\":\"" + "a".repeat(3 * 1024 * 1024) + "\"}";
        mvc.perform(withCsrf(post("/api/chat")).session(client.session()).contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("grande demais")));
    }

    @Test
    void bodyWithoutDeclaredLengthIsRefused() throws Exception {
        ClientLogin client = createClientAndLogin(admin, "Cliente");
        mvc.perform(withCsrf(post("/api/chat")).session(client.session()).contentType(MediaType.APPLICATION_JSON)
                        .header("Transfer-Encoding", "chunked"))
                .andExpect(status().isLengthRequired());
    }
}
