package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
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
    private final ApplicationEventPublisher publisher;

    public LocationEventWriter(EquipmentRepository equipmentRepository,
                               LocationEventRepository eventRepository,
                               ApplicationEventPublisher publisher) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
        this.publisher = publisher;
    }

    @Transactional
    public void save(LocationMessage m) {
        if (eventRepository.findByIdempotencyKey(m.idempotencyKey()).isPresent()) {
            log.info("duplicate message skipped: idempotencyKey={}", m.idempotencyKey());
            return;
        }

        Equipment equipment = equipmentRepository.findById(m.equipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(m.equipmentId()));

        LocationEvent saved = eventRepository.save(new LocationEvent(
                equipment, m.lat(), m.lon(), m.speedKmh(), m.heading(), m.fixType(),
                m.horizontalAccuracyM(), m.recordedAt(), m.receivedAt(), m.idempotencyKey()));

        publisher.publishEvent(new LocationRecorded(LatestLocation.from(saved)));
    }
}
