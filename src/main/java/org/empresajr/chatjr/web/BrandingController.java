package org.empresajr.chatjr.web;

import org.empresajr.chatjr.service.BrandingService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/** Logo da consultoria: leitura pública (aparece na landing) e troca só pela administração. */
@RestController
public class BrandingController {

    private final BrandingService branding;

    public BrandingController(BrandingService branding) {
        this.branding = branding;
    }

    @GetMapping("/api/public/logo")
    public ResponseEntity<byte[]> logo() {
        return branding.logo()
                .map(l -> ResponseEntity.ok().contentType(MediaType.parseMediaType(l.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(l.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/admin/settings/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > BrandingService.MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "O logo pode ter no máximo 1 MB.");
        }
        branding.save(file.getBytes());
    }

    @DeleteMapping("/api/admin/settings/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove() {
        branding.remove();
    }
}
