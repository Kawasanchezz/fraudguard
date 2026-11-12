package com.fraudguard.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_profile")
public class CustomerProfile {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "avg_amount")
    private BigDecimal avgAmount;

    /** Formato "HH-HH" (ex.: "06-23"; "22-06" atravessa a meia-noite). Nulo = sem preferência. */
    @Column(name = "usual_hours")
    private String usualHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CustomerProfile() {}

    public CustomerProfile(String customerId, BigDecimal avgAmount, String usualHours) {
        this.customerId = customerId;
        this.avgAmount = avgAmount;
        this.usualHours = usualHours;
    }

    public BigDecimal getAvgAmount() { return avgAmount; }

    /** Média móvel exponencial: valores recentes pesam mais, sem guardar o histórico. */
    public void learn(BigDecimal amount) {
        this.avgAmount = (avgAmount == null)
                ? amount
                : avgAmount.multiply(BigDecimal.valueOf(0.9)).add(amount.multiply(BigDecimal.valueOf(0.1)));
        this.avgAmount = this.avgAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    public java.util.OptionalInt usualHour(boolean start) {
        if (usualHours == null || !usualHours.matches("\\d{2}-\\d{2}")) {
            return java.util.OptionalInt.empty();
        }
        String[] p = usualHours.split("-");
        return java.util.OptionalInt.of(Integer.parseInt(p[start ? 0 : 1]));
    }

    /** @return vazio se não há faixa cadastrada; caso contrário se a hora está dentro dela. */
    public java.util.Optional<Boolean> isUsualHour(int hour) {
        var s = usualHour(true);
        var e = usualHour(false);
        if (s.isEmpty() || e.isEmpty()) {
            return java.util.Optional.empty();
        }
        int start = s.getAsInt();
        int end = e.getAsInt();
        boolean inside = start <= end ? (hour >= start && hour < end) : (hour >= start || hour < end);
        return java.util.Optional.of(inside);
    }
}
