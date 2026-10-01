package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        registry.add("chatjr.data-dir", () -> System.getProperty("java.io.tmpdir") + "/chatjr-test-" + UUID.randomUUID());
    }

    protected static final String CSRF_TOKEN = "token-csrf-de-teste";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE app_error, ai_call_log, audit_log, app_setting, query_log, chat_message, "
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

    protected MockHttpSession installAndLoginAdmin() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        install(email, password);
        return loginOk(email, password);
    }
}
