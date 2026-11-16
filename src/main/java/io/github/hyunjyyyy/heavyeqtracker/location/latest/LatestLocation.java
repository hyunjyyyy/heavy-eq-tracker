package io.github.hyunjyyyy.heavyeqtracker.location.latest;

import io.github.hyunjyyyy.heavyeqtracker.location.FixType;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationEvent;
import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import java.time.Instant;
import org.springframework.modulith.NamedInterface;

@NamedInterface("latest")
public record LatestLocation(
        Long equipmentId,
        Long siteId,
        double lat,
        double lon,
        Double speedKmh,
        Double heading,
        FixType fixType,
        Double horizontalAccuracyM,
        Instant recordedAt,
        PositioningSource positioningSource
) {
    public static LatestLocation from(LocationEvent e) {
        return new LatestLocation(
                e.getEquipment().getId(), e.getSiteId(),
                e.getLat(), e.getLon(), e.getSpeedKmh(), e.getHeading(),
                e.getFixType(), e.getHorizontalAccuracyM(), e.getRecordedAt(),
                e.getEquipment().getPositioningSource());
    }
}
