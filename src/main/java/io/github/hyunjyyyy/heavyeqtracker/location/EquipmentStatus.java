package io.github.hyunjyyyy.heavyeqtracker.location;

import java.util.List;

public record EquipmentStatus(Long equipmentId, LatestLocation latest, List<Long> zoneIds) {
}
