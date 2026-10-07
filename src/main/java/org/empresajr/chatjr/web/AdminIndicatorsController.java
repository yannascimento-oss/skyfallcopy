package org.empresajr.chatjr.web;

import org.empresajr.chatjr.domain.IndicatorCalculator;
import org.empresajr.chatjr.service.IndicatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicadores de uso do chat para a consultoria (restrito ao perfil ADMIN na SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminIndicatorsController {

    private final IndicatorService indicators;

    public AdminIndicatorsController(IndicatorService indicators) {
        this.indicators = indicators;
    }

    @GetMapping("/indicators")
    public IndicatorCalculator.Result overall(@RequestParam(defaultValue = "30") int days) {
        return indicators.overall(days);
    }

    @GetMapping("/clients/{clientId}/indicators")
    public IndicatorCalculator.Result forClient(@PathVariable Long clientId, @RequestParam(defaultValue = "30") int days) {
        return indicators.forClient(clientId, days);
    }
}
