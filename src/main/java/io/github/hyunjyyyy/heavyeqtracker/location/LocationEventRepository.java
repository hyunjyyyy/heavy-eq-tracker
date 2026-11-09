package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationEventRepository extends JpaRepository<LocationEvent, Long> {
    Optional<LocationEvent> findByIdempotencyKey(String idempotencyKey);
    Optional<LocationEvent> findFirstByEquipmentIdOrderByRecordedAtDesc(Long equipmentId);
}
