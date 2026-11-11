package io.github.hyunjyyyy.heavyeqtracker.status;

import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocation;

import java.util.List;

public record EquipmentStatus(Long equipmentId, LatestLocation latest, List<Long> zoneIds) {
}
