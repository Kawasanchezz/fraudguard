package com.fraudguard.rules;

import com.fraudguard.evaluation.TransactionRequest;
import com.fraudguard.profile.CustomerProfile;
import com.fraudguard.velocity.VelocitySnapshot;
import java.math.BigDecimal;
import java.time.Instant;

public final class Fixtures {
    private Fixtures() {}

    /** 15:00 UTC = 12:00 em São Paulo: horário comercial. */
    public static final Instant NOON_SP = Instant.parse("2026-01-15T15:00:00Z");

    public static TransactionRequest tx(String amount) {
        return new TransactionRequest("tx-1", "cust-1", new BigDecimal(amount),
                TransactionRequest.Channel.PIX, "dev-1", "BR-SP", "rcp-1", NOON_SP);
    }

    public static RuleContext ctx(String amount, CustomerProfile profile, VelocitySnapshot v, Instant at) {
        return new RuleContext(tx(amount), profile, v, at);
    }

    public static VelocitySnapshot quiet() {
        return VelocitySnapshot.of(1, false, false, false);
    }

    public static CustomerProfile profile(String avg, String hours) {
        return new CustomerProfile("cust-1", avg == null ? null : new BigDecimal(avg), hours);
    }
}
