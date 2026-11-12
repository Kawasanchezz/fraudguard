package com.fraudguard.rules;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém o conjunto de regras em memória: o endpoint síncrono nunca vai ao banco para isso.
 * Recarrega a cada 30 s (outras instâncias enxergam mudanças sem deploy) e na hora, quando a
 * alteração é feita nesta instância.
 */
@Component
public class RuleSetProvider {

    private static final Logger log = LoggerFactory.getLogger(RuleSetProvider.class);

    private final FraudRuleRepository repository;
    private final AtomicReference<RuleSet> current = new AtomicReference<>(new RuleSet(0, List.of()));

    public RuleSetProvider(FraudRuleRepository repository) {
        this.repository = repository;
    }

    public RuleSet current() {
        return current.get();
    }

    @PostConstruct
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reload() {
        List<FraudRuleEntity> all = repository.findAll();
        Map<String, FraudRuleEntity> latest = all.stream().collect(Collectors.toMap(
                FraudRuleEntity::getCode, Function.identity(),
                (a, b) -> a.getVersion() >= b.getVersion() ? a : b));
        int version = all.stream().mapToInt(FraudRuleEntity::getVersion).max().orElse(0);
        List<RuleConfig> active = latest.values().stream()
                .filter(FraudRuleEntity::isEnabled)
                .sorted(Comparator.comparing(FraudRuleEntity::getCode))
                .map(e -> new RuleConfig(e.getCode(), e.getWeight(), e.getParams()))
                .toList();
        RuleSet old = current.getAndSet(new RuleSet(version, active));
        if (old.version() != version) {
            log.info("Conjunto de regras carregado: versão {} com {} regras ativas", version, active.size());
        }
    }
}
