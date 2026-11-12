package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.common.KafkaTopicConfig;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LocationEventConsumer.class);

    private final LocationEventWriter writer;
    private final ObjectMapper objectMapper;

    public LocationEventConsumer(LocationEventWriter writer, ObjectMapper objectMapper) {
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.LOCATION_EVENTS)
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("consume partition={} offset={} key={}", record.partition(), record.offset(), record.key());
        LocationMessage message = objectMapper.readValue(record.value(), LocationMessage.class);
        if (message.siteId() == null) {
            throw new MissingSiteIdException(record.key());
        }
        /* writer.save가 트랜잭션(세션)을 열기 전에 현장을 정한다. 세션이 열린 뒤엔 바꿀 수 없다 */
        SiteContext.run(message.siteId(), () -> writer.save(message));
    }
}
