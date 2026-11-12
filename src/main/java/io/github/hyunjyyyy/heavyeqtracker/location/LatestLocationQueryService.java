package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LatestLocationQueryService {

    private final LatestLocationCache cache;
    private final LocationEventRepository eventRepository;

    public LatestLocationQueryService(LatestLocationCache cache, LocationEventRepository eventRepository) {
        this.cache = cache;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public Optional<LatestLocation> latestOf(long siteId, Long equipmentId) {
        /* loc:은 장비 단위 키라 다른 현장으로 옮겨 간 장비의 위치일 수 있다. @TenantId는 DB만 막는다 */
        Optional<LatestLocation> cached = cache.get(equipmentId)
                .filter(l -> Long.valueOf(siteId).equals(l.siteId()));
        if (cached.isPresent()) {
            return cached;
        }
        Optional<LatestLocation> fromDb = eventRepository
                .findFirstByEquipmentIdOrderByRecordedAtDesc(equipmentId)
                .map(LatestLocation::from);
        fromDb.ifPresent(cache::put);   // 이 현장의 옛 위치여도 put이 recordedAt을 비교해 새 값을 덮지 않는다
        return fromDb;
    }
}
