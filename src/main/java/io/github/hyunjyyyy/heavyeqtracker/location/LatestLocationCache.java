package io.github.hyunjyyyy.heavyeqtracker.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class LatestLocationCache {

    private static final Logger log = LoggerFactory.getLogger(LatestLocationCache.class);
    private static final Duration TTL = Duration.ofHours(24);

    public static final String FLEET_KEY = "fleet";

    /* 조회 API의 캐시 미스 채우기(HTTP 스레드)와 컨슈머가 같이 쓰므로 비교와 쓰기를 한 번에 한다. */
    private static final DefaultRedisScript<Long> PUT_IF_NEWER = new DefaultRedisScript<>("""
            local cur = redis.call('GET', KEYS[2])
            if cur and tonumber(cur) >= tonumber(ARGV[2]) then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            redis.call('GEOADD', KEYS[3], ARGV[4], ARGV[5], ARGV[6])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public LatestLocationCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecorded(LocationRecorded event) {
        try {
            put(event.latest());
        } catch (RuntimeException e) {
            log.warn("latest-location cache update failed: equipmentId={}", event.latest().equipmentId(), e);
        }
    }

    /** 저장된 것보다 recordedAt이 클 때만 덮는다. 덮었으면 true */
    public boolean put(LatestLocation latest) {
        String json = objectMapper.writeValueAsString(latest);
        String key = keyOf(latest.equipmentId());
        Long applied = redis.execute(PUT_IF_NEWER, List.of(key, key + ":ts", FLEET_KEY),
                json, String.valueOf(micros(latest.recordedAt())), String.valueOf(TTL.toSeconds()),
                String.valueOf(latest.lon()), String.valueOf(latest.lat()), String.valueOf(latest.equipmentId()));
        return applied != null && applied == 1;
    }

    /* JSON 안 recordedAt은 소수 자릿수가 값마다 달라 문자열 비교가 틀린다. 마이크로초 숫자로 비교한다
       (2^53 안이라 Lua number로 정확). */
    private static long micros(Instant t) {
        return t.getEpochSecond() * 1_000_000 + t.getNano() / 1_000;
    }

    public Optional<LatestLocation> get(Long equipmentId) {
        String json = redis.opsForValue().get(keyOf(equipmentId));
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, LatestLocation.class));
    }

    private String keyOf(Long equipmentId) {
        return "loc:" + equipmentId;
    }
}
