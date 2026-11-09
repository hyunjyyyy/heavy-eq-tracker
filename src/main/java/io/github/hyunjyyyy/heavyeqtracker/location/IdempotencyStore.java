package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public IdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key) {
        Boolean ok = redis.opsForValue().setIfAbsent(keyOf(key), "accepted", TTL);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String key) {
        redis.delete(keyOf(key));
    }

    private String keyOf(String key) {
        return "idem:" + key;
    }
}
