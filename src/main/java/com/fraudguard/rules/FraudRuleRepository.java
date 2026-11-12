package com.fraudguard.rules;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudRuleRepository extends JpaRepository<FraudRuleEntity, Long> {}
