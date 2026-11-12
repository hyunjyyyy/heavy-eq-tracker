package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignment;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.site.Site;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteIngestIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired SiteRepository siteRepository;
    @Autowired EquipmentSiteAssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reportGoesToSiteAssignedAtRecordedAtNotAtArrival() throws Exception {
        Long siteB = siteRepository.save(new Site("test-site-b")).getId();
        Long id = equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();
        Instant moved = Instant.now().minus(Duration.ofMinutes(10));
        assignments.save(new EquipmentSiteAssignment(id, 1L, moved.minus(Duration.ofDays(1)), moved));
        assignments.save(new EquipmentSiteAssignment(id, siteB, moved, null));

        String before = key();
        String after = key();
        report(id, before, moved.minusSeconds(60)).andExpect(status().isAccepted());   // 현장 1에서 찍혀 지금 도착
        report(id, after, moved.plusSeconds(60)).andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> rowsWithKeys(before, after) == 2);
        assertThat(siteOf(before)).isEqualTo(1L);
        assertThat(siteOf(after)).isEqualTo(siteB);
    }

    @Test
    void reportBeforeAnyAssignmentIs422() throws Exception {
        Long id = equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();
        Instant from = Instant.now().minus(Duration.ofMinutes(10));
        assignments.save(new EquipmentSiteAssignment(id, 1L, from, null));

        report(id, key(), from.minusSeconds(1)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownEquipmentIs404() throws Exception {
        report(999_999L, key(), Instant.now()).andExpect(status().isNotFound());
    }

    /* 리포지토리 조회엔 현재 현장 조건이 붙어 현장 B 행이 안 보인다. 그래서 JDBC로 직접 센다 */
    private long rowsWithKeys(String a, String b) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM location_event WHERE idempotency_key IN (?, ?)", Long.class, a, b);
    }

    private Long siteOf(String key) {
        return jdbc.queryForObject("SELECT site_id FROM location_event WHERE idempotency_key = ?", Long.class, key);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ResultActions report(Long id, String key, Instant recordedAt) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": 37.5665, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, recordedAt);
        return mvc.perform(post("/api/v1/locations")
                .header("Idempotency-Key", key)
                .header("X-Equipment-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
