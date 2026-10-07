package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.HtmlText;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.Slugs;
import org.empresajr.chatjr.web.dto.ExportView;
import org.empresajr.chatjr.web.dto.TabView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

/** Exportação do plano em JSON e PDF. O cliente exporta só o que pode ler; a consultoria exporta tudo. */
@Service
public class ExportService {

    private final PlanService plans;
    private final AuditService audit;
    private final Clock clock;

    public ExportService(PlanService plans, AuditService audit, Clock clock) {
        this.plans = plans;
        this.audit = audit;
        this.clock = clock;
    }

    public ExportView json(AccountPrincipal who, Long clientId) {
        ClientAccount client = plans.adminClient(clientId);
        List<PlanTab> tabs = plans.readableTabs(who, clientId);
        audit.record("EXPORT_JSON", clientId, null, null, null);
        return new ExportView(client.getCompany(), client.getSegment(), clock.instant(), client.getPlanVersion(),
                tabs.stream().map(t -> TabView.of(t, true, who.isAdmin() ? t.isPublished() : null, null)).toList());
    }

    public byte[] pdf(AccountPrincipal who, Long clientId) {
        ClientAccount client = plans.adminClient(clientId);
        List<PlanTab> tabs = plans.readableTabs(who, clientId);
        List<PdfExporter.Section> sections = tabs.stream()
                .filter(t -> !t.getHtml().isBlank())
                .map(t -> new PdfExporter.Section(t.getTitle(), HtmlText.toPlain(t.getHtml()))).toList();
        String date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Bahia")).format(clock.instant());
        audit.record("EXPORT_PDF", clientId, null, null, null);
        return PdfExporter.render("Plano de Negócios - " + client.getCompany(), "Exportado em " + date
                + " | Chat Jr - Empresa JR", sections);
    }

    public String fileName(Long clientId, String extension) {
        return "plano-" + Slugs.slugify(plans.adminClient(clientId).getCompany()) + "." + extension;
    }
}
