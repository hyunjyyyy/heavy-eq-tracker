package io.github.hyunjyyyy.heavyeqtracker.proximity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("tracker.proximity")
@Validated
public record ProximityProperties(
        @Positive double thresholdM,
        @Positive double maxErrorM,
        @NotNull @DurationMin(seconds = 1) Duration staleAfter,
        @Positive double defaultPhoneErrorM,
        @Positive double defaultRtkErrorM) {

    /* 설정 키로 두지 않는다. 따로 두면 임계 + 오차 합이 반경을 넘는 설정이 가능해진다(#24). */
    public double searchRadiusM() {
        return thresholdM + 2 * maxErrorM;
    }
}
