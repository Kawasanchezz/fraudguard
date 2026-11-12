package com.fraudguard.rules;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rules")
public class RuleAdminController {

    public record RuleView(String code, int weight, boolean enabled, Map<String, Object> params, int version) {
        static RuleView of(FraudRuleEntity e) {
            return new RuleView(e.getCode(), e.getWeight(), e.isEnabled(), e.getParams(), e.getVersion());
        }
    }

    /** Campos nulos = manter o valor atual. */
    public record UpdateRule(Integer weight, Boolean enabled, Map<String, Object> params) {}

    private final RuleAdminService service;

    public RuleAdminController(RuleAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<RuleView> list() {
        return service.latestRules().stream().map(RuleView::of).toList();
    }

    @PutMapping("/{code}")
    public RuleView update(@PathVariable String code, @RequestBody UpdateRule body) {
        return RuleView.of(service.update(code, body.weight(), body.enabled(), body.params()));
    }
}
