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

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 등장방형 근사. 수 km 안에서 haversine과 cm 차이, 삼각함수 1번 */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2);
        double x = Math.toRadians(lon2 - lon1) * Math.cos((phi1 + phi2) / 2);
        double y = phi2 - phi1;
        return Math.sqrt(x * x + y * y) * EARTH_RADIUS_M;
    }

    /** haversine. 비교·시험용 */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1, dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
