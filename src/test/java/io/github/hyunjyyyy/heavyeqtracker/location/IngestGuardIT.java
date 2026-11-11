package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestGuardIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired LocationEventRepository eventRepository;

    @Test
    void sameKeyTwiceSavesOnce() throws Exception {
        Long id = newEquipment();
        String key = UUID.randomUUID().toString();
        String marker = UUID.randomUUID().toString();

        report(id, key).andExpect(status().isAccepted());
        report(id, key).andExpect(status().isAccepted());
        report(id, marker).andExpect(status().isAccepted());

        /* 같은 장비 = 같은 파티션이라 보낸 순서대로 저장된다. 표지가 저장됐을 때 2행이면 중복은 저장되지 않은 것 */
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> eventRepository.findByIdempotencyKey(marker).isPresent());
        assertThat(eventRepository.count()).isEqualTo(2);
    }

    @Test
    void eleventhRequestInOneSecondIs429() throws Exception {
        Long id = newEquipment();
        Thread.sleep(1000 - System.currentTimeMillis() % 1000);   // 한도 창이 epoch 초라서 초 경계 직후에 시작

        for (int i = 0; i < 10; i++) {
            report(id, UUID.randomUUID().toString()).andExpect(status().isAccepted());
        }
        report(id, UUID.randomUUID().toString())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        /* 202 10건이 저장될 때까지 기다린다. 남기고 끝내면 다음 시험의 비우기 뒤에 들어온다 */
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> eventRepository.count() == 10);
    }

    private Long newEquipment() {
        return equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();
    }

    private ResultActions report(Long id, String key) throws Exception {
        String body = """
                {"equipmentId": %d, "lat": 37.5665, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, Instant.now());
        return mvc.perform(post("/api/v1/locations")
                .header("Idempotency-Key", key)
                .header("X-Equipment-Id", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
