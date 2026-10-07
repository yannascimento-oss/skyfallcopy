package org.empresajr.chatjr.web;

import jakarta.validation.Valid;
import org.empresajr.chatjr.service.AttachmentService;
import org.empresajr.chatjr.service.ProcessingService;
import org.empresajr.chatjr.web.dto.AttachmentView;
import org.empresajr.chatjr.web.dto.LinkRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** PDF, link de slides e processamento de cada etapa (restrito ao perfil ADMIN na SecurityConfig). */
@RestController
@RequestMapping("/api/admin/clients/{clientId}/tabs/{tabId}/attachment")
public class AdminAttachmentController {

    private final AttachmentService attachments;
    private final ProcessingService processing;

    public AdminAttachmentController(AttachmentService attachments, ProcessingService processing) {
        this.attachments = attachments;
        this.processing = processing;
    }

    @GetMapping
    public AttachmentView get(@PathVariable Long clientId, @PathVariable Long tabId) {
        return attachments.view(clientId, tabId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentView upload(@PathVariable Long clientId, @PathVariable Long tabId,
                                 @RequestParam("file") MultipartFile file) {
        return attachments.upload(clientId, tabId, file);
    }

    @PutMapping("/link")
    public AttachmentView link(@PathVariable Long clientId, @PathVariable Long tabId,
                               @Valid @RequestBody LinkRequest body) {
        return attachments.setLink(clientId, tabId, body.slideLink());
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable Long clientId, @PathVariable Long tabId) {
        attachments.remove(clientId, tabId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<AttachmentView> process(@PathVariable Long clientId, @PathVariable Long tabId) {
        return ResponseEntity.accepted().body(processing.start(clientId, tabId, PlanController.caller()));
    }
}
