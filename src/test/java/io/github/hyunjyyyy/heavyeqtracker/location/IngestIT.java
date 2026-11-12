package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestIT extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void reportedLocationBecomesLatest() throws Exception {
        Long id = newEquipment();
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

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> mvc.perform(get("/api/v1/equipment/{id}/latest", id))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.lat").value(37.5665)));
    }
}
