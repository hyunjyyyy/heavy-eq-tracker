package io.github.hyunjyyyy.heavyeqtracker.location.pipeline;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.sql.SQLException;

@Configuration
public class ConsumerErrorConfig {

    private static final int MYSQL_DEADLOCK = 1213;

    /* 이 빈이 있으면 Boot가 @KafkaListener 컨테이너에 붙인다. 없으면 간격 0으로 10번 뒤 로그만 남기고 버린다. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), retryBackOff());
        handler.addNotRetryableExceptions(JacksonException.class, EquipmentNotFoundException.class, MissingSiteIdException.class);
        /* 백오프 동안 그 파티션 전체가 멈춘다. 다른 장비와 동시에 판정하다 생긴 충돌은 다시 하면 풀리니 기다리지 않는다(#46 결정 3) */
        handler.setBackOffFunction((record, e) -> isConcurrentJudgeConflict(e) ? new FixedBackOff(0, 3) : null);
        return handler;
    }

    static ExponentialBackOff retryBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(30_000);
        return backOff;
    }

    /** MySQL 교착, 같은 쌍을 동시에 넣은 짝 상태 UNIQUE, 상대가 먼저 지운 짝 상태. 다른 중복 키(멱등키 등)는 해당 없음 */
    static boolean isConcurrentJudgeConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            // 모듈 경계 때문에 클래스를 참조하지 않고 이름으로 본다
            if (t instanceof ObjectOptimisticLockingFailureException ol && ol.getPersistentClassName() != null
                    && ol.getPersistentClassName().endsWith(".ProximityPairState")) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getErrorCode() == MYSQL_DEADLOCK) {
                return true;
            }
            if (t instanceof ConstraintViolationException cv && cv.getConstraintName() != null
                    && cv.getConstraintName().endsWith("uk_pair")) {
                return true;
            }
        }
        return false;
    }
}
