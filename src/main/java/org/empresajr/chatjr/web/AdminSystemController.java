package org.empresajr.chatjr.web;

import org.empresajr.chatjr.service.AuditQueryService;
import org.empresajr.chatjr.service.SystemInfoService;
import org.empresajr.chatjr.web.dto.AuditPage;
import org.empresajr.chatjr.web.dto.SystemDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tela Sistema e histórico de auditoria (restrito ao perfil ADMIN na SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminSystemController {

    private final SystemInfoService system;
    private final AuditQueryService audit;

    public AdminSystemController(SystemInfoService system, AuditQueryService audit) {
        this.system = system;
        this.audit = audit;
    }

    @GetMapping("/system")
    public SystemDto.Overview overview() {
        return system.overview();
    }

    @PostMapping("/system/test-email")
    public SystemDto.EmailTest testEmail() {
        return system.testEmail();
    }

    @GetMapping("/audit")
    public AuditPage audit(@RequestParam(required = false) Long clientId,
                           @RequestParam(required = false) String action,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "25") int size) {
        return audit.search(clientId, action, page, size);
    }
}
