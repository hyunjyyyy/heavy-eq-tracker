package io.github.hyunjyyyy.heavyeqtracker.zone;

public final class GeoMath {

    private GeoMath() {
    }

    /**
     * ray casting. polygon[i] = {lat, lon}. 오른쪽 수평 반직선과 변의 교차 횟수가 홀수면 안.
     * 위·오른쪽 경계는 밖, 아래·왼쪽 경계는 안(반열림).
     */
    public static boolean contains(double[][] polygon, double lat, double lon) {
        boolean inside = false;
        int n = polygon.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = polygon[i][0], xi = polygon[i][1];
            double yj = polygon[j][0], xj = polygon[j][1];
            boolean crossesLatitude = (yi > lat) != (yj > lat);
            if (crossesLatitude) {
                double lonAtLat = (xj - xi) * (lat - yi) / (yj - yi) + xi;
                if (lon < lonAtLat) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
