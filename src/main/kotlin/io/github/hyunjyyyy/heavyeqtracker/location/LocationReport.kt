package io.github.hyunjyyyy.heavyeqtracker.location

import jakarta.validation.constraints.*
import java.time.Instant

data class LocationReport(
    @NotNull val equipmentId: Long,
    @DecimalMin("-90.0") @DecimalMax("90.0") val lat: Double,
    @DecimalMin("-180.0") @DecimalMax("180.0") val lon: Double,
    @PositiveOrZero val speedKmh: Double? = null,
    @DecimalMin("0.0") @DecimalMax("360.0") val heading: Double? = null,
    @NotNull val fixType: FixType,
    @PositiveOrZero val horizontalAccuracyM: Double? = null,
    @NotNull val recordedAt: Instant,
)