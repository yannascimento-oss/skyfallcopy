package org.empresajr.chatjr;

import com.fasterxml.jackson.databind.JsonNode;
import org.empresajr.chatjr.service.DemoDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("demo")
class DemoDataLoaderTest extends AbstractIntegrationTest {

    static final String DEMO_PASSWORD = "Demo-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "1";

    @DynamicPropertySource
    static void demoProperties(DynamicPropertyRegistry registry) {
        registry.add("chatjr.demo.password", () -> DEMO_PASSWORD);
    }

    @Autowired
    private DemoDataLoader loader;

    @Test
    void createsAdminAndClientsWithPublishedContentOnlyOnAnEmptyInstallation() throws Exception {
        // O carregador já rodou na subida; a base de teste foi limpa depois, então a instalação está vazia.
        assertTrue(loader.load());

        MockHttpSession admin = loginOk("admin@demo.chatjr.test", DEMO_PASSWORD);
        mvc.perform(get("/api/admin/clients").session(admin)).andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM client_account WHERE role = 'CLIENT'", Integer.class));

        MockHttpSession marina = loginOk("marina@norte-fit.demo.chatjr.test", DEMO_PASSWORD);
        long id = json.readTree(mvc.perform(get("/api/me").session(marina)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        JsonNode tabs = json.readTree(mvc.perform(get("/api/clients/" + id + "/tabs").session(marina)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(5, tabs.size(), "só as etapas com conteúdo de demonstração aparecem para o cliente");

        // Segunda chamada não mexe em nada.
        assertFalse(loader.load());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM client_account WHERE role = 'CLIENT'", Integer.class));
    }

    @Test
    void demoPasswordIsNotStoredInPlainText() {
        loader.load();
        String hashes = jdbc.queryForList("SELECT password_hash FROM client_account", String.class).toString();
        assertFalse(hashes.contains(DEMO_PASSWORD));
        assertTrue(hashes.contains("$2"), "senha guardada como BCrypt");
    }
}
