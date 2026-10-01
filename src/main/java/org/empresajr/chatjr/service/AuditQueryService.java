package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AuditLog;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.repository.AuditLogRepository;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.web.dto.AuditPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Consulta do histórico de auditoria, do mais recente para o mais antigo. */
@Service
public class AuditQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository audit;
    private final ClientAccountRepository accounts;

    public AuditQueryService(AuditLogRepository audit, ClientAccountRepository accounts) {
        this.audit = audit;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AuditPage search(Long clientId, String action, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)));
        String act = action == null || action.isBlank() ? null : action.trim();
        Page<AuditLog> result;
        if (clientId != null && act != null) {
            result = audit.findByClientIdAndActionOrderByCreatedAtDescIdDesc(clientId, act, pageable);
        } else if (clientId != null) {
            result = audit.findByClientIdOrderByCreatedAtDescIdDesc(clientId, pageable);
        } else if (act != null) {
            result = audit.findByActionOrderByCreatedAtDescIdDesc(act, pageable);
        } else {
            result = audit.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        Set<Long> ids = result.getContent().stream().map(AuditLog::getClientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (ClientAccount a : accounts.findAllById(ids)) {
            names.put(a.getId(), a.getCompany() != null ? a.getCompany() : a.getName());
        }
        List<AuditPage.Item> items = result.getContent().stream()
                .map(a -> AuditPage.Item.of(a, a.getClientId() == null ? null : names.get(a.getClientId()))).toList();
        return new AuditPage(items, result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
    }
}
