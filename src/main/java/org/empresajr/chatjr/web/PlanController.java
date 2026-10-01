package org.empresajr.chatjr.web;

import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.service.CurrentUser;
import org.empresajr.chatjr.service.PlanService;
import org.empresajr.chatjr.web.dto.TabView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Leitura do plano. O cliente só acessa o próprio; a consultoria acessa qualquer um. */
@RestController
@RequestMapping("/api/clients/{clientId}/tabs")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<TabView> list(@PathVariable Long clientId) {
        return plans.listTabs(caller(), clientId);
    }

    @GetMapping("/{tabId}")
    public TabView get(@PathVariable Long clientId, @PathVariable Long tabId) {
        return plans.getTab(caller(), clientId, tabId);
    }

    static AccountPrincipal caller() {
        return CurrentUser.get()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
    }
}
