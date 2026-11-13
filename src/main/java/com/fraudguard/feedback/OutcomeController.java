package com.fraudguard.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fraud/outcomes")
public class OutcomeController {

    public record OutcomeRequest(@NotBlank @Size(max = 64) String transactionId,
                                 @NotNull OutcomeType type, Instant occurredAt) {}

    private final OutcomeService service;

    public OutcomeController(OutcomeService service) {
        this.service = service;
    }

    @PostMapping
    public OutcomeService.Recorded record(@Valid @RequestBody OutcomeRequest r) {
        return service.record(r.transactionId(), r.type(), r.occurredAt());
    }

    @GetMapping("/report")
    public List<OutcomeService.VersionReport> report() {
        return service.report();
    }
}
