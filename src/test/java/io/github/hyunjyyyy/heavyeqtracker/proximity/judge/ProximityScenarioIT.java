package io.github.hyunjyyyy.heavyeqtracker.proximity.judge;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.ApproachScenario;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.GeoProjection;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.Route;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.Sample;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProximityScenarioIT extends IntegrationTest {

    private static final GeoProjection GEO = new GeoProjection(37.56650, 126.97800);

    @Autowired MockMvc mvc;
    @Autowired LatestLocationCache cache;
    @Autowired JdbcTemplate jdbc;

    /* 기본 임계 10 + 오차 0.05×2 = 10.1 m. 4 m까지 다가가면 NEAR 한 번, 다시 40 m로 멀어지면 CLEAR 한 번 */
    @Test
    void approachThenPartGivesOneNearThenOneClear() throws Exception {
        Long a = newEquipment();
        Long b = newEquipment();
        play(a, b, 40, 4);
        assertThat(types(a, b)).containsExactly("NEAR", "CLEAR");
    }

    /* 대조군: 12 m까지만 다가가면 아무것도 생기지 않는다 */
    @Test
    void stayingOutsideThresholdGivesNothing() throws Exception {
        Long a = newEquipment();
        Long b = newEquipment();
        play(a, b, 40, 12);
        assertThat(types(a, b)).isEmpty();
    }

    /** 초속 1 m로 다가갔다가 처음 자리로 돌아오는 한 바퀴. A 한 걸음, B 한 걸음 번갈아 */
    private void play(Long a, Long b, double startGap, double closestGap) throws Exception {
        List<Route> pair = ApproachScenario.pair(startGap, closestGap, 1);
        int steps = (int) (startGap - closestGap);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(steps + 60);
        for (int i = 1; i <= steps; i++) {
            Instant at = base.plusSeconds(i);
            report(a, pair.get(0).next(1), at);
            report(b, pair.get(1).next(1), at);
        }
    }

    private void report(Long id, Sample s, Instant at) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": %s, "lon": %s,
                 "fixType": "RTK_FIXED", "horizontalAccuracyM": 0.05, "recordedAt": "%s"}
                """.formatted(id, GEO.lat(s), GEO.lon(s), at);
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        // 판정은 상대 위치를 Redis에서 읽고, 그 값은 커밋 뒤에 바뀐다.
        // 저장만 기다리면 다음 장비가 상대의 옛 위치로 판정한다
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .until(() -> cache.get(id).map(l -> l.recordedAt().equals(at)).orElse(false));
    }

    private List<String> types(Long x, Long y) {
        return jdbc.queryForList(
                "SELECT type FROM proximity_event WHERE equipment_a_id = ? AND equipment_b_id = ? ORDER BY id",
                String.class, Math.min(x, y), Math.max(x, y));
    }
}
