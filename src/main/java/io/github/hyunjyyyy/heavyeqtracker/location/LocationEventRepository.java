package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface LocationEventRepository extends JpaRepository<LocationEvent, Long> {

    Optional<LocationEvent> findByIdempotencyKey(String idempotencyKey);
    @EntityGraph(attributePaths = "equipment")
    Optional<LocationEvent> findFirstByEquipmentIdOrderByRecordedAtDesc(Long equipmentId);

    @EntityGraph(attributePaths = "equipment")
    List<LocationEvent> findAllByEquipmentIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long equipmentId, Instant from, Instant to, Limit limit);

    @EntityGraph(attributePaths = "equipment")
    List<LocationEvent> findAllByOrderByRecordedAtDesc(Limit limit);
}
