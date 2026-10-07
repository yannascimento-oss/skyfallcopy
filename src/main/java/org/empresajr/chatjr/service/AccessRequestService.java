package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccessRequest;
import org.empresajr.chatjr.repository.AccessRequestRepository;
import org.empresajr.chatjr.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pedidos de acesso da landing. O formulário é público, então tem freios: no máximo 5 pedidos por hora por endereço
 * de origem, 500 pedidos em aberto no total e um pedido em aberto por e-mail. Um campo-isca escondido descarta robôs
 * sem avisar (a resposta é a mesma de sucesso).
 */
@Service
public class AccessRequestService {

    static final int PER_HOUR_PER_ORIGIN = 5;
    static final int MAX_OPEN = 500;

    private final AccessRequestRepository requests;
    private final AuditService audit;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recentByOrigin = new ConcurrentHashMap<>();

    public AccessRequestService(AccessRequestRepository requests, AuditService audit, Clock clock) {
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void submit(String name, String email, String company, String phone, String message, String honeypot, String origin) {
        if (honeypot != null && !honeypot.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        Deque<Instant> recent = recentByOrigin.computeIfAbsent(origin == null ? "?" : origin, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                recent.pollFirst();
            }
            if (recent.size() >= PER_HOUR_PER_ORIGIN) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Muitos pedidos enviados. Tente de novo mais tarde.");
            }
            recent.addLast(now);
        }
        if (recentByOrigin.size() > 10_000) {
            recentByOrigin.clear();
        }
        String normalized = AccountService.normalizeEmail(email);
        if (requests.existsByEmailAndHandledAtIsNull(normalized)) {
            return; // já existe um pedido em aberto para esse e-mail; não duplica nem revela
        }
        if (requests.countByHandledAtIsNull() >= MAX_OPEN) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível registrar o pedido agora. Fale com a consultoria por e-mail.");
        }
        requests.save(new AccessRequest(name.trim(), normalized, company.trim(), blank(phone), blank(message), now));
    }

    @Transactional(readOnly = true)
    public List<AccessRequest> listOpen() {
        return requests.findByHandledAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return requests.countByHandledAtIsNull();
    }

    @Transactional
    public void close(Long id, String actorEmail, boolean accessCreated) {
        AccessRequest request = requests.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!request.isOpen()) {
            return;
        }
        request.markHandled(actorEmail, clock.instant());
        requests.save(request);
        audit.record("ACCESS_REQUEST_CLOSED", null, null, null,
                request.getEmail() + (accessCreated ? " (acesso criado)" : " (descartado)"));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
