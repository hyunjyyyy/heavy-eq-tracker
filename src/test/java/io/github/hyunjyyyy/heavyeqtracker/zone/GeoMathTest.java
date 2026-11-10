package io.github.hyunjyyyy.heavyeqtracker.zone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoMathTest {

    // 37.50~37.51 × 127.00~127.01 사각형. 약 1.1 km × 0.9 km
    private final double[][] square = {
            {37.50, 127.00}, {37.51, 127.00}, {37.51, 127.01}, {37.50, 127.01}
    };

    @Test
    void insidePoint() {
        assertTrue(GeoMath.contains(square, 37.505, 127.005));
    }

    @Test
    void outsidePoints() {
        assertFalse(GeoMath.contains(square, 37.52, 127.005));   // 북쪽
        assertFalse(GeoMath.contains(square, 37.505, 126.99));   // 서쪽
    }

    @Test
    void halfOpenBoundary() {
        assertTrue(GeoMath.contains(square, 37.50, 127.005));    // 아랫변: 안
        assertFalse(GeoMath.contains(square, 37.51, 127.005));   // 윗변: 밖
    }

    @Test
    void concavePolygon() {
        double[][] lShape = {{0, 0}, {10, 0}, {10, 4}, {4, 4}, {4, 10}, {0, 10}};
        assertTrue(GeoMath.contains(lShape, 2, 8));
        assertFalse(GeoMath.contains(lShape, 8, 8));   // 오목하게 파인 부분
    }

    @Test
    void lonDegreeShrinksWithLatitude() {
        // 서울 위도에서 경도 0.0001° ≈ 8.8 m (평면 계산이면 11.1 m)
        double d = GeoMath.distanceMeters(37.5, 127.0, 37.5, 127.0001);
        assertEquals(8.8, d, 0.1);
    }

    @Test
    void equirectangularMatchesHaversineAtShortRange() {
        double a = GeoMath.distanceMeters(37.505, 127.005, 37.5052, 127.0053);
        double b = GeoMath.haversineMeters(37.505, 127.005, 37.5052, 127.0053);
        assertEquals(b, a, 0.01);   // 1 cm 안
    }
}
