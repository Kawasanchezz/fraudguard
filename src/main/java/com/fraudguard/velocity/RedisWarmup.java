package com.fraudguard.velocity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * O Lettuce abre a conexão na primeira chamada. Com timeout de 100 ms, essa primeira requisição
 * poderia cair no fail-safe sem necessidade; então conectamos já na subida.
 */
@Component
public class RedisWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisWarmup.class);

    private final StringRedisTemplate redis;

    public RedisWarmup(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            redis.execute((RedisCallback<String>) c -> c.ping());
            log.info("Conexão com o Redis aquecida");
        } catch (RuntimeException e) {
            log.warn("Redis indisponível na subida; o fail-safe cobre até a conexão voltar: {}", e.getMessage());
        }
    }
}
