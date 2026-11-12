package io.github.hyunjyyyy.heavyeqtracker.location;

import java.time.Instant;

public record LocationEventView(
        Long id,
        Long equipmentId,
        Long siteId,
        String equipmentName,
        double lat,
        double lon,
        Double speedKmh,
        Double heading,
        FixType fixType,
        Double horizontalAccuracyM,
        Instant recordedAt,
        Instant receivedAt
) {
    static LocationEventView from(LocationEvent e) {
        return new LocationEventView(
                e.getId(),
                e.getEquipment().getId(),
                e.getSiteId(),
                e.getEquipment().getName(),       // ← LAZY 로딩이 걸리는 줄. N+1의 현장
                e.getLat(), e.getLon(), e.getSpeedKmh(), e.getHeading(),
                e.getFixType(), e.getHorizontalAccuracyM(),
                e.getRecordedAt(), e.getReceivedAt());
    }
}
