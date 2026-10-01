package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.DefaultStages;
import org.empresajr.chatjr.domain.GeneratedContent;
import org.empresajr.chatjr.domain.HtmlSanitizer;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.PlanVersion;
import org.empresajr.chatjr.domain.Role;
import org.empresajr.chatjr.domain.Slugs;
import org.empresajr.chatjr.domain.TabScope;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.repository.PlanTabRepository;
import org.empresajr.chatjr.repository.PlanVersionRepository;
import org.empresajr.chatjr.repository.TabScopeRepository;
import org.empresajr.chatjr.web.ApiException;
import org.empresajr.chatjr.web.dto.TabView;
import org.empresajr.chatjr.web.dto.UpdateTabRequest;
import org.empresajr.chatjr.web.dto.VersionView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras do plano: quem enxerga cada aba. O cliente só vê as abas do próprio plano que estejam publicadas
 * e liberadas; a consultoria vê tudo. Toda checagem acontece aqui, no servidor.
 */
@Service
public class PlanService {

    private static final String NO_ACCESS = "Você não tem acesso a este plano.";
    private static final String TAB_NOT_FOUND = "Etapa não encontrada.";

    private final PlanTabRepository tabs;
    private final TabScopeRepository scopes;
    private final PlanVersionRepository versions;
    private final ClientAccountRepository accounts;
    private final AuditService audit;
    private final Clock clock;

