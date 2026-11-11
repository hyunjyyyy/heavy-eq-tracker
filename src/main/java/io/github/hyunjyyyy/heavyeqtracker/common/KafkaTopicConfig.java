package io.github.hyunjyyyy.heavyeqtracker.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String LOCATION_EVENTS = "location-events";
    /* Spring Kafka 4의 DLT 기본 이름(원래 토픽 + "-dlt"). 원래와 같은 번호 파티션으로 보내므로 파티션 수를 맞춘다. */
    public static final String LOCATION_EVENTS_DLT = LOCATION_EVENTS + "-dlt";

    @Bean
    public NewTopic locationEvents() {
        return TopicBuilder.name(LOCATION_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic locationEventsDlt() {
        return TopicBuilder.name(LOCATION_EVENTS_DLT).partitions(3).replicas(1).build();
    }
}
