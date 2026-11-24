package io.github.hyunjyyyy.heavyeqtracker.location.pipeline;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.location.FixType;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocation;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocationCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConsumerOrderIT extends IntegrationTest {

    private static final int COUNT = 50;

    @Autowired LocationEventProducer producer;
    @Autowired LatestLocationCache cache;
    @Autowired JdbcTemplate jdbc;

    /* 컨슈머 스레드가 여럿이어도 한 장비의 위치는 보낸 순서대로 저장·판정된다.
       키를 장비 ID가 아닌 값으로 바꾸면 실패하는 것을 확인했다(#46) */
    @Test
    void oneEquipmentIsProcessedInSendOrder() {
        Long id = newEquipment();
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(COUNT + 60);
        for (int i = 1; i <= COUNT; i++) {
            send(id, t.plusSeconds(i));   // 기다리지 않고 연달아
        }

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> jdbc.queryForObject(
                        "SELECT COUNT(*) FROM location_event WHERE equipment_id = ?", Integer.class, id) == COUNT);

        List<Instant> inSavedOrder = jdbc.queryForList(
                        "SELECT recorded_at FROM location_event WHERE equipment_id = ? ORDER BY id", Timestamp.class, id)
                .stream().map(Timestamp::toInstant).toList();
        assertThat(inSavedOrder).isSorted().doesNotHaveDuplicates();
        assertThat(cache.get(id)).get().extracting(LatestLocation::recordedAt).isEqualTo(t.plusSeconds(COUNT));
    }

    /* API를 거치면 장비당 초당 한도(429)에 걸린다. 키를 정하는 프로듀서부터 탄다 */
    private void send(Long id, Instant at) {
        producer.publish(new LocationMessage(UUID.randomUUID().toString(), id, 1L, 37.5665, 126.9780,
                null, null, FixType.RTK_FIXED, 0.05, at, Instant.now()));
    }
}
