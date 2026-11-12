package io.github.hyunjyyyy.heavyeqtracker.status;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
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

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteQueryIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SiteRepository siteRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void unknownSiteIs404() throws Exception {
        mvc.perform(get("/api/v1/sites/999/equipment")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sites/999/events/recent")).andExpect(status().isNotFound());
    }

    @Test
    void eachSiteSeesOnlyItsOwnAndHqSeesAll() throws Exception {
        Long siteB = siteRepository.save(new Site("test-site-b")).getId();
        Long a = newEquipment();
        Long b = newEquipment(siteB);
        report(a);
        report(b);
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> mvc.perform(get("/api/v1/sites/{s}/equipment/{id}/latest", siteB, b))
                        .andExpect(status().isOk()));
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM location_event", Long.class) == 2);

        mvc.perform(get("/api/v1/sites/1/events/recent"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].equipmentId").value(a));
        mvc.perform(get("/api/v1/sites/{s}/events/recent", siteB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].equipmentId").value(b));
        mvc.perform(get("/api/v1/hq/events/recent"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/hq/sites"))
                .andExpect(jsonPath("$.length()").value(2));

        /* b의 최신 위치는 Redis loc:에 있지만 현장 B 것이라 현장 1 경로로는 안 보여야 한다 */
        mvc.perform(get("/api/v1/sites/1/equipment/{id}/latest", b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sites/1/equipment"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].equipmentId").value(a));
    }

    private void report(Long id) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": 37.5665, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, Instant.now());
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }
}
