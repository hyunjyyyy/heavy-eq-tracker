package io.github.hyunjyyyy.heavyeqtracker.proximity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProximityPairStateRepository extends JpaRepository<ProximityPairState, Long> {

    List<ProximityPairState> findAllByEquipmentAIdOrEquipmentBId(Long equipmentAId, Long equipmentBId);

    void deleteByEquipmentAIdAndEquipmentBId(Long equipmentAId, Long equipmentBId);
}
