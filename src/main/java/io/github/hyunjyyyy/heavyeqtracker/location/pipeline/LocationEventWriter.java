package io.github.hyunjyyyy.heavyeqtracker.location.pipeline;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationEvent;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationSaved;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LatestLocation;
import io.github.hyunjyyyy.heavyeqtracker.location.latest.LocationRecorded;
import io.github.hyunjyyyy.heavyeqtracker.location.store.LocationEventRepository;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.time.Instant;

@Service
public class LocationEventWriter {

    private static final Logger log = LoggerFactory.getLogger(LocationEventWriter.class);

    private final EquipmentRepository equipmentRepository;
    private final LocationEventRepository eventRepository;
    private final EquipmentSiteAssignmentRepository assignments;
    private final ApplicationEventPublisher publisher;
    private final Timer sinceReceived;
    private final Timer sinceRecorded;

    public LocationEventWriter(EquipmentRepository equipmentRepository,
                               LocationEventRepository eventRepository,
                               EquipmentSiteAssignmentRepository assignments,
                               ApplicationEventPublisher publisher,
                               MeterRegistry registry) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
        this.assignments = assignments;
        this.publisher = publisher;
        this.sinceReceived = Timer.builder("tracker.location.judged.since.received")
                .description("서버가 받은 때부터 판정이 커밋될 때까지").register(registry);
        this.sinceRecorded = Timer.builder("tracker.location.judged.since.recorded")
                .description("단말이 잰 때부터 판정이 커밋될 때까지. 늦게 오기·끊김 포함").register(registry);
    }

    @Transactional
    public void save(LocationMessage m) {
        if (eventRepository.findByIdempotencyKey(m.idempotencyKey()).isPresent()) {
            log.info("duplicate message skipped: idempotencyKey={}", m.idempotencyKey());
            return;
        }

        /* 이동 API와 같은 잠금. 열린 배정 확인과 판정 사이에 이동이 끼어들지 못한다(#31) */
        Equipment equipment = equipmentRepository.findWithLockById(m.equipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(m.equipmentId()));

        LocationEvent saved = eventRepository.save(new LocationEvent(
                equipment, m.lat(), m.lon(), m.speedKmh(), m.heading(), m.fixType(),
                m.horizontalAccuracyM(), m.recordedAt(), m.receivedAt(), m.idempotencyKey()));

        /* 이동 뒤 도착한 옛 현장 위치는 기록만 한다. 판정·캐시하면 정리한 체류·쌍·GEO가 되살아난다(#31 결정 2) */
        if (!belongsToOpenAssignment(m)) {
            log.info("location of closed assignment recorded only: equipmentId={} siteId={} recordedAt={}",
                    m.equipmentId(), m.siteId(), m.recordedAt());
            return;
        }
        publisher.publishEvent(new LocationSaved(saved));                              // 동기. 같은 트랜잭션에서 판정
        publisher.publishEvent(new LocationRecorded(LatestLocation.from(saved)));      // 커밋 뒤. 캐시·GEO

        /* 판정 결과는 커밋돼야 남에게 보인다. 여기서 바로 재면 커밋 시간이 빠진다 */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Instant now = Instant.now();
                sinceReceived.record(Duration.between(m.receivedAt(), now));
                sinceRecorded.record(Duration.between(m.recordedAt(), now));
            }
        });
    }

    private boolean belongsToOpenAssignment(LocationMessage m) {
        return assignments.findByEquipmentIdAndAssignedToIsNull(m.equipmentId())
                .filter(a -> a.getSiteId().equals(m.siteId()) && !m.recordedAt().isBefore(a.getAssignedFrom()))
                .isPresent();
    }
}
