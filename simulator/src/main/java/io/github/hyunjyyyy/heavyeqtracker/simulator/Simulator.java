package io.github.hyunjyyyy.heavyeqtracker.simulator;

import io.github.hyunjyyyy.heavyeqtracker.simulator.route.GeoProjection;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.RandomWalkRoute;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public final class Simulator {

    public static void main(String[] args) {
        Config c = Config.parse(args);
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        String runId = Long.toString(System.currentTimeMillis(), 36);
        System.out.println("runId " + runId);
        Instant from = Instant.now().plusSeconds(c.warmupSec());
        Instant end = from.plusSeconds(c.durationSec());
        Stats stats = new Stats(from.toEpochMilli());
        // Prometheus를 이 구간으로 잘라 쿼리한다
        System.out.println("measure " + from + " ~ " + end + " end=" + end.getEpochSecond());

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int n = 1; n <= c.sites(); n++) {
                long siteId = 100 + n;
                GeoProjection geo = new GeoProjection(37.0 + n * 0.1, 127.0);
                for (int k = 1; k <= c.perSite(); k++) {
                    long equipmentId = siteId * 1000 + k;
                    var route = new RandomWalkRoute(c.seed() * 1_000_003L + equipmentId, c.radiusM(), 2);
                    Device d = new Device(equipmentId, route, geo, http, c, stats, runId);
                    exec.submit(() -> d.run(end));
                }
            }
        } // close()가 모든 장비가 끝날 때까지 기다린다
        System.out.print(stats.summary(c.durationSec()));
    }
}
