package io.github.hyunjyyyy.heavyeqtracker.location.history;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationHistoryIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void equipmentHistoryInRangeNewestFirst() throws Exception {
        Long id = newEquipment();
        Instant t0 = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        report(id, 37.561, t0);
        report(id, 37.562, t0.plusSeconds(60));
        report(id, 37.563, t0.plusSeconds(120));
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM location_event WHERE equipment_id = ?", Long.class, id) == 3);

        mvc.perform(get("/api/v1/sites/1/equipment/{id}/events", id)
                        .param("from", t0.plusSeconds(30).toString())
                        .param("to", t0.plusSeconds(150).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lat").value(37.563))
                .andExpect(jsonPath("$[1].lat").value(37.562));
    }

    private void report(Long id, double lat, Instant recordedAt) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, lat, recordedAt);
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }
}
