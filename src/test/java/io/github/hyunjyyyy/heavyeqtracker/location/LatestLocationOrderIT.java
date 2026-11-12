package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LatestLocationOrderIT extends IntegrationTest {

    private static final double NEW_LAT = 37.5665;
    private static final double OLD_LAT = 37.5700;
    private static final double LON = 126.9780;

    @Autowired MockMvc mvc;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired LocationEventRepository eventRepository;
    @Autowired LatestLocationCache cache;
    @Autowired StringRedisTemplate redis;

    @Test
    void lateOlderReportDoesNotOverwriteLatest() throws Exception {
        Long id = newEquipment();
        Instant t = Instant.now();

        report(id, NEW_LAT, t.plusSeconds(5));
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> mvc.perform(get("/api/v1/equipment/{id}/latest", id))
                        .andExpect(jsonPath("$.lat").value(NEW_LAT)));

        String late = report(id, OLD_LAT, t);   // 5초 전에 찍혔는데 지금 도착
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> eventRepository.findByIdempotencyKey(late).isPresent());

        mvc.perform(get("/api/v1/equipment/{id}/latest", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(NEW_LAT));
        List<Point> geo = redis.opsForGeo().position(LatestLocationCache.FLEET_KEY, String.valueOf(id));
        assertThat(geo.get(0).getY()).isCloseTo(NEW_LAT, within(1e-4));
    }

    /* 조회 API의 캐시 미스 채우기도 put을 부른다. 컨슈머보다 늦게 옛 값을 넣어도 지면 안 된다. */
    @Test
    void putIgnoresOlderAndEqualRecordedAt() {
        Instant t = Instant.parse("2026-11-11T07:00:05.5Z");
        cache.put(latest(1L, NEW_LAT, t));
        cache.put(latest(1L, OLD_LAT, Instant.parse("2026-11-11T07:00:05Z")));   // 문자열로는 더 커 보이는 옛 시각
        cache.put(latest(1L, OLD_LAT, t));                                        // 같은 시각

        assertThat(cache.get(1L)).get().extracting(LatestLocation::lat).isEqualTo(NEW_LAT);
    }

    private static LatestLocation latest(Long id, double lat, Instant recordedAt) {
        return new LatestLocation(id, lat, LON, null, null, FixType.SINGLE, 3.0, recordedAt, PositioningSource.PHONE);
    }

    private String report(Long id, double lat, Instant recordedAt) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": %s,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, lat, LON, recordedAt);
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
