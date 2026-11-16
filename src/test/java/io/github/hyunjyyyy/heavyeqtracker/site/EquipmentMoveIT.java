package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.zone.Zone;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneCatalog;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneKind;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* 확인은 JDBC로 — 이동 뒤엔 현장 1과 현장 B에 행이 나뉘어 리포지토리로는 한쪽만 보인다 */
class EquipmentMoveIT extends IntegrationTest {

    private static final String SQUARE =
            "[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]";

    @Autowired MockMvc mvc;
    @Autowired SiteRepository siteRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired ZoneCatalog zoneCatalog;
    @Autowired EquipmentMoveService moveService;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @Test
    void moveClosesPresencePairAndFleetOfOldSite() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        zoneRepository.save(new Zone("yard", ZoneKind.WORK, SQUARE));   // 현장 1 구역
        zoneCatalog.reload();
        Long mover = newEquipment();
        Long stayer = newEquipment();
        report(mover, 37.56650);
        await().atMost(Duration.ofSeconds(10)).until(() -> redis.opsForGeo()
                .position(LatestLocationCache.fleetKey(1L), String.valueOf(mover)).get(0) != null);
        report(stayer, 37.56653);   // 약 3 m → NEAR
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> count("SELECT COUNT(*) FROM proximity_pair_state") == 1
                        && count("SELECT COUNT(*) FROM equipment_zone_presence WHERE equipment_id = ?", mover) == 1);

        mvc.perform(post("/api/v1/sites/1/equipment/{id}/move", mover)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toSiteId\": " + siteB + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value(siteB));

        assertThat(count("SELECT COUNT(*) FROM equipment_site_assignment WHERE equipment_id = ? AND assigned_to IS NULL AND site_id = ?", mover, siteB)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM equipment_zone_presence WHERE equipment_id = ?", mover)).isZero();
        assertThat(count("SELECT COUNT(*) FROM geofence_event WHERE equipment_id = ? AND type = 'EXIT' AND location_event_id IS NULL AND site_id = 1", mover)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM proximity_pair_state")).isZero();
        assertThat(count("SELECT COUNT(*) FROM proximity_event WHERE type = 'CLEAR' AND location_event_id IS NULL AND site_id = 1")).isEqualTo(1);
        assertThat(redis.opsForZSet().score(LatestLocationCache.fleetKey(1L), String.valueOf(mover))).isNull();
        assertThat(count("SELECT COUNT(*) FROM equipment_zone_presence WHERE equipment_id = ?", stayer)).isEqualTo(1);   // 남은 장비는 그대로
    }

    @Test
    void concurrentMovesOfSameEquipmentOnlyOneWins() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        long siteC = siteRepository.save(new Site("test-site-c")).getId();
        Long id = newEquipment();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> r1 = pool.submit(attempt(start, id, siteB));
            Future<String> r2 = pool.submit(attempt(start, id, siteC));
            start.countDown();                                   // 둘을 동시에 출발시킨다
            List<String> outcomes = java.util.stream.Stream.of(r1.get(), r2.get()).sorted().toList();
        } finally {
            pool.shutdown();
        }
        assertThat(count("SELECT COUNT(*) FROM equipment_site_assignment WHERE equipment_id = ? AND assigned_to IS NULL", id)).isEqualTo(1);
    }

    @Test
    void invalidMovesAreRejected() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        Long id = newEquipment();
        move(1, id, 1).andExpect(status().isConflict());        // 같은 현장
        move(1, id, 999).andExpect(status().isNotFound());       // 없는 현장
        move(siteB, id, 1).andExpect(status().isConflict());    // 지금 그 현장에 없음
    }

    /* 스레드마다 현재 현장이 비어 있어 옛 현장(1)을 직접 정한다 — 인터셉터가 하는 일 */
    private Callable<String> attempt(CountDownLatch start, Long id, long to) {
        return () -> {
            start.await();
            try {
                SiteContext.call(1L, () -> moveService.move(1L, id, to));
                return "moved";
            } catch (MoveConflictException e) {
                return "conflict";
            }
        };
    }

    private org.springframework.test.web.servlet.ResultActions move(long from, Long id, long to) throws Exception {
        return mvc.perform(post("/api/v1/sites/{s}/equipment/{id}/move", from, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toSiteId\": " + to + "}"));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void report(Long id, double lat) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, lat, Instant.now());
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }
}
