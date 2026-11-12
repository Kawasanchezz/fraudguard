package com.fraudguard.rules;

import static com.fraudguard.rules.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudguard.velocity.VelocitySnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NewRecipientHighAmountRuleTest {

    private final NewRecipientHighAmountRule rule = new NewRecipientHighAmountRule();

    @ParameterizedTest
    @CsvSource({
            "true,2000,true",      // limite inclusivo
            "true,1999.99,false",
            "false,50000,false",   // destinatário conhecido nunca dispara
            "true,50000,true"
    })
    void defaultMinAmount(boolean newRecipient, String amount, boolean fires) {
        var v = VelocitySnapshot.of(1, false, false, newRecipient);
        assertThat(rule.evaluate(ctx(amount, null, v, NOON_SP), Map.of()).isPresent()).isEqualTo(fires);
    }

    @Test
    void respectsConfiguredMinAmount() {
        var v = VelocitySnapshot.of(1, false, false, true);
        assertThat(rule.evaluate(ctx("500", null, v, NOON_SP), Map.of("minAmount", 500))).isPresent();
        assertThat(rule.evaluate(ctx("499", null, v, NOON_SP), Map.of("minAmount", 500))).isEmpty();
    }

    @Test
    void unavailableCacheNeverFires() {
        assertThat(rule.evaluate(ctx("99999", null, VelocitySnapshot.unavailable(), NOON_SP), Map.of())).isEmpty();
    }

    @Test
    void exposesCode() {
        assertThat(rule.code()).isEqualTo("NEW_RECIPIENT_HIGH_AMOUNT");
    }
}
