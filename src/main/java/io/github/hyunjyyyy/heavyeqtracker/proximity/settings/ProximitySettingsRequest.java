package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

import jakarta.validation.constraints.Positive;

/** 현장 덮어쓰기 값. null = yml 기본값 따름 */
public record ProximitySettingsRequest(
        @Positive Double thresholdM,
        @Positive Double maxErrorM,
        @Positive Integer staleAfterSeconds,
        @Positive Double defaultPhoneErrorM,
        @Positive Double defaultRtkErrorM) {
}