    public PlanService(PlanTabRepository tabs, TabScopeRepository scopes, PlanVersionRepository versions,
                       ClientAccountRepository accounts, AuditService audit, Clock clock) {
        this.tabs = tabs;
        this.scopes = scopes;
        this.versions = versions;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Cria as etapas padrão de um cliente novo. Nascem como rascunho: o cliente só vê o que a consultoria publicar. */
    @Transactional
    public void createDefaultTabs(ClientAccount client) {
        Instant now = clock.instant();
        int order = 0;
        for (DefaultStages.Stage stage : DefaultStages.ALL) {
            PlanTab tab = new PlanTab(client.getId(), Slugs.slugify(stage.name()), stage.name(), order++, now);
            tab.applyDefinition(stage.shortDescription(), stage.whatIsIt(), stage.objective());
            tabs.save(tab);
        }
    }

    // ---------- leitura (cliente e consultoria) ----------

    @Transactional(readOnly = true)
    public List<TabView> listTabs(AccountPrincipal who, Long clientId) {
        checkAccess(who, clientId);
        List<PlanTab> all = tabs.findByClientIdOrderBySortOrderAscIdAsc(clientId);
        Set<Long> blocked = blockedTabIds(clientId);
        if (who.isAdmin()) {
            requireClient(clientId);
            return all.stream()
                    .map(t -> TabView.of(t, false, t.isPublished(), !blocked.contains(t.getId())))
                    .toList();
        }
        return all.stream()
                .filter(t -> isVisibleToClient(t, blocked))
                .map(t -> TabView.of(t, false, null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public TabView getTab(AccountPrincipal who, Long clientId, Long tabId) {
        checkAccess(who, clientId);
        PlanTab tab = requireTab(clientId, tabId);
        Set<Long> blocked = blockedTabIds(clientId);
        if (who.isAdmin()) {
            return TabView.of(tab, true, tab.isPublished(), !blocked.contains(tab.getId()));
        }
        if (!isVisibleToClient(tab, blocked)) {
            throw new ApiException(HttpStatus.NOT_FOUND, TAB_NOT_FOUND);
        }
        return TabView.of(tab, true, null, null);
    }

    /** Abas que o cliente pode ler. É a única fonte do que a IA do chat pode usar para responder. */
    @Transactional(readOnly = true)
    public List<PlanTab> visibleTabs(Long clientId) {
        Set<Long> blocked = blockedTabIds(clientId);
        return tabs.findByClientIdOrderBySortOrderAscIdAsc(clientId).stream()
                .filter(t -> isVisibleToClient(t, blocked)).toList();
    }

    // ---------- gestão (só consultoria) ----------

    @Transactional
    public TabView createTab(Long clientId, String name) {
        requireClient(clientId);
        String base = Slugs.slugify(name);
        String slug = base;
        for (int n = 2; tabs.existsByClientIdAndSlug(clientId, slug); n++) {
            slug = base + "-" + n;
        }
        PlanTab tab = tabs.save(new PlanTab(clientId, slug, name.trim(), (int) tabs.countByClientId(clientId), clock.instant()));
        audit.record("TAB_CREATED", clientId, tab.getId(), tab.getName(), null);
        return TabView.of(tab, true, tab.isPublished(), true);
    }

    @Transactional
    public TabView updateTab(Long clientId, Long tabId, UpdateTabRequest request) {
        ClientAccount client = requireClient(clientId);
        PlanTab tab = requireTab(clientId, tabId);
        Instant now = clock.instant();

        String title = request.title().trim();
        String html = HtmlSanitizer.sanitize(request.html());
        String shortDescription = blankToNull(request.shortDescription());
        String whatIsIt = blankToNull(request.whatIsIt());
        String objective = blankToNull(request.objective());
        String keyPoints = PlanTab.join(request.keyPoints());
        String questions = PlanTab.join(request.suggestedQuestions());
        String source = blankToNull(request.source());

        if (!tab.hasSameContent(title, html, shortDescription, whatIsIt, objective, keyPoints, questions, source)) {
            versions.save(PlanVersion.snapshotOf(tab, "EDIT", actorEmail(), now));
            tab.applyContent(title, html, shortDescription, whatIsIt, objective, keyPoints, questions, source, now);
            touchPlan(client, now);
            audit.record("TAB_UPDATED", clientId, tab.getId(), tab.getName(), null);
        }
        if (request.published() != null && request.published() != tab.isPublished()) {
            tab.setPublished(request.published());
            audit.record(request.published() ? "TAB_PUBLISHED" : "TAB_UNPUBLISHED", clientId, tab.getId(), tab.getName(), null);
        }
        tabs.save(tab);
        return TabView.of(tab, true, tab.isPublished(), !blockedTabIds(clientId).contains(tab.getId()));
    }

    @Transactional
    public void deleteTab(Long clientId, Long tabId) {
        requireClient(clientId);
        PlanTab tab = requireTab(clientId, tabId);
        tabs.delete(tab);
        audit.record("TAB_DELETED", clientId, tab.getId(), tab.getName(), null);
    }

    @Transactional
    public void setScope(Long clientId, Long tabId, boolean allowed) {
        requireClient(clientId);
        PlanTab tab = requireTab(clientId, tabId);
        var existing = scopes.findByClientIdAndTabId(clientId, tabId);
        if (allowed) {
            existing.ifPresent(scopes::delete);
        } else if (existing.isEmpty()) {
            scopes.save(new TabScope(clientId, tabId, false));
        }
        audit.record("SCOPE_CHANGED", clientId, tab.getId(), tab.getName(), allowed ? "liberada" : "bloqueada");
    }

    @Transactional(readOnly = true)
    public List<VersionView> listVersions(Long clientId, Long tabId) {
        requireClient(clientId);
        requireTab(clientId, tabId);
        return versions.findByTabIdOrderByVersionDesc(tabId).stream().map(VersionView::of).toList();
    }

    /** Volta a aba a uma versão anterior. O estado atual também é guardado, então dá para desfazer a restauração. */
    @Transactional
    public TabView restoreVersion(Long clientId, Long tabId, int version) {
        ClientAccount client = requireClient(clientId);
        PlanTab tab = requireTab(clientId, tabId);
        PlanVersion old = versions.findByTabIdAndVersion(tabId, version)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Versão não encontrada."));
        Instant now = clock.instant();
        versions.save(PlanVersion.snapshotOf(tab, "RESTORE", actorEmail(), now));
        tab.applyContent(old.getTitle(), old.getHtml(), old.getShortDescription(), old.getWhatIsIt(),
                old.getObjective(), old.getKeyPoints(), old.getSuggestedQuestions(), tab.getSourceLabel(), now);
        tabs.save(tab);
        touchPlan(client, now);
        audit.record("TAB_RESTORED", clientId, tab.getId(), tab.getName(), "versão " + version);
        return TabView.of(tab, true, tab.isPublished(), !blockedTabIds(clientId).contains(tab.getId()));
    }

    /** Abas que o solicitante pode exportar: a consultoria vê todas; o cliente, só as publicadas e liberadas. */
    @Transactional(readOnly = true)
    public List<PlanTab> readableTabs(AccountPrincipal who, Long clientId) {
        checkAccess(who, clientId);
        requireClient(clientId);
        return who.isAdmin() ? tabs.findByClientIdOrderBySortOrderAscIdAsc(clientId) : visibleTabs(clientId);
    }

    /** Confirma que o cliente existe (para telas da consultoria). */
    @Transactional(readOnly = true)
    public ClientAccount adminClient(Long clientId) {
        return requireClient(clientId);
    }

    /** Aba de um cliente existente, para a consultoria. */
    @Transactional(readOnly = true)
    public PlanTab adminTab(Long clientId, Long tabId) {
        requireClient(clientId);
        return requireTab(clientId, tabId);
    }

    /** Grava o conteúdo gerado a partir do PDF. O estado anterior vira uma versão; a publicação não muda. */
    @Transactional
    public void applyGenerated(Long clientId, Long tabId, GeneratedContent content, String source,
                               String actorEmail, Long actorId) {
        ClientAccount client = requireClient(clientId);
        PlanTab tab = requireTab(clientId, tabId);
        Instant now = clock.instant();
        versions.save(PlanVersion.snapshotOf(tab, "PROCESS", actorEmail, now));
        tab.applyContent(
                content.title() == null ? tab.getTitle() : content.title(),
                HtmlSanitizer.sanitize(content.html()),
                orElse(content.shortDescription(), tab.getShortDescription()),
                orElse(content.whatIsIt(), tab.getWhatIsIt()),
                orElse(content.objective(), tab.getObjective()),
                orElse(PlanTab.join(content.keyPoints()), tab.getKeyPoints()),
                orElse(PlanTab.join(content.suggestedQuestions()), tab.getSuggestedQuestions()),
                source, now);
        tabs.save(tab);
        touchPlan(client, now);
        audit.recordAs(actorId, actorEmail, "TAB_PROCESSED", clientId, tab.getId(), tab.getName(), null);
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    // ---------- apoio ----------

    private void checkAccess(AccountPrincipal who, Long clientId) {
        if (!who.isAdmin() && !who.id().equals(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NO_ACCESS);
        }
    }

    private ClientAccount requireClient(Long clientId) {
        return accounts.findById(clientId)
                .filter(a -> a.getRole() == Role.CLIENT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private PlanTab requireTab(Long clientId, Long tabId) {
        return tabs.findByIdAndClientId(tabId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TAB_NOT_FOUND));
    }

    private Set<Long> blockedTabIds(Long clientId) {
        return scopes.findByClientId(clientId).stream()
                .filter(s -> !s.isAllowed()).map(TabScope::getTabId).collect(Collectors.toSet());
    }

    private static boolean isVisibleToClient(PlanTab tab, Set<Long> blocked) {
        return tab.isPublished() && !blocked.contains(tab.getId());
    }

    private void touchPlan(ClientAccount client, Instant now) {
        client.setPlanVersion(client.getPlanVersion() + 1);
        client.setPlanUpdatedAt(now);
        accounts.save(client);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String actorEmail() {
        return CurrentUser.get().map(AccountPrincipal::email).orElse(null);
    }
}
