package org.empresajr.chatjr.web;

import org.empresajr.chatjr.service.BrandingService;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.SettingsService;
import org.empresajr.chatjr.service.SetupService;
import org.empresajr.chatjr.web.dto.SetupRequest;
import org.empresajr.chatjr.web.dto.SetupStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setup;
    private final SettingsService settings;
    private final BrandingService branding;

    public SetupController(SetupService setup, SettingsService settings, BrandingService branding) {
        this.setup = setup;
        this.settings = settings;
        this.branding = branding;
    }

    @GetMapping("/status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(!settings.isSetupDone(), settings.get(SettingsService.ORG_NAME).orElse(null), branding.hasLogo());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void complete(@Valid @RequestBody SetupRequest body) {
        setup.complete(body.orgName(), body.adminName(), body.adminEmail(), body.password(), body.aiKey());
    }
}
