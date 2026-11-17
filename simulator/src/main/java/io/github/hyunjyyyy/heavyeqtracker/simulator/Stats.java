package io.github.hyunjyyyy.heavyeqtracker.simulator;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

final class Stats {

    private final long fromMs;
    private final Map<Integer, LongAdder> byStatus = new ConcurrentHashMap<>();
    private final LongAdder failures = new LongAdder();
    private final ConcurrentLinkedQueue<Long> nanos = new ConcurrentLinkedQueue<>();
    private final LongAdder late = new LongAdder();
    private final LongAdder resent = new LongAdder();
    private final LongAdder offline = new LongAdder();

    /** fromMs 전(예열)에 생긴 일은 세지 않는다 */
    Stats(long fromMs) {
        this.fromMs = fromMs;
    }

    private boolean measuring() {
        return System.currentTimeMillis() >= fromMs;
    }

    void record(int status, long elapsedNanos) {
        if (!measuring()) return;
        byStatus.computeIfAbsent(status, k -> new LongAdder()).increment();
        nanos.add(elapsedNanos);
    }

    void failure() {
        if (!measuring()) return;
        failures.increment();
    }

    void late() {
        if (!measuring()) return;
        late.increment();
    }

    void resent() {
        if (!measuring()) return;
        resent.increment();
    }

    void offline() {
        if (!measuring()) return;
        offline.increment();
    }

    String summary(long seconds) {
        long[] l = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
        long sent = l.length + failures.sum();
        StringBuilder sb = new StringBuilder();
        sb.append("sent ").append(sent).append(" (").append(sent / seconds).append("/s)\n");
        byStatus.forEach((code, n) -> sb.append("  ").append(code).append(": ").append(n.sum()).append('\n'));
        sb.append("  connect/timeout failures: ").append(failures.sum()).append('\n');
        sb.append("  late ").append(late.sum()).append(", resent ").append(resent.sum())
                .append(", offline ").append(offline.sum()).append('\n');
        if (l.length > 0) {
            sb.append(String.format(Locale.ROOT, "response ms p50 %.1f / p95 %.1f / p99 %.1f / max %.1f%n",
                    ms(l, 0.50), ms(l, 0.95), ms(l, 0.99), l[l.length - 1] / 1e6));
        }
        return sb.toString();
    }

    private static double ms(long[] sorted, double q) {
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))] / 1e6;
    }
}
