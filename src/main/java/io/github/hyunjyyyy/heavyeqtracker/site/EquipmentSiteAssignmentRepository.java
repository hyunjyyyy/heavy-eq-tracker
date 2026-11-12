package io.github.hyunjyyyy.heavyeqtracker.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface EquipmentSiteAssignmentRepository extends JpaRepository<EquipmentSiteAssignment, Long> {

    /** at 시점의 배정. 시작은 포함, 끝은 미포함 */
    @Query("""
            select a from EquipmentSiteAssignment a
            where a.equipmentId = :equipmentId
              and a.assignedFrom <= :at
              and (a.assignedTo is null or a.assignedTo > :at)
            """)
    Optional<EquipmentSiteAssignment> findAt(Long equipmentId, Instant at);
}
