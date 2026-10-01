package org.empresajr.chatjr.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indicadores calculados sobre as perguntas realmente feitas (nada de números inventados). */
public final class IndicatorCalculator {

    public record Entry(String question, QueryType type, boolean answered, Long sourceTabId, String theme,
                        boolean degraded, Instant createdAt) {
    }

    public record Count(String label, int count) {
    }

    public record DayCount(LocalDate date, int count) {
    }

    public record Unanswered(String question, Instant createdAt) {
    }

    public record Result(int total, int answered, int unanswered, int answeredPercent, int degraded,
                         Map<String, Integer> byType, List<Count> topSources, List<Count> topThemes,
                         List<Unanswered> unansweredQuestions, List<DayCount> perDay, Instant lastQuestionAt,
                         int windowDays) {
    }

    private static final int TOP = 8;
    private static final int UNANSWERED_LIMIT = 20;
    private static final int CHART_DAYS = 14;

    private IndicatorCalculator() {
    }

    public static Result compute(List<Entry> entries, Map<Long, String> tabNames, Instant now, int windowDays) {
        int answered = 0;
        int degraded = 0;
        Instant last = null;
        Map<QueryType, Integer> byType = new EnumMap<>(QueryType.class);
        for (QueryType type : QueryType.values()) {
            byType.put(type, 0);
        }
        Map<String, Integer> sources = new HashMap<>();
        Map<String, Integer> themes = new HashMap<>();
        List<Unanswered> unanswered = new ArrayList<>();

        for (Entry e : entries) {
            byType.merge(e.type(), 1, Integer::sum);
            if (e.degraded()) {
                degraded++;
            }
            if (last == null || e.createdAt().isAfter(last)) {
                last = e.createdAt();
            }
            if (e.answered()) {
                answered++;
                if (e.sourceTabId() != null) {
                    sources.merge(tabNames.getOrDefault(e.sourceTabId(), "(etapa removida)"), 1, Integer::sum);
                }
                if (e.theme() != null && !e.theme().isBlank()) {
                    themes.merge(e.theme().trim(), 1, Integer::sum);
                }
            } else {
                unanswered.add(new Unanswered(e.question(), e.createdAt()));
            }
        }
        unanswered.sort(Comparator.comparing(Unanswered::createdAt).reversed());

        int total = entries.size();
        Map<String, Integer> byTypeNames = new LinkedHashMap<>();
        byType.forEach((type, n) -> byTypeNames.put(type.name(), n));

        return new Result(total, answered, total - answered, total == 0 ? 0 : Math.round(answered * 100f / total),
                degraded, byTypeNames, top(sources), top(themes),
                unanswered.size() <= UNANSWERED_LIMIT ? unanswered : unanswered.subList(0, UNANSWERED_LIMIT),
                perDay(entries, now, Math.min(windowDays, CHART_DAYS)), last, windowDays);
    }

    private static List<Count> top(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP).map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    private static List<DayCount> perDay(List<Entry> entries, Instant now, int days) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Entry e : entries) {
            counts.merge(e.createdAt().atZone(ZoneOffset.UTC).toLocalDate(), 1, Integer::sum);
        }
        List<DayCount> series = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            series.add(new DayCount(day, counts.getOrDefault(day, 0)));
        }
        return series;
    }
}
