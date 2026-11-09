package io.github.hyunjyyyy.heavyeqtracker.location;

import java.time.Instant;

public record LocationMessage(
        String idempotencyKey,
        Long equipmentId,
        double lat,
        double lon,
        Double speedKmh,
        Double heading,
        FixType fixType,
        Double horizontalAccuracyM,
        Instant recordedAt,
        Instant receivedAt
) {
    static LocationMessage of(LocationReport r, String idempotencyKey) {
        return new LocationMessage(idempotencyKey, r.equipmentId(), r.lat(), r.lon(),
                r.speedKmh(), r.heading(), r.fixType(), r.horizontalAccuracyM(),
                r.recordedAt(), Instant.now());
    }
}
