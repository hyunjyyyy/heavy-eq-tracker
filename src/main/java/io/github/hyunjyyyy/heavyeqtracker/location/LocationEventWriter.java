package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class LocationEventWriter {

    private final EquipmentRepository equipmentRepository;
    private final LocationEventRepository eventRepository;
    private final ApplicationEventPublisher publisher;

    public LocationEventWriter(EquipmentRepository equipmentRepository,
                               LocationEventRepository eventRepository,
                               ApplicationEventPublisher publisher) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
        this.publisher = publisher;
    }

    @Transactional
    public IngestResult save(LocationReport report, String idempotencyKey) {
        var existing = eventRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new IngestResult(new LocationAck(existing.get().getId()), false);
        }

        Equipment equipment = equipmentRepository.findById(report.equipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(report.equipmentId()));

        LocationEvent saved = eventRepository.save(new LocationEvent(
                equipment,
                report.lat(),
                report.lon(),
                report.speedKmh(),
                report.heading(),
                report.fixType(),
                report.horizontalAccuracyM(),
                report.recordedAt(),
                idempotencyKey
        ));
        publisher.publishEvent(new LocationRecorded(LatestLocation.from(saved)));
        return new IngestResult(new LocationAck(saved.getId()), true);
    }
}
