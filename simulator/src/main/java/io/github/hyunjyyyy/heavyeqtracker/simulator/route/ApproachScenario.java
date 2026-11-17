package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

import java.util.List;

public final class ApproachScenario {

    private ApproachScenario() {
    }

    /** 동서로 startGapM 떨어져 출발 → closestGapM까지 다가갔다가 다시 멀어진다 */
    public static List<Route> pair(double startGapM, double closestGapM, double speedMps) {
        return List.of(
                new ShuttleRoute(-startGapM / 2, 0, -closestGapM / 2, 0, speedMps),
                new ShuttleRoute(startGapM / 2, 0, closestGapM / 2, 0, speedMps));
    }
}
