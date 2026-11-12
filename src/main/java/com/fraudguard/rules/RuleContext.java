package com.fraudguard.rules;

import com.fraudguard.evaluation.TransactionRequest;
import com.fraudguard.profile.CustomerProfile;
import com.fraudguard.velocity.VelocitySnapshot;
import java.time.Instant;

/** Tudo que uma regra precisa, já resolvido: regras são funções puras e fáceis de testar. */
public record RuleContext(
        TransactionRequest tx,
        CustomerProfile profile,   // nulo para cliente sem histórico
        VelocitySnapshot velocity,
        Instant at) {}
