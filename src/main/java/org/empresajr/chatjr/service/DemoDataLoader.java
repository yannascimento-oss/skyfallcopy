package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.repository.ClientAccountRepository;
import org.empresajr.chatjr.web.dto.TabView;
import org.empresajr.chatjr.web.dto.UpdateTabRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dados de demonstração, só no perfil "demo". A senha vem de CHATJR_DEMO_PASSWORD: nenhuma credencial fica no código
 * nem em migração. Só roda em instalação vazia e nunca sobrescreve dados existentes.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    static final String ADMIN_EMAIL = "admin@demo.chatjr.test";

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final SetupService setup;
    private final AccountService accounts;
    private final PlanService plans;
    private final SettingsService settings;
    private final ClientAccountRepository repository;
    private final String password;

    public DemoDataLoader(SetupService setup, AccountService accounts, PlanService plans, SettingsService settings,
                          ClientAccountRepository repository, @Value("${chatjr.demo.password:}") String password) {
        this.setup = setup;
        this.accounts = accounts;
        this.plans = plans;
        this.settings = settings;
        this.repository = repository;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    /** Devolve true se criou os dados de demonstração. */
    public boolean load() {
        if (password == null || password.isBlank()) {
            log.warn("Perfil demo ativo, mas CHATJR_DEMO_PASSWORD não foi definida: nada foi criado.");
            return false;
        }
        if (settings.isSetupDone()) {
            log.info("A instalação já foi feita: os dados de demonstração não serão criados.");
            return false;
        }
        setup.complete("Empresa JR (demonstração)", "Administrador Demo", ADMIN_EMAIL, password, null);
        AccountPrincipal admin = repository.findByEmail(ADMIN_EMAIL).orElseThrow().toPrincipal();

        createClient(admin, "Marina Alves", "marina@norte-fit.demo.chatjr.test", "Norte Fit", "Academias", Map.of(
                "Resumo Executivo", "<p>A Norte Fit é uma academia de bairro focada em treino funcional para pessoas de 25 a 45 anos. "
                        + "O plano prevê abrir a primeira unidade em Salvador e chegar a 300 alunos em 12 meses.</p>",
                "Mercado", "<p>O mercado de academias no Brasil cresce cerca de 10% ao ano. No bairro escolhido há 6 academias, "
                        + "mas só uma oferece treino funcional em grupos pequenos.</p>",
                "Público-Alvo", "<p>Profissionais que trabalham perto, com renda de 3 a 8 salários mínimos, que querem treinar "
                        + "em horários curtos, antes ou depois do expediente.</p>",
                "Riscos", "<p>O principal risco é a perda de alunos para academias de baixo custo. Outro risco é o aluguel do "
                        + "ponto, que representa 28% das despesas fixas.</p>",
                "Investimento Inicial", "<p>O investimento inicial é de R$ 180 mil: R$ 90 mil em equipamentos, R$ 60 mil em reforma "
                        + "e R$ 30 mil em capital de giro para os três primeiros meses.</p>"));
        createClient(admin, "Beatriz Souza", "beatriz@doce-bia.demo.chatjr.test", "Doce Bia", "Confeitaria", Map.of(
                "Resumo Executivo", "<p>A Doce Bia vende bolos e doces por encomenda, com entrega em Salvador. "
                        + "O plano prevê faturar R$ 15 mil por mês no primeiro ano.</p>",
                "Produto/Serviço", "<p>Bolos personalizados, caixas de doces para festas e uma linha de sobremesas individuais "
                        + "para cafeterias parceiras.</p>"));
        log.info("Dados de demonstração criados: administrador {} e 2 clientes.", ADMIN_EMAIL);
        return true;
    }

    private void createClient(AccountPrincipal admin, String name, String email, String company, String segment,
                              Map<String, String> contentByTab) {
        AccountService.Invite invite = accounts.createClient(name, email, company, segment);
        accounts.acceptToken(invite.token(), password);
        ClientAccount client = invite.account();
        List<TabView> tabs = plans.listTabs(admin, client.getId());
        for (TabView tab : tabs) {
            String html = contentByTab.get(tab.name());
            if (html != null) {
                plans.updateTab(client.getId(), tab.id(), new UpdateTabRequest(tab.title(), html, tab.shortDescription(),
                        tab.whatIsIt(), tab.objective(), List.of(), List.of(), "Plano de demonstração", true));
            }
        }
    }
}
