package io.github.hyunjyyyy.heavyeqtracker.proximity;

public record ProximitySettingsView(ProximitySettingsRequest overrides, Effective effective) {

    public record Effective(double thresholdM, double maxErrorM, long staleAfterSeconds,
                            double defaultPhoneErrorM, double defaultRtkErrorM, double searchRadiusM) {

        static Effective of(ProximityProperties p) {
            return new Effective(p.thresholdM(), p.maxErrorM(), p.staleAfter().toSeconds(),
                    p.defaultPhoneErrorM(), p.defaultRtkErrorM(), p.searchRadiusM());
        }
    }
}
