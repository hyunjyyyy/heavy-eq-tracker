package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PROCESSING = "processing";

    private final StringRedisTemplate redis;

    public IdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key) {
        Boolean ok = redis.opsForValue().setIfAbsent(keyOf(key), PROCESSING, TTL);
        return Boolean.TRUE.equals(ok);
    }

    public Optional<Long> savedEventId(String key) {
        String value = redis.opsForValue().get(keyOf(key));
        if (value == null || PROCESSING.equals(value)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    public void complete(String key, Long eventId) {
        redis.opsForValue().set(keyOf(key), String.valueOf(eventId), TTL);
    }

    public void release(String key) {
        redis.delete(keyOf(key));
    }

    private String keyOf(String key) {
        return "idem:" + key;
    }
}
