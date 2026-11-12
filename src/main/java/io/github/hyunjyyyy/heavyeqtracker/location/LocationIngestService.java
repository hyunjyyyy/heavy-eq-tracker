package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignment;
import io.github.hyunjyyyy.heavyeqtracker.site.EquipmentSiteAssignmentRepository;
import org.springframework.stereotype.Service;

@Service
public class LocationIngestService {

    private final IdempotencyStore store;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentSiteAssignmentRepository assignments;
    private final LocationEventProducer producer;

    public LocationIngestService(IdempotencyStore store,
                                 EquipmentRepository equipmentRepository,
                                 EquipmentSiteAssignmentRepository assignments,
                                 LocationEventProducer producer) {
        this.store = store;
        this.equipmentRepository = equipmentRepository;
        this.assignments = assignments;
        this.producer = producer;
    }

    public LocationAccepted ingest(LocationReport report, String idempotencyKey) {
        if (!store.tryAcquire(idempotencyKey)) {
            return new LocationAccepted(idempotencyKey);
        }
        try {
            /* 받은 시각이 아니라 찍힌 시각의 현장. 끊겼다가 늦게 온 위치도 찍힌 곳의 현장으로 간다(#30 결정 1) */
            long siteId = assignments.findAt(report.equipmentId(), report.recordedAt())
                    .map(EquipmentSiteAssignment::getSiteId)
                    .orElseThrow(() -> equipmentRepository.existsById(report.equipmentId())
                            ? new NoSiteAssignmentException(report.equipmentId(), report.recordedAt())
                            : new EquipmentNotFoundException(report.equipmentId()));
            producer.publish(LocationMessage.of(report, idempotencyKey, siteId));
        } catch (RuntimeException e) {
            store.release(idempotencyKey);
            throw e;
        }
        return new LocationAccepted(idempotencyKey);
    }
}
