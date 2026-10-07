package org.empresajr.chatjr.web;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.SettingsAdminService;
import org.empresajr.chatjr.web.dto.SettingsDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configurações da consultoria (restrito ao perfil ADMIN na SecurityConfig). A chave da IA nunca é devolvida. */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final SettingsAdminService settings;

    public AdminSettingsController(SettingsAdminService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsDto.All get() {
        return settings.view();
    }

    @PutMapping("/ai")
    public SettingsDto.Ai updateAi(@Valid @RequestBody SettingsDto.UpdateAi body) {
        return settings.updateAi(body);
    }

    @DeleteMapping("/ai/key")
    public SettingsDto.Ai removeKey() {
        return settings.removeAiKey();
    }

    @PostMapping("/ai/test")
    public SettingsDto.AiTest testAi() {
        return settings.testAi();
    }

    @PutMapping("/limits")
    public SettingsDto.Limits updateLimits(@Valid @RequestBody SettingsDto.UpdateLimits body) {
        return settings.updateLimits(body);
    }

    @PutMapping("/organization")
    public SettingsDto.Organization updateOrganization(@Valid @RequestBody SettingsDto.UpdateOrganization body) {
        return settings.updateOrganization(body);
    }
}
