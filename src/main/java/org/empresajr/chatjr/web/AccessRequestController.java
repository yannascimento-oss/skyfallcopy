package org.empresajr.chatjr.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.empresajr.chatjr.service.AccessRequestService;
import org.empresajr.chatjr.web.dto.AccessRequestBody;
import org.empresajr.chatjr.web.dto.AccessRequestView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Pedido público de acesso (landing) e a fila desses pedidos para a consultoria. */
@RestController
public class AccessRequestController {

    private final AccessRequestService requests;

    public AccessRequestController(AccessRequestService requests) {
        this.requests = requests;
    }

    @PostMapping("/api/access-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submit(@Valid @RequestBody AccessRequestBody body, HttpServletRequest request) {
        requests.submit(body.name(), body.email(), body.company(), body.phone(), body.message(), body.website(),
                request.getRemoteAddr());
    }

    @GetMapping("/api/admin/access-requests")
    public List<AccessRequestView> open() {
        return requests.listOpen().stream().map(AccessRequestView::of).toList();
    }

    @GetMapping("/api/admin/access-requests/count")
    public Map<String, Long> count() {
        return Map.of("open", requests.countOpen());
    }

    @PostMapping("/api/admin/access-requests/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean accessCreated) {
        requests.close(id, PlanController.caller().email(), accessCreated);
    }
}
