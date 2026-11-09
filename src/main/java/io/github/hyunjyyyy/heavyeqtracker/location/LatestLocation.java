package io.github.hyunjyyyy.heavyeqtracker.location;

import java.time.Instant;

public record LatestLocation(
        Long equipmentId,
        double lat,
        double lon,
        Double speedKmh,
        Double heading,
        FixType fixType,
        Double horizontalAccuracyM,
        Instant recordedAt
) {
    static LatestLocation from(LocationEvent e) {
        return new LatestLocation(
                e.getEquipment().getId(),
                e.getLat(), e.getLon(), e.getSpeedKmh(), e.getHeading(),
                e.getFixType(), e.getHorizontalAccuracyM(), e.getRecordedAt());
    }
}
