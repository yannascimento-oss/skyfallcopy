package org.empresajr.chatjr.web;

import org.empresajr.chatjr.service.ExportService;
import org.empresajr.chatjr.web.dto.ExportView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Download do plano. O próprio cliente exporta o que pode ler; a consultoria exporta qualquer plano. */
@RestController
@RequestMapping("/api/clients/{clientId}")
public class ExportController {

    private final ExportService exports;

    public ExportController(ExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/export.json")
    public ResponseEntity<ExportView> json(@PathVariable Long clientId) {
        ExportView view = exports.json(PlanController.caller(), clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(exports.fileName(clientId, "json")))
                .contentType(MediaType.APPLICATION_JSON).body(view);
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long clientId) {
        byte[] bytes = exports.pdf(PlanController.caller(), clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(exports.fileName(clientId, "pdf")))
                .contentType(MediaType.APPLICATION_PDF).body(bytes);
    }

    private static String attachment(String fileName) {
        return "attachment; filename=\"" + fileName + "\"";
    }
}
