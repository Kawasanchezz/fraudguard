package com.fraudguard;

import com.fraudguard.config.FraudProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(FraudProperties.class)
public class FraudGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudGuardApplication.class, args);
    }
}
