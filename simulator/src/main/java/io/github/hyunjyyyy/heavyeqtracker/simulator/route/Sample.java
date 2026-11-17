package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

/** 현장 중심에서 동쪽·북쪽으로 몇 m. heading은 북 0°, 시계 방향 */
public record Sample(double eastM, double northM, double speedKmh, double headingDeg) {

    static double headingOf(double dEast, double dNorth) {
        return (Math.toDegrees(Math.atan2(dEast, dNorth)) + 360) % 360;
    }
}
