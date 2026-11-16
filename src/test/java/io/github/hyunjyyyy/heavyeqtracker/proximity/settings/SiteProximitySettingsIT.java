package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.site.Site;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class SiteProximitySettingsIT extends IntegrationTest {

    private static final double LAT = 37.56650;
    private static final double LAT_15M_NORTH = LAT + 0.000135;   // 약 15 m

    @Autowired MockMvc mvc;
    @Autowired SiteRepository siteRepository;
    @Autowired SiteProximitySettingsRepository settingsRepository;
    @Autowired ProximitySettingsResolver resolver;
    @Autowired LatestLocationCache cache;
    @Autowired JdbcTemplate jdbc;

    /* 오차 0.05 m씩: 현장 1은 20 + 0.1 > 15 → NEAR, 현장 B는 기본 10 + 0.1 < 15 → 아님 */
    @Test
    void thresholdOfEachSiteDecidesNear() throws Exception {
        long siteB = siteRepository.save(new Site("test-site-b")).getId();
        SiteProximitySettings site1 = new SiteProximitySettings(1L);
        site1.update(20.0, null, null, null, null);
        settingsRepository.save(site1);
        resolver.evict(1L);

        Long a1 = newEquipment();
        Long a2 = newEquipment();
        Long b1 = newEquipment(siteB);
        Long b2 = newEquipment(siteB);

        waitSaved(report(a1, LAT));
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.get(a1).isPresent());
        waitSaved(report(a2, LAT_15M_NORTH));
        waitSaved(report(b1, LAT));
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.get(b1).isPresent());
        waitSaved(report(b2, LAT_15M_NORTH));

        assertThat(nearCount(a1, a2)).isEqualTo(1);
        assertThat(nearCount(b1, b2)).isZero();
    }

    /* 캐시에 기본값(10)을 먼저 올려 두고 바꾼다. 커밋 뒤 무효화가 없으면 60초 동안 옛 값으로 판정해 NEAR가 안 생긴다 */
    @Test
    void putTakesEffectImmediately() throws Exception {
        assertThat(resolver.forSite(1L).thresholdM()).isEqualTo(10.0);   // 캐시에 기본값

        mvc.perform(put("/api/v1/sites/1/settings/proximity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdM\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides.thresholdM").value(20.0))
                .andExpect(jsonPath("$.overrides.maxErrorM").doesNotExist())
                .andExpect(jsonPath("$.effective.thresholdM").value(20.0))
                .andExpect(jsonPath("$.effective.searchRadiusM").value(40.0));

        Long a1 = newEquipment();
        Long a2 = newEquipment();
        waitSaved(report(a1, LAT));
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.get(a1).isPresent());
        waitSaved(report(a2, LAT_15M_NORTH));
        assertThat(nearCount(a1, a2)).isEqualTo(1);
    }

    @Test
    void invalidValueIsRejectedAndNothingChanges() throws Exception {
        mvc.perform(put("/api/v1/sites/1/settings/proximity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdM\": -1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/sites/1/settings/proximity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides.thresholdM").doesNotExist())
                .andExpect(jsonPath("$.effective.thresholdM").value(10.0));
    }

    @Test
    void unknownSiteIs404() throws Exception {
        mvc.perform(get("/api/v1/sites/999/settings/proximity")).andExpect(status().isNotFound());
    }

    private long nearCount(Long x, Long y) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM proximity_event WHERE type = 'NEAR' AND equipment_a_id = ? AND equipment_b_id = ?",
                Long.class, Math.min(x, y), Math.max(x, y));
    }

    private void waitSaved(String key) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM location_event WHERE idempotency_key = ?", Long.class, key) == 1);
    }

    private String report(Long id, double lat) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": 126.9780,
                 "fixType": "RTK_FIXED", "horizontalAccuracyM": 0.05, "recordedAt": "%s"}
                """.formatted(id, lat, Instant.now());
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
