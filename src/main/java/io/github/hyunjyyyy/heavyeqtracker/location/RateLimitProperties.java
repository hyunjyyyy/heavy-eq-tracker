package io.github.hyunjyyyy.heavyeqtracker.location;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("tracker.rate-limit")
@Validated
public record RateLimitProperties(@Positive int perSecond) {
}
