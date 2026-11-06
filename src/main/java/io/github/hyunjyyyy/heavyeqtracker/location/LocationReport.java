package io.github.hyunjyyyy.heavyeqtracker.location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record LocationReport(
        @NotNull Long equipmentId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
        @PositiveOrZero Double speedKmh,
        @DecimalMin("0.0") @DecimalMax("360.0") Double heading,
        @NotNull FixType fixType,
        @PositiveOrZero Double horizontalAccuracyM,
        @NotNull Instant recordedAt
) {
}
