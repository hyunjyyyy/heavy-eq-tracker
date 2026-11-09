package io.github.hyunjyyyy.heavyeqtracker.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String LOCATION_EVENTS = "location-events";

    @Bean
    public NewTopic locationEvents() {
        return TopicBuilder.name(LOCATION_EVENTS).partitions(3).replicas(1).build();
    }
}
