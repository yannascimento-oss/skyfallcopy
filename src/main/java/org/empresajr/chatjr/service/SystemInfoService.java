package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.Role;
import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.repository.AppErrorRepository;
import org.empresajr.chatjr.repository.AttachmentRepository;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.repository.ConversationRepository;
import org.empresajr.chatjr.repository.PlanTabRepository;
import org.empresajr.chatjr.repository.QueryLogRepository;
import org.empresajr.chatjr.web.dto.SystemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Fotografia da instalação para a tela Sistema. */
@Service
public class SystemInfoService {

    public static final String EMAIL_NOT_CONFIGURED = "O envio de e-mail não está configurado nesta instalação. "
            + "Os convites são entregues por link, que a consultoria copia e envia ao cliente.";

    private final ClientAccountRepository accounts;
    private final PlanTabRepository tabs;
    private final ConversationRepository conversations;
    private final QueryLogRepository queries;
    private final AttachmentRepository attachments;
    private final AiCallLogRepository callLogs;
    private final AppErrorRepository errors;
    private final SettingsService settings;
    private final DataSource dataSource;
    private final Clock clock;
    private final Path dataDir;
    private final String version;

    public SystemInfoService(ClientAccountRepository accounts, PlanTabRepository tabs,
                             ConversationRepository conversations, QueryLogRepository queries,
                             AttachmentRepository attachments, AiCallLogRepository callLogs,
                             AppErrorRepository errors, SettingsService settings, DataSource dataSource, Clock clock,
                             @Value("${chatjr.data-dir:./data}") String dataDir,
                             @Value("${chatjr.version:1.0.0}") String version) {
        this.accounts = accounts;
        this.tabs = tabs;
        this.conversations = conversations;
        this.queries = queries;
        this.attachments = attachments;
        this.callLogs = callLogs;
        this.errors = errors;
        this.settings = settings;
        this.dataSource = dataSource;
        this.clock = clock;
        this.dataDir = Path.of(dataDir);
        this.version = version;
    }

    @Transactional(readOnly = true)
    public SystemDto.Overview overview() {
        Instant since = clock.instant().minus(Duration.ofDays(30));
        return new SystemDto.Overview(version, System.getProperty("java.version"), databaseName(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000, disk(),
                new SystemDto.Counts(accounts.countByRole(Role.CLIENT), accounts.countByRole(Role.ADMIN), tabs.count(),
                        conversations.count(), queries.count()),
                new SystemDto.AiUsage(settings.aiKey().isPresent(), settings.aiModel(),
                        callLogs.countByCreatedAtAfter(since), callLogs.countByStatusAndCreatedAtAfter("ERROR", since),
                        callLogs.inputTokensSince(since), callLogs.outputTokensSince(since), callLogs.costSince(since)),
                false,
                errors.findTop20ByOrderByIdDesc().stream()
                        .map(e -> new SystemDto.ErrorItem(e.getCreatedAt(), e.getMessage(), e.getPath())).toList(),
                callLogs.findTop20ByOrderByIdDesc().stream()
                        .map(c -> new SystemDto.CallItem(c.getCreatedAt(), c.getKind(), c.getStatus(), c.getHttpStatus(),
                                c.getDurationMs(), c.getInputTokens(), c.getOutputTokens(), c.getModel(),
                                c.getErrorMessage())).toList());
    }

    public SystemDto.EmailTest testEmail() {
        return new SystemDto.EmailTest(false, EMAIL_NOT_CONFIGURED);
    }

    private SystemDto.Disk disk() {
        long free = 0;
        long total = 0;
        try {
            Files.createDirectories(dataDir);
            var store = Files.getFileStore(dataDir);
            free = store.getUsableSpace();
            total = store.getTotalSpace();
        } catch (IOException ignored) {
            // Sem acesso à pasta de dados, os números ficam em zero.
        }
        return new SystemDto.Disk(attachments.totalStoredBytes(), free, total);
    }

    private String databaseName() {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            return "indisponível";
        }
    }
}
