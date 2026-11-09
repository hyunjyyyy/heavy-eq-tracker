package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RateLimiter {

    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 이번 초에 이 장비가 보낸 횟수(이번 요청 포함) */
    public long hit(String equipmentId) {
        String key = "rl:" + equipmentId + ":" + Instant.now().getEpochSecond();
        Long n = redis.execute(HIT, List.of(key), "1");
        return n == null ? 0 : n;
    }
}
