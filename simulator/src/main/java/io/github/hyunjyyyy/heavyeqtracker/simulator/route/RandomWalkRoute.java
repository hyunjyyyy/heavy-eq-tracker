package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

import java.util.Random;

public final class RandomWalkRoute implements Route {

    private final Random random;
    private final double radiusM, speedMps;
    private double x, y, heading;

    /** 장비마다 따로 만든다. 스레드끼리 나눠 쓰면 시드가 같아도 경로가 달라진다 */
    public RandomWalkRoute(long seed, double radiusM, double speedMps) {
        this.random = new Random(seed);
        this.radiusM = radiusM;
        this.speedMps = speedMps;
        this.x = random.nextDouble(-radiusM / 2, radiusM / 2);
        this.y = random.nextDouble(-radiusM / 2, radiusM / 2);
        this.heading = random.nextDouble(0, 360);
    }

    @Override
    public Sample next(double seconds) {
        heading = Math.hypot(x, y) > radiusM
                ? Sample.headingOf(-x, -y)
                : (heading + random.nextDouble(-30, 30) + 360) % 360;
        double d = speedMps * seconds;
        x += d * Math.sin(Math.toRadians(heading));
        y += d * Math.cos(Math.toRadians(heading));
        return new Sample(x, y, speedMps * 3.6, heading);
    }
}
