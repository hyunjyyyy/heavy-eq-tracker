package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* 현장 설정 = 현장 값 ?? yml 기본값(항목마다). 메모리에 두고 변경 시 evict, 다른 서버의 옛 값은 TTL로 정리(#32 결정 1) */
@Component
public class ProximitySettingsResolver {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Cached(ProximityProperties props, Instant loadedAt) {
    }

    private final ProximityProperties defaults;
    private final SiteProximitySettingsRepository repository;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public ProximitySettingsResolver(ProximityProperties defaults, SiteProximitySettingsRepository repository) {
        this.defaults = defaults;
        this.repository = repository;
    }

    public ProximityProperties forSite(long siteId) {
        Instant now = Instant.now();
        Cached c = cache.get(siteId);
        if (c == null || c.loadedAt().plus(TTL).isBefore(now)) {
            c = new Cached(repository.findById(siteId).map(this::merge).orElse(defaults), now);
            cache.put(siteId, c);
        }
        return c.props();
    }

    public void evict(long siteId) {
        cache.remove(siteId);
    }

    /** 시험용. 시험마다 표를 비우는데 이 캐시는 앱과 함께 살아남는다 */
    public void clear() {
        cache.clear();
    }

    ProximityProperties merge(SiteProximitySettings s) {
        return new ProximityProperties(
                or(s.getThresholdM(), defaults.thresholdM()),
                or(s.getMaxErrorM(), defaults.maxErrorM()),
                s.getStaleAfterSeconds() != null ? Duration.ofSeconds(s.getStaleAfterSeconds()) : defaults.staleAfter(),
                or(s.getDefaultPhoneErrorM(), defaults.defaultPhoneErrorM()),
                or(s.getDefaultRtkErrorM(), defaults.defaultRtkErrorM()));
    }

    private static double or(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    ProximityProperties defaults() {
        return defaults;
    }

    /* 트랜잭션 안에서 지우면 커밋 전에 다른 스레드가 옛 값을 다시 캐시에 넣을 수 있다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onChanged(ProximitySettingsChanged changed) {
        evict(changed.siteId());
    }
}
