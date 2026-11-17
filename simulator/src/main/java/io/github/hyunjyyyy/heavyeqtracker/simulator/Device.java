package io.github.hyunjyyyy.heavyeqtracker.simulator;

import io.github.hyunjyyyy.heavyeqtracker.simulator.route.GeoProjection;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.Route;
import io.github.hyunjyyyy.heavyeqtracker.simulator.route.Sample;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

final class Device {

    private final long id;
    private final Route route;
    private final GeoProjection geo;
    private final HttpClient http;
    private final Config config;
    private final Stats stats;
    private final String runId;

    Device(long id, Route route, GeoProjection geo, HttpClient http, Config config, Stats stats, String runId) {
        this.id = id;
        this.route = route;
        this.geo = geo;
        this.http = http;
        this.config = config;
        this.stats = stats;
        this.runId = runId;
    }

    void run(Instant end) {
        long period = config.periodMs();
        // 모든 장비가 같은 순간에 보내지 않게 시작을 흩는다
        if (!sleep(Math.floorMod(id * 7919, period))) return;
        long seq = 0;
        while (Instant.now().isBefore(end)) {
            send(route.next(period / 1000.0), seq++);
            if (!sleep(period)) return;
        }
    }

    private void send(Sample s, long seq) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/v1/locations"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                // runId가 없으면 다시 실행할 때 키가 겹쳐 서버가 중복으로 버린다
                .header("Idempotency-Key", runId + "-" + id + "-" + seq)
                .header("X-Equipment-Id", String.valueOf(id))
                .POST(HttpRequest.BodyPublishers.ofString(json(s)))
                .build();
        long t0 = System.nanoTime();
        try {
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            stats.record(res.statusCode(), System.nanoTime() - t0);
        } catch (IOException e) {
            stats.failure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String json(Sample s) {
        // Locale.ROOT: 소수점이 쉼표인 로케일에서 JSON이 깨지지 않게
        return String.format(Locale.ROOT,
                "{\"equipmentId\":%d,\"lat\":%.7f,\"lon\":%.7f,\"speedKmh\":%.2f,\"heading\":%.1f,"
                        + "\"fixType\":\"RTK_FIXED\",\"horizontalAccuracyM\":0.05,\"recordedAt\":\"%s\"}",
                id, geo.lat(s), geo.lon(s), s.speedKmh(), s.headingDeg(), Instant.now());
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
