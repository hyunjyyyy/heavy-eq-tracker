package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class ConsumerErrorConfig {

    /* 이 빈이 있으면 Boot가 @KafkaListener 컨테이너에 붙인다. 없으면 간격 0으로 10번 뒤 로그만 남기고 버린다. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), retryBackOff());
        handler.addNotRetryableExceptions(JacksonException.class, EquipmentNotFoundException.class);
        return handler;
    }

    static ExponentialBackOff retryBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(30_000);
        return backOff;
    }
}
