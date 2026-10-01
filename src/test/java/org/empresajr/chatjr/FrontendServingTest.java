package org.empresajr.chatjr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O servidor de verdade entrega a interface, sem exigir login, com os tipos e os cabeçalhos certos. */
class FrontendServingTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void servesTheLandingPageWithoutLogin() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML));
        assertTrue(response.getBody().contains("id=\"auth-card\""));
        assertTrue(response.getBody().contains("js/main.js"));
    }

    @Test
    void inviteLinkLandsOnTheSamePage() {
        ResponseEntity<String> response = rest.getForEntity("/?convite=qualquer-token", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("id=\"auth-card\""));
    }

    @Test
    void servesModulesStylesFontsAndLogo() {
        for (String path : new String[] {"/js/main.js", "/js/api.js", "/js/ui.js", "/js/views/auth.js", "/js/views/chat.js",
                "/js/views/admin-content.js", "/css/styles.css"}) {
            ResponseEntity<String> r = rest.getForEntity(path, String.class);
            assertEquals(200, r.getStatusCode().value(), path);
            assertTrue(r.getBody() != null && !r.getBody().isBlank(), path);
        }
        MediaType js = rest.getForEntity("/js/main.js", String.class).getHeaders().getContentType();
        assertTrue(js.getSubtype().contains("javascript"), "os módulos precisam sair como JavaScript, senão o navegador recusa: " + js);
        assertTrue(rest.getForEntity("/css/styles.css", String.class).getBody().contains("@font-face"));

        ResponseEntity<byte[]> font = rest.getForEntity("/fonts/roboto-latin-400-normal.woff2", byte[].class);
        assertEquals(200, font.getStatusCode().value());
        assertTrue(font.getBody().length > 1000);
        ResponseEntity<byte[]> logo = rest.getForEntity("/img/logo-empresa-jr.png", byte[].class);
        assertEquals(200, logo.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, logo.getHeaders().getContentType());
    }

    @Test
    void staticPagesCarryTheStrictSecurityHeaders() {
        HttpHeaders headers = rest.getForEntity("/", String.class).getHeaders();
        String csp = headers.getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("script-src 'self';"), csp);
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    void unknownApiPathsStayJsonAndDoNotFallBackToThePage() {
        ResponseEntity<String> response = rest.getForEntity("/api/nao-existe", String.class);
        assertTrue(response.getStatusCode().is4xxClientError());
        assertTrue(response.getHeaders().getContentType() == null || !response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML));
    }
}
