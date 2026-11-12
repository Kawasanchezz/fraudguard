package com.fraudguard.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fraud_rule")
public class FraudRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    private int weight;
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params;

    @Column(nullable = false, updatable = false)
    private int version;

    protected FraudRuleEntity() {}

    public FraudRuleEntity(String code, int weight, boolean enabled, Map<String, Object> params, int version) {
        this.code = code;
        this.weight = weight;
        this.enabled = enabled;
        this.params = params;
        this.version = version;
    }

    public String getCode() { return code; }
    public int getWeight() { return weight; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getParams() { return params; }
    public int getVersion() { return version; }
}
