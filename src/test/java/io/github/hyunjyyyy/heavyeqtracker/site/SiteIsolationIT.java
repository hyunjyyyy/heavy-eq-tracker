package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.zone.Zone;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneCatalog;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneKind;
import io.github.hyunjyyyy.heavyeqtracker.zone.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* "다른 현장이면 안 생긴다"는 판정이 고장 나도 통과한다. 같은 현장이면 생기는 대조군을 같이 둔다.
   확인은 JDBC로 — 리포지토리엔 시험 스레드의 현장 조건이 붙는다. */
class SiteIsolationIT extends IntegrationTest {

    private static final String SQUARE =
            "[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]";

    @Autowired MockMvc mvc;
    @Autowired SiteRepository siteRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired ZoneCatalog zoneCatalog;
    @Autowired LatestLocationCache cache;
    @Autowired JdbcTemplate jdbc;

    @Test
    void zoneOfAnotherSiteIsNotJudged() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        SiteContext.run(siteB, () -> zoneRepository.save(new Zone("site-b-yard", ZoneKind.WORK, SQUARE)));
        zoneCatalog.reload();
        Long outsider = newEquipment();          // 현장 1
        Long insider = newEquipment(siteB);      // 대조군

        String k1 = report(outsider, 37.5665, 126.9780);
        String k2 = report(insider, 37.5665, 126.9780);
        waitSaved(k1, k2);

        assertThat(count("SELECT COUNT(*) FROM geofence_event WHERE equipment_id = ?", insider)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM geofence_event WHERE equipment_id = ?", outsider)).isZero();
    }

    @Test
    void equipmentOfNeighborSiteIsNotNear() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        Long a1 = newEquipment();
        Long a2 = newEquipment();                // a1과 같은 현장 → 대조군
        Long b = newEquipment(siteB);            // 같은 자리, 다른 현장

        waitSaved(report(a1, 37.56650, 126.97800));
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.get(a1).isPresent());   // a1이 GEO에 들어간 뒤에
        waitSaved(report(a2, 37.56653, 126.97800));                                       // 약 3 m 북쪽
        waitSaved(report(b, 37.56651, 126.97800));
        waitSaved(report(a1, 37.56650, 126.97800));                                       // b가 GEO에 들어간 뒤 a1 다시

        assertThat(count("SELECT COUNT(*) FROM proximity_event WHERE type = 'NEAR' AND equipment_a_id = ? AND equipment_b_id = ?",
                Math.min(a1, a2), Math.max(a1, a2))).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM proximity_event WHERE equipment_a_id = ? OR equipment_b_id = ?", b, b)).isZero();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void waitSaved(String... keys) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    long n = 0;
                    for (String k : keys) {
                        n += count("SELECT COUNT(*) FROM location_event WHERE idempotency_key = ?", k);
                    }
                    return n == keys.length;
                });
    }

    private String report(Long id, double lat, double lon) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": %s,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, lat, lon, Instant.now());
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
