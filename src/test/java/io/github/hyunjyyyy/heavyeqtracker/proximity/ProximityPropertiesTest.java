package io.github.hyunjyyyy.heavyeqtracker.proximity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProximityPropertiesTest {

    @EnableConfigurationProperties(ProximityProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "tracker.proximity.threshold-m=10",
                    "tracker.proximity.max-error-m=10",
                    "tracker.proximity.stale-after=10m",
                    "tracker.proximity.default-phone-error-m=5",
                    "tracker.proximity.default-rtk-error-m=0.05");

    @Test
    void searchRadiusIsDerivedFromThresholdAndMaxError() {
        runner.withPropertyValues("tracker.proximity.threshold-m=20")
                .run(ctx -> assertThat(ctx.getBean(ProximityProperties.class).searchRadiusM()).isEqualTo(40.0));
    }

    @Test
    void negativeThresholdFailsAtStartup() {
        runner.withPropertyValues("tracker.proximity.threshold-m=-5")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasStackTraceContaining("thresholdM"));
    }

    @Test
    void missingStaleAfterFailsAtStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("tracker.proximity.threshold-m=10")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
