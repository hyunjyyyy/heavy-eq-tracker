package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationEventWriter {

    private final EquipmentRepository equipmentRepository;
    private final LocationEventRepository eventRepository;

    public LocationEventWriter(EquipmentRepository equipmentRepository,
                               LocationEventRepository eventRepository) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
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

        return new IngestResult(new LocationAck(saved.getId()), true);
    }
}
