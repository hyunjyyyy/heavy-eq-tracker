package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LatestLocationRebuilderIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired LocationEventRepository events;
    @Autowired LatestLocationCache cache;
    @Autowired LatestLocationRebuilder rebuilder;
    @Autowired StringRedisTemplate redis;

    @Test
    void rebuildRestoresCacheAndSiteFleetAfterRedisLoss() throws Exception {
        Long id = newEquipment();
        String key = report(id, Instant.now());
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> events.findByIdempotencyKey(key).isPresent() && cache.get(id).isPresent());

        flushRedis();
        redis.opsForGeo().add("fleet", new Point(126.978, 37.5665), "999");   // #30 전에 남은 옛 키
        assertThat(cache.get(id)).isEmpty();

        assertThat(rebuilder.rebuild()).isEqualTo(1);

        assertThat(cache.get(id)).get().extracting(LatestLocation::siteId).isEqualTo(1L);
        assertThat(redis.opsForGeo().position(LatestLocationCache.fleetKey(1L), String.valueOf(id)).get(0)).isNotNull();
        assertThat(redis.hasKey("fleet")).isFalse();
    }

    @Test
    void equipmentSilentForMoreThan24HoursIsNotRebuilt() throws Exception {
        Long id = newEquipment();
        String key = report(id, Instant.now().minus(Duration.ofHours(25)));
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> events.findByIdempotencyKey(key).isPresent());

        flushRedis();
        assertThat(rebuilder.rebuild()).isZero();
        assertThat(cache.get(id)).isEmpty();
    }

    private void flushRedis() {
        try (RedisConnection c = redis.getRequiredConnectionFactory().getConnection()) {
            c.serverCommands().flushAll();
        }
    }

    private String report(Long id, Instant recordedAt) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": 37.5665, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, recordedAt);
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
