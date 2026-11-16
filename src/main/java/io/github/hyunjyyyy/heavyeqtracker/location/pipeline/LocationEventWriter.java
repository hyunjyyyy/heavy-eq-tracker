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

@Service
public class LocationEventWriter {

    private static final Logger log = LoggerFactory.getLogger(LocationEventWriter.class);

    private final EquipmentRepository equipmentRepository;
    private final LocationEventRepository eventRepository;
    private final EquipmentSiteAssignmentRepository assignments;
    private final ApplicationEventPublisher publisher;

    public LocationEventWriter(EquipmentRepository equipmentRepository,
                               LocationEventRepository eventRepository,
                               EquipmentSiteAssignmentRepository assignments,
                               ApplicationEventPublisher publisher) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
        this.assignments = assignments;
        this.publisher = publisher;
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
    }

    private boolean belongsToOpenAssignment(LocationMessage m) {
        return assignments.findByEquipmentIdAndAssignedToIsNull(m.equipmentId())
                .filter(a -> a.getSiteId().equals(m.siteId()) && !m.recordedAt().isBefore(a.getAssignedFrom()))
                .isPresent();
    }
}
