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
    public Optional<LatestLocation> latestOf(Long equipmentId) {
        Optional<LatestLocation> cached = cache.get(equipmentId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<LatestLocation> fromDb = eventRepository
                .findFirstByEquipmentIdOrderByRecordedAtDesc(equipmentId)
                .map(LatestLocation::from);
        fromDb.ifPresent(cache::put);
        return fromDb;
    }
}
