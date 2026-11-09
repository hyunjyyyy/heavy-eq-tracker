package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import org.springframework.stereotype.Service;

@Service
public class LocationIngestService {

    private final IdempotencyStore store;
    private final EquipmentRepository equipmentRepository;
    private final LocationEventProducer producer;

    public LocationIngestService(IdempotencyStore store,
                                 EquipmentRepository equipmentRepository,
                                 LocationEventProducer producer) {
        this.store = store;
        this.equipmentRepository = equipmentRepository;
        this.producer = producer;
    }

    public LocationAccepted ingest(LocationReport report, String idempotencyKey) {
        if (!store.tryAcquire(idempotencyKey)) {
            return new LocationAccepted(idempotencyKey);   // 재전송: 같은 응답
        }
        try {
            if (!equipmentRepository.existsById(report.equipmentId())) {
                throw new EquipmentNotFoundException(report.equipmentId());
            }
            producer.publish(LocationMessage.of(report, idempotencyKey));
        } catch (RuntimeException e) {
            store.release(idempotencyKey);
            throw e;
        }
        return new LocationAccepted(idempotencyKey);
    }
}
