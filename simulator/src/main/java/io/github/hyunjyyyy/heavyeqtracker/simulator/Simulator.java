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
        Stats stats = new Stats();
        String runId = Long.toString(System.currentTimeMillis(), 36);
        Instant end = Instant.now().plusSeconds(c.durationSec());

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int n = 1; n <= c.sites(); n++) {
                long siteId = 100 + n;
                GeoProjection geo = new GeoProjection(37.0 + n * 0.1, 127.0);
                for (int k = 1; k <= c.perSite(); k++) {
                    long equipmentId = siteId * 1000 + k;
                    var route = new RandomWalkRoute(c.seed() * 1_000_003L + equipmentId, 300, 2);
                    Device d = new Device(equipmentId, route, geo, http, c, stats, runId);
                    exec.submit(() -> d.run(end));
                }
            }
        } // close()가 모든 장비가 끝날 때까지 기다린다
        System.out.print(stats.summary(c.durationSec()));
    }
}
