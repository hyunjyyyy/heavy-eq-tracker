package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.common.KafkaTopicConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LocationEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 브로커 ack를 기다린다. 실패하면 RuntimeException(CompletionException) */
    public void publish(LocationMessage message) {
        String key = String.valueOf(message.equipmentId());
        String value = objectMapper.writeValueAsString(message);
        kafkaTemplate.send(KafkaTopicConfig.LOCATION_EVENTS, key, value).join();
    }
}
