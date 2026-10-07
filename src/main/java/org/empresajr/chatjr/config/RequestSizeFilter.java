package org.empresajr.chatjr.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.empresajr.chatjr.web.JsonErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limite explícito de corpo para a API (defeito 8). Envio de arquivo tem o próprio limite (25 MB, no multipart);
 * importação de plano aceita até 8 MB; o resto da API, até 2 MB. Passou disso: 413 com mensagem legível.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    static final long DEFAULT_LIMIT = 2L * 1024 * 1024;
    static final long IMPORT_LIMIT = 8L * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return !request.getRequestURI().startsWith("/api/") || (type != null && type.startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = request.getRequestURI().endsWith("/import") ? IMPORT_LIMIT : DEFAULT_LIMIT;
        if (request.getContentLengthLong() > limit) {
            JsonErrors.write(response, 413, "O conteúdo enviado é grande demais (limite de " + (limit / 1024 / 1024) + " MB).");
            return;
        }
        chain.doFilter(request, response);
    }
}
