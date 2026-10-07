package org.empresajr.chatjr.config;

import org.empresajr.chatjr.web.JsonErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            // Só scripts do próprio site: a interface não usa onclick nem <script> em linha (há teste de guarda para isso).
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * A autenticação é feita pelo AuthService. Este bean existe só para impedir que o Spring Boot crie o
     * usuário padrão com senha aleatória no log.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Autenticação feita pelo AuthService.");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(secureCookies));

        http
            .securityContext(context -> context.securityContextRepository(contextRepository))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/setup/**", "/api/auth/csrf", "/api/auth/login",
                            "/api/auth/logout", "/api/auth/accept-invite", "/api/access-requests", "/api/public/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/chat/**").hasRole("CLIENT")
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint((request, response, ex) -> JsonErrors.write(response, 401,
                            "Sua sessão expirou ou não foi iniciada. Entre novamente."))
                    .accessDeniedHandler((request, response, ex) -> JsonErrors.write(response, 403,
                            ex instanceof CsrfException
                                    ? "Sessão de segurança inválida. Recarregue a página e tente de novo."
                                    : "Você não tem permissão para esta ação.")))
            .headers(headers -> headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    // Cache decidido por tipo de conteúdo: a API fica no-store (ApiNoStoreFilter) e os arquivos
                    // estáticos são revalidados (spring.web.resources.cache), em vez de baixados de novo a cada visita.
                    .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.disable());
        return http.build();
    }
}
