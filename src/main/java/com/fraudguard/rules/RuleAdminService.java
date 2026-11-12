package com.fraudguard.rules;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Alterar regra = inserir nova versão (nunca UPDATE): o histórico das decisões continua explicável. */
@Service
public class RuleAdminService {

    private static final int MAX_PARAMS = 10;
    private static final int MAX_TEXT = 64;

    private final FraudRuleRepository repository;
    private final RuleSetProvider provider;

    public RuleAdminService(FraudRuleRepository repository, RuleSetProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    public List<FraudRuleEntity> latestRules() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(FraudRuleEntity::getCode, Function.identity(),
                        (a, b) -> a.getVersion() >= b.getVersion() ? a : b))
                .values().stream()
                .sorted(Comparator.comparing(FraudRuleEntity::getCode))
                .toList();
    }

    @Transactional
    public FraudRuleEntity update(String code, Integer weight, Boolean enabled, Map<String, Object> params) {
        List<FraudRuleEntity> all = repository.findAll();
        FraudRuleEntity latest = all.stream()
                .filter(r -> r.getCode().equals(code))
                .max(Comparator.comparingInt(FraudRuleEntity::getVersion))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regra desconhecida"));
        if (weight != null && (weight < 0 || weight > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weight deve estar entre 0 e 100");
        }
        if (params != null) {
            validateParams(params);
        }
        int nextVersion = all.stream().mapToInt(FraudRuleEntity::getVersion).max().orElse(0) + 1;
        try {
            FraudRuleEntity next = repository.saveAndFlush(new FraudRuleEntity(
                    code,
                    weight != null ? weight : latest.getWeight(),
                    enabled != null ? enabled : latest.isEnabled(),
                    params != null ? params : latest.getParams(),
                    nextVersion));
            provider.reload();
            return next;
        } catch (DataIntegrityViolationException concurrentUpdate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Regra alterada por outra requisição; tente novamente");
        }
    }

    private static void validateParams(Map<String, Object> params) {
        if (params.size() > MAX_PARAMS) {
            throw bad("params aceita no máximo " + MAX_PARAMS + " chaves");
        }
        params.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_TEXT) {
                throw bad("chave de params inválida");
            }
            if (value instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d) || d < 0 || d > 1_000_000_000d) {
                    throw bad("valor numérico fora do intervalo em " + key);
                }
                if (key.endsWith("Hour") && d > 23) {
                    throw bad(key + " deve estar entre 0 e 23");
                }
            } else if (value instanceof String s) {
                if (s.length() > MAX_TEXT) {
                    throw bad("texto longo demais em " + key);
                }
                if (key.equals("zone")) {
                    try {
                        ZoneId.of(s);
                    } catch (DateTimeException e) {
                        throw bad("zone inválida");
                    }
                }
            } else {
                throw bad("params aceita apenas números e textos");
            }
        });
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
