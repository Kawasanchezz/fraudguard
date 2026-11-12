package com.fraudguard.cases;

import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fila de revisão manual: apenas o perfil ANALISTA (ver SecurityConfig). */
@Validated
@RestController
@RequestMapping("/cases")
public class CaseController {

    public record Resolution(@Size(max = 500) String note) {}

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<CaseService.CaseView> list(@RequestParam(defaultValue = "ABERTO") FraudCase.Status status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    public CaseService.CaseView approve(@PathVariable Long id, @RequestBody(required = false) Resolution body,
                                        Authentication auth) {
        return service.resolve(id, FraudCase.Status.APROVADO, auth.getName(), body == null ? null : body.note());
    }

    @PostMapping("/{id}/block")
    public CaseService.CaseView block(@PathVariable Long id, @RequestBody(required = false) Resolution body,
                                      Authentication auth) {
        return service.resolve(id, FraudCase.Status.BLOQUEADO, auth.getName(), body == null ? null : body.note());
    }
}
