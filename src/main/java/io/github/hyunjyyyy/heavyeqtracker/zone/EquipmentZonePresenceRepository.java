package io.github.hyunjyyyy.heavyeqtracker.zone;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentZonePresenceRepository extends JpaRepository<EquipmentZonePresence, Long> {

    List<EquipmentZonePresence> findAllByEquipmentId(Long equipmentId);

    void deleteByEquipmentIdAndZoneId(Long equipmentId, Long zoneId);
}
