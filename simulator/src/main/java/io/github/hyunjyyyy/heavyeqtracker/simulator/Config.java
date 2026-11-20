package io.github.hyunjyyyy.heavyeqtracker.simulator;

import java.util.HashMap;
import java.util.Map;

/** 실행 인자 key=value. sites ≤ 10, perSite ≤ 100 (dev/seed.sql이 넣은 만큼)
 dropMs는 장비당 속도 제한(10/초)보다 적게 쌓이도록 짧게 둔다. 넘으면 몰아 보낼 때 429 */
record Config(String baseUrl, int sites, int perSite, long periodMs, long durationSec, long seed,
                double radiusM, double lateRate, long lateMs, double resendRate, double dropRate, long dropMs,
                long warmupSec) {

    static Config parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (String a : args) {
            String[] kv = a.split("=", 2);
            m.put(kv[0], kv[1]);
        }
        return new Config(
                m.getOrDefault("baseUrl", "http://localhost:8080"),
                Integer.parseInt(m.getOrDefault("sites", "1")),
                Integer.parseInt(m.getOrDefault("perSite", "10")),
                Long.parseLong(m.getOrDefault("periodMs", "1000")),
                Long.parseLong(m.getOrDefault("durationSec", "30")),
                Long.parseLong(m.getOrDefault("seed", "42")),
                Double.parseDouble(m.getOrDefault("radiusM", "300")),
                Double.parseDouble(m.getOrDefault("lateRate", "0")),
                Long.parseLong(m.getOrDefault("lateMs", "3000")),
                Double.parseDouble(m.getOrDefault("resendRate", "0")),
                Double.parseDouble(m.getOrDefault("dropRate", "0")),
                Long.parseLong(m.getOrDefault("dropMs", "5000")),
                Long.parseLong(m.getOrDefault("warmupSec", "0")));
    }
}
