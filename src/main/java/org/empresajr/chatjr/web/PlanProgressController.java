package org.empresajr.chatjr.web;

import org.empresajr.chatjr.service.PlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Progresso do plano para o painel inicial (a rosca do Dashboard). */
@RestController
public class PlanProgressController {

    private final PlanService plans;

    public PlanProgressController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping("/api/clients/{clientId}/progress")
    public PlanService.PlanProgress progress(@PathVariable Long clientId) {
        return plans.progress(PlanController.caller(), clientId);
    }
}
