package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: PostgreSQL real (o mesmo motor da produção) e a aplicação inteira no ar.
 * O contêiner é compartilhado por todas as classes de teste; as tabelas são limpas antes de cada teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Segredos de teste são gerados na hora: nenhuma credencial fica escrita no código.
        registry.add("chatjr.secret", () -> UUID.randomUUID() + "-" + UUID.randomUUID());
        registry.add("chatjr.ai.base-url", () -> "http://127.0.0.1:" + AI_STUB.getAddress().getPort());
        registry.add("chatjr.data-dir", () -> DATA_DIR);
    }

    // ---------- servidor de mentira da API da Anthropic ----------

    /** Resposta programada do servidor de mentira. */
    protected record StubReply(int status, String body, long delayMs) {
    }

    static final HttpServer AI_STUB;
    protected static final AtomicReference<StubReply> AI_REPLY = new AtomicReference<>();
    /** Cada chamada recebida: key, version, path e body. */
    protected static final List<Map<String, String>> AI_CALLS = new CopyOnWriteArrayList<>();

    static {
        try {
            AI_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AI_STUB.createContext("/v1/messages", exchange -> {
                byte[] request = exchange.getRequestBody().readAllBytes();
                Map<String, String> call = new HashMap<>();
                call.put("key", exchange.getRequestHeaders().getFirst("x-api-key"));
                call.put("version", exchange.getRequestHeaders().getFirst("anthropic-version"));
                call.put("path", exchange.getRequestURI().getPath());
                call.put("body", new String(request, StandardCharsets.UTF_8));
                AI_CALLS.add(call);
                StubReply reply = AI_REPLY.get();
                try {
                    if (reply.delayMs() > 0) {
                        Thread.sleep(reply.delayMs());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            AI_STUB.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Envelope no formato da API de mensagens, com o texto da resposta e o uso de tokens. */
    protected static String aiEnvelope(String text, int inputTokens, int outputTokens) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            var root = mapper.createObjectNode();
            root.put("id", "msg_teste");
            root.put("type", "message");
            root.put("role", "assistant");
            root.put("model", "modelo-de-teste");
            root.putArray("content").addObject().put("type", "text").put("text", text);
            root.put("stop_reason", "end_turn");
            root.putObject("usage").put("input_tokens", inputTokens).put("output_tokens", outputTokens);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Conteúdo que uma IA bem-comportada devolveria para a etapa Mercado. */
    protected static String goodAiContent() {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "title", "Mercado",
                    "html", "<p>O mercado cresce dez por cento ao ano.</p><script>alert(1)</script>",
                    "shortDescription", "Mercado em crescimento.",
                    "whatIsIt", "Análise do mercado em que a empresa atua.",
                    "objective", "Mostrar o tamanho do mercado.",
                    "keyPoints", List.of("Crescimento de 10% ao ano", "Mercado fragmentado"),
                    "suggestedQuestions", List.of("Quanto o mercado cresce?"),
                    "sections", List.of("Tamanho do mercado", "Crescimento")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void resetAiStub() {
        AI_CALLS.clear();
        AI_REPLY.set(new StubReply(200, aiEnvelope(goodAiContent(), 120, 80), 0));
    }

    /** Pasta de dados dos testes: fixa durante a execução, para o teste e o servidor olharem o mesmo lugar. */
    static final String DATA_DIR = System.getProperty("java.io.tmpdir") + "/chatjr-test-" + UUID.randomUUID();

    protected static final String CSRF_TOKEN = "token-csrf-de-teste";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE access_request, app_error, ai_call_log, audit_log, app_setting, query_log, chat_message, "
                + "conversation, tab_scope, plan_version, tab_chunk, attachment, plan_tab, client_account "
                + "RESTART IDENTITY CASCADE");
    }

    /** Simula o que o navegador faz: cookie XSRF-TOKEN e o mesmo valor no cabeçalho X-XSRF-TOKEN. */
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN)).header("X-XSRF-TOKEN", CSRF_TOKEN);
    }

    protected static String randomPassword() {
        return "Aa1-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    protected static String randomEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.test";
    }

    protected void install(String adminEmail, String password) throws Exception {
        installWithAiKey(adminEmail, password, null);
    }

    protected void installWithAiKey(String adminEmail, String password, String aiKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("orgName", "Empresa JR (teste)");
        body.put("adminName", "Admin de Teste");
        body.put("adminEmail", adminEmail);
        body.put("password", password);
        if (aiKey != null) {
            body.put("aiKey", aiKey);
        }
        mvc.perform(withCsrf(post("/api/setup")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated());
    }

    protected ResultActions tryLogin(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected MockHttpSession loginOk(String email, String password) throws Exception {
        MvcResult result = tryLogin(email, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Cria um cliente como administrador e devolve a resposta (inclui o token do convite). */
    protected JsonNode createClient(MockHttpSession admin, String name, String email, String company) throws Exception {
        String body = json.writeValueAsString(Map.of("name", name, "email", email, "company", company));
        MvcResult result = mvc.perform(withCsrf(post("/api/admin/clients")).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected void acceptInvite(String token, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("token", token, "password", password));
        mvc.perform(withCsrf(post("/api/auth/accept-invite")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    protected String messageOf(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content).path("message").asText();
    }

    protected record ClientLogin(MockHttpSession session, long id, String email) {
    }

    /** Cria um cliente, aceita o convite e entra como ele. */
    protected ClientLogin createClientAndLogin(MockHttpSession admin, String name) throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        JsonNode created = createClient(admin, name, email, name + " Ltda");
        acceptInvite(created.get("inviteToken").asText(), password);
        return new ClientLogin(loginOk(email, password), created.get("client").get("id").asLong(), email);
    }

    /** E-mail e senha do administrador criado por {@link #installAndLoginAdmin()}. */
    protected String adminEmail;
    protected String adminPassword;

    protected MockHttpSession installAndLoginAdmin() throws Exception {
        adminEmail = randomEmail();
        adminPassword = randomPassword();
        install(adminEmail, adminPassword);
        return loginOk(adminEmail, adminPassword);
    }

    /** PDF de teste com uma linha de texto por item. */
    protected static byte[] buildPdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
