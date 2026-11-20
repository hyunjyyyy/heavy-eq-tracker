package io.github.hyunjyyyy.heavyeqtracker.simulator.route;

public record GeoProjection(double centerLat, double centerLon) {

    private static final double M_PER_DEG = 111_320;

    public double lat(Sample s) {
        return centerLat + s.northM() / M_PER_DEG;
    }

    public double lon(Sample s) {
        return centerLon + s.eastM() / (M_PER_DEG * Math.cos(Math.toRadians(centerLat)));
    }
}
