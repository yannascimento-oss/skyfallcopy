package org.empresajr.chatjr.web.dto;

import java.time.Instant;
import java.util.List;

/** Dados da tela Sistema: saúde da instalação, uso de disco, custo estimado da IA e erros recentes. */
public final class SystemDto {

    private SystemDto() {
    }

    public record Disk(long attachmentsBytes, long freeBytes, long totalBytes) {
    }

    public record Counts(long clients, long admins, long tabs, long conversations, long questions) {
    }

    public record AiUsage(boolean keyConfigured, String model, long calls30d, long errors30d, long inputTokens30d,
                          long outputTokens30d, long estCostMicroUsd30d) {
    }

    public record ErrorItem(Instant at, String message, String path) {
    }

    public record CallItem(Instant at, String kind, String status, Integer httpStatus, Integer durationMs,
                           Integer inputTokens, Integer outputTokens, String model, String error,
                           Long estCostMicroUsd) {
    }

    public record Overview(String version, String javaVersion, String database, long uptimeSeconds, Disk disk,
                           Counts counts, AiUsage ai, boolean emailConfigured, List<ErrorItem> recentErrors,
                           List<CallItem> recentCalls) {
    }

    public record EmailTest(boolean ok, String message) {
    }
}
