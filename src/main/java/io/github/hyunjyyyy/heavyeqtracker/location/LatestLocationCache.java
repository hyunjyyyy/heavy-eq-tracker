package io.github.hyunjyyyy.heavyeqtracker.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class LatestLocationCache {

    private static final Logger log = LoggerFactory.getLogger(LatestLocationCache.class);
    private static final Duration TTL = Duration.ofHours(24);

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

    public void put(LatestLocation latest) {
        String json = objectMapper.writeValueAsString(latest);
        redis.opsForValue().set(keyOf(latest.equipmentId()), json, TTL);
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
