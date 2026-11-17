package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

public final class ShuttleRoute implements Route {

    private final double ax, ay, bx, by, speedMps, length;
    private double traveled;

    public ShuttleRoute(double ax, double ay, double bx, double by, double speedMps) {
        this.ax = ax;
        this.ay = ay;
        this.bx = bx;
        this.by = by;
        this.speedMps = speedMps;
        this.length = Math.hypot(bx - ax, by - ay);
    }

    @Override
    public Sample next(double seconds) {
        traveled = (traveled + speedMps * seconds) % (2 * length);
        boolean back = traveled > length;
        double t = (back ? 2 * length - traveled : traveled) / length;
        double heading = back
                ? Sample.headingOf(ax - bx, ay - by)
                : Sample.headingOf(bx - ax, by - ay);
        return new Sample(ax + (bx - ax) * t, ay + (by - ay) * t, speedMps * 3.6, heading);
    }
}
