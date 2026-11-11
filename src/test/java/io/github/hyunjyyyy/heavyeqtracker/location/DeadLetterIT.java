package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.IntegrationTest;
import io.github.hyunjyyyy.heavyeqtracker.common.KafkaTopicConfig;
import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentType;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.backoff.BackOffExecution;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeadLetterIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ConsumerFactory<?, ?> consumerFactory;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired LocationEventRepository eventRepository;

    @Test
    @SuppressWarnings("unchecked")
    void brokenJsonGoesToDltWithoutRetryAndPartitionKeepsFlowing() throws Exception {
        Long id = equipmentRepository
                .save(new Equipment("test-excavator", EquipmentType.EXCAVATOR, PositioningSource.PHONE))
                .getId();

        kafka.send(KafkaTopicConfig.LOCATION_EVENTS, String.valueOf(id), "{broken").join();   // API를 거치지 않은 독
        String next = report(id);                                                              // 같은 키 = 같은 파티션, 독 뒤에 줄 섬

        /* 재시도했다면 35초가 걸린다. 10초 안에 오면 재시도 없이 보낸 것 */
        List<ConsumerRecord<String, String>> dlt = new ArrayList<>();
        try (Consumer<String, String> c = (Consumer<String, String>) consumerFactory
                .createConsumer("dlt-test-" + UUID.randomUUID(), null)) {
            c.subscribe(List.of(KafkaTopicConfig.LOCATION_EVENTS_DLT));
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> {
                        c.poll(Duration.ofMillis(200)).forEach(dlt::add);
                        return !dlt.isEmpty();
                    });
        }

        ConsumerRecord<String, String> dead = dlt.get(0);
        assertThat(dead.value()).isEqualTo("{broken");
        assertThat(new String(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).value(),
                StandardCharsets.UTF_8)).startsWith("tools.jackson");

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> eventRepository.findByIdempotencyKey(next).isPresent());
    }

    @Test
    void retryBackOffIsOneTwoFourEightTenTenThenStop() {
        BackOffExecution e = ConsumerErrorConfig.retryBackOff().start();
        List<Long> intervals = new ArrayList<>();
        for (long n = e.nextBackOff(); n != BackOffExecution.STOP; n = e.nextBackOff()) {
            intervals.add(n);
        }
        assertThat(intervals).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L);
    }

    private String report(Long id) throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"equipmentId": %d, "lat": 37.5665, "lon": 126.9780,
                 "fixType": "SINGLE", "horizontalAccuracyM": 3.0, "recordedAt": "%s"}
                """.formatted(id, Instant.now());
        mvc.perform(post("/api/v1/locations")
                        .header("Idempotency-Key", key)
                        .header("X-Equipment-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        return key;
    }
}
