package com.fraudguard.velocity;

import com.fraudguard.evaluation.TransactionRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Janela deslizante com sorted set: ZADD (timestamp) + ZREMRANGEBYSCORE (remove o que saiu da
 * janela) + ZCARD (conta), tudo em um único round-trip via pipeline.
 * Dispositivo, localização e destinatário "conhecidos" ficam em sets (SADD retorna 1 se novo).
 * LGPD: nada é guardado em claro; valores e id do cliente viram hash SHA-256 truncado, com TTL.
 */
@Component("redisVelocityStore")
public class RedisVelocityStore implements VelocityStore {

    private static final long SET_TTL_SECONDS = Duration.ofDays(90).toSeconds();

    private final StringRedisTemplate redis;

    public RedisVelocityStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public VelocitySnapshot observe(TransactionRequest tx, Instant at, Duration window) {
        String cust = hash(tx.customerId());
        byte[] txKey = bytes("vel:" + cust + ":tx");
        long nowMs = at.toEpochMilli();
        double cutoff = nowMs - window.toMillis();

        String[] values = {tx.deviceId(), tx.location(), tx.recipientId()};
        String[] suffix = {"dev", "loc", "rcp"};

        List<Object> r = redis.executePipelined((RedisCallback<Object>) c -> {
            // Membro = transactionId: reenvio do mesmo evento não conta duas vezes (idempotente).
            c.zSetCommands().zAdd(txKey, nowMs, bytes(tx.transactionId()));
            c.zSetCommands().zRemRangeByScore(txKey, Range.closed(0d, cutoff));
            c.zSetCommands().zCard(txKey);
            c.keyCommands().expire(txKey, window.toSeconds() * 2);
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null || values[i].isBlank()) {
                    continue;
                }
                byte[] key = bytes("vel:" + cust + ":" + suffix[i]);
                c.setCommands().sAdd(key, bytes(hash(values[i])));
                c.setCommands().sCard(key);
                c.keyCommands().expire(key, SET_TTL_SECONDS);
            }
            return null;
        });

        long count = ((Number) r.get(2)).longValue();
        int idx = 4;
        boolean[] isNew = new boolean[3];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || values[i].isBlank()) {
                continue;
            }
            long added = ((Number) r.get(idx)).longValue();
            long card = ((Number) r.get(idx + 1)).longValue();
            // O primeiro valor já visto é a linha de base, não "novidade".
            isNew[i] = added == 1 && card > 1;
            idx += 3;
        }
        return VelocitySnapshot.of(count, isNew[0], isNew[1], isNew[2]);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String hash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes(value));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
