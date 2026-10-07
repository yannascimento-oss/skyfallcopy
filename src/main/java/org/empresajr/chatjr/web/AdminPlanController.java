package org.empresajr.chatjr.web;

import org.empresajr.chatjr.web.dto.ImportPlanRequest;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.PlanService;
import org.empresajr.chatjr.web.dto.CreateTabRequest;
import org.empresajr.chatjr.web.dto.ScopeRequest;
import org.empresajr.chatjr.web.dto.TabView;
import org.empresajr.chatjr.web.dto.UpdateTabRequest;
import org.empresajr.chatjr.web.dto.VersionView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestão das etapas do plano pela consultoria (restrito ao perfil ADMIN na SecurityConfig). */
@RestController
@RequestMapping("/api/admin/clients/{clientId}/tabs")
public class AdminPlanController {

    private final PlanService plans;

    public AdminPlanController(PlanService plans) {
        this.plans = plans;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TabView create(@PathVariable Long clientId, @Valid @RequestBody CreateTabRequest body) {
        return plans.createTab(clientId, body.name());
    }

    @PutMapping("/{tabId}")
    public TabView update(@PathVariable Long clientId, @PathVariable Long tabId,
                          @Valid @RequestBody UpdateTabRequest body) {
        return plans.updateTab(clientId, tabId, body);
    }

    @DeleteMapping("/{tabId}")
    public ResponseEntity<Void> delete(@PathVariable Long clientId, @PathVariable Long tabId) {
        plans.deleteTab(clientId, tabId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{tabId}/scope")
    public ResponseEntity<Void> scope(@PathVariable Long clientId, @PathVariable Long tabId,
                                      @Valid @RequestBody ScopeRequest body) {
        plans.setScope(clientId, tabId, body.allowed());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tabId}/versions")
    public List<VersionView> versions(@PathVariable Long clientId, @PathVariable Long tabId) {
        return plans.listVersions(clientId, tabId);
    }

    @PostMapping("/{tabId}/versions/{version}/restore")
    public TabView restore(@PathVariable Long clientId, @PathVariable Long tabId, @PathVariable int version) {
        return plans.restoreVersion(clientId, tabId, version);
    }

    /** Importa etapas de um JSON exportado. */
    @PostMapping("/import")
    public PlanService.ImportResult importPlan(@PathVariable Long clientId, @Valid @RequestBody ImportPlanRequest body) {
        return plans.importPlan(clientId, body.tabs().stream().map(ImportPlanRequest.Tab::toUpdate).toList(),
                body.tabs().stream().map(ImportPlanRequest.Tab::name).toList());
    }
}
