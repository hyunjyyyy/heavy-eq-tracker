package io.github.hyunjyyyy.heavyeqtracker.zone;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeofenceIT extends IntegrationTest {

    /* 서울시청 주변 약 260 m × 350 m 사각형. 점 순서는 [lat, lon] */
    private static final String SQUARE =
            "[[37.565,126.976],[37.565,126.980],[37.568,126.980],[37.568,126.976]]";

    @Autowired MockMvc mvc;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired ZoneCatalog zoneCatalog;
    @Autowired GeofenceEventRepository geofenceEventRepository;
    @Autowired EquipmentZonePresenceRepository presenceRepository;
    @Autowired LocationEventRepository locationEventRepository;

    @Test
    void outInOutRecordsEnterThenExit() throws Exception {
        Long zoneId = zoneRepository.save(new Zone("test-site", ZoneKind.WORK, SQUARE)).getId();
        zoneCatalog.reload();
        Long equipmentId = newEquipment();

        Instant t = Instant.now();
        report(equipmentId, 37.5700, 126.9780, t);                                // 밖 (북쪽 약 200 m)
        report(equipmentId, 37.5665, 126.9780, t.plusSeconds(1));                // 안
        String last = report(equipmentId, 37.5700, 126.9780, t.plusSeconds(2));  // 밖

        /* 판정은 위치 저장과 같은 트랜잭션이라, 마지막 위치가 보이면 판정도 끝난 것 */
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> locationEventRepository.findByIdempotencyKey(last).isPresent());

        List<GeofenceEvent> events = geofenceEventRepository.findAll(Sort.by("id"));
        assertThat(events)
                .extracting(GeofenceEvent::getEquipmentId, GeofenceEvent::getZoneId, GeofenceEvent::getType)
                .containsExactly(
                        tuple(equipmentId, zoneId, GeofenceEventType.ENTER),
                        tuple(equipmentId, zoneId, GeofenceEventType.EXIT));
        assertThat(presenceRepository.findAllByEquipmentId(equipmentId)).isEmpty();
    }

    private String report(Long equipmentId, double lat, double lon, Instant recordedAt) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": %s,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(equipmentId, lat, lon, recordedAt);
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
