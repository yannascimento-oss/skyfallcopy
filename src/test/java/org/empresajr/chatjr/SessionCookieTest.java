package org.empresajr.chatjr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confere os atributos dos cookies numa chamada HTTP de verdade (o MockMvc não passa pelo Tomcat). */
class SessionCookieTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders csrfHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + CSRF_TOKEN);
        headers.add("X-XSRF-TOKEN", CSRF_TOKEN);
        return headers;
    }

    private static String cookie(List<String> setCookies, String name) {
        assertNotNull(setCookies, "a resposta deveria definir cookies");
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("cookie " + name + " não foi enviado: " + setCookies));
    }

    @Test
    void sessionCookie_isHttpOnlySecureAndSameSiteStrict() throws Exception {
        String email = randomEmail();
        String password = randomPassword();
        Map<String, String> setup = Map.of("orgName", "Org", "adminName", "Adm", "adminEmail", email, "password", password);
        ResponseEntity<String> installed = rest.exchange("/api/setup", HttpMethod.POST,
                new HttpEntity<>(json.writeValueAsString(setup), csrfHeaders()), String.class);
        assertEquals(201, installed.getStatusCode().value());

        Map<String, String> login = Map.of("email", email, "password", password);
        ResponseEntity<String> response = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(json.writeValueAsString(login), csrfHeaders()), String.class);
        assertEquals(200, response.getStatusCode().value());

        String session = cookie(response.getHeaders().get(HttpHeaders.SET_COOKIE), "JSESSIONID");
        assertTrue(session.contains("HttpOnly"), session);
        assertTrue(session.contains("Secure"), session);
        assertTrue(session.contains("SameSite=Strict"), session);
    }

    @Test
    void csrfCookie_isReadableByScriptButSecureAndSameSiteStrict() {
        ResponseEntity<String> response = rest.getForEntity("/api/auth/csrf", String.class);
        assertEquals(204, response.getStatusCode().value());

        String csrf = cookie(response.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        assertFalse(csrf.contains("HttpOnly"), "o JavaScript precisa ler este cookie: " + csrf);
        assertTrue(csrf.contains("Secure"), csrf);
        assertTrue(csrf.contains("SameSite=Strict"), csrf);
    }

    @Test
    void responses_carrySecurityHeaders() {
        ResponseEntity<String> response = rest.getForEntity("/api/setup/status", String.class);
        HttpHeaders headers = response.getHeaders();
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        String csp = headers.getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        String scriptSrc = java.util.Arrays.stream(csp.split(";")).map(String::trim)
                .filter(d -> d.startsWith("script-src")).findFirst().orElseThrow();
        assertEquals("script-src 'self'", scriptSrc, "scripts só do próprio site, sem unsafe-inline nem unsafe-eval");
    }

    @Test
    void healthEndpoint_isPublicAndHidesDetails() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("UP"));
        assertFalse(response.getBody().contains("jdbc"), "o health público não deve expor detalhes");
    }
}
