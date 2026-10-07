package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.IndicatorCalculator;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.QueryLog;
import org.empresajr.chatjr.repository.PlanTabRepository;
import org.empresajr.chatjr.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Indicadores de uso do chat, calculados sobre as perguntas registradas. */
@Service
public class IndicatorService {

    private final QueryLogRepository queryLogs;
    private final PlanTabRepository tabs;
    private final PlanService plans;
    private final Clock clock;

    public IndicatorService(QueryLogRepository queryLogs, PlanTabRepository tabs, PlanService plans, Clock clock) {
        this.queryLogs = queryLogs;
        this.tabs = tabs;
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IndicatorCalculator.Result forClient(Long clientId, int days) {
        plans.adminClient(clientId);
        int window = clamp(days);
        Instant now = clock.instant();
        return compute(queryLogs.findByClientIdAndCreatedAtAfter(clientId, now.minus(Duration.ofDays(window))), now, window);
    }

    @Transactional(readOnly = true)
    public IndicatorCalculator.Result overall(int days) {
        int window = clamp(days);
        Instant now = clock.instant();
        return compute(queryLogs.findByCreatedAtAfter(now.minus(Duration.ofDays(window))), now, window);
    }

    private IndicatorCalculator.Result compute(List<QueryLog> logs, Instant now, int window) {
        Set<Long> tabIds = logs.stream().map(QueryLog::getSourceTabId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (PlanTab tab : tabs.findAllById(tabIds)) {
            names.put(tab.getId(), tab.getName());
        }
        List<IndicatorCalculator.Entry> entries = logs.stream()
                .map(q -> new IndicatorCalculator.Entry(q.getQuestion(), q.getQueryType(), q.isAnswered(),
                        q.getSourceTabId(), q.getTheme(), q.isDegraded(), q.getCreatedAt()))
                .toList();
        return IndicatorCalculator.compute(entries, names, now, window);
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(90, days));
    }
}
