package com.fraudguard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fraudguard")
public record FraudProperties(
        @Valid @DefaultValue Thresholds thresholds,
        @DefaultValue FailSafe failsafe,
        @DefaultValue CircuitBreaker circuitBreaker,
        @DefaultValue("PT24H") Duration clockSkew,
        @Valid Security security) {

    public record Thresholds(@DefaultValue("40") int review, @DefaultValue("80") int block) {

        @AssertTrue(message = "exige 0 < review < block <= 100")
        public boolean isOrdered() {
            return review > 0 && review < block && block <= 100;
        }
    }

    /** Política quando o Redis está indisponível: valor >= highAmount vai para revisão. */
    public record FailSafe(@DefaultValue("5000") BigDecimal highAmount) {}

    public record CircuitBreaker(@DefaultValue("10") int waitOpenSeconds) {}

    /** Sem valores padrão: a aplicação não sobe sem as três senhas (mínimo de 16; analista e admin até 72, limite do BCrypt). */
    public record Security(
            @NotBlank @Size(min = 16, max = 128) String servicePassword,
            @NotBlank @Size(min = 16, max = 72) String analystPassword,
            @NotBlank @Size(min = 16, max = 72) String adminPassword) {}
}
