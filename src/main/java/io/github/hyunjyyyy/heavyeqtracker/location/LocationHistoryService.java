package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LocationHistoryService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 1000;

    private final LocationEventRepository eventRepository;

    public LocationHistoryService(LocationEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationEventView> history(Long equipmentId, Instant from, Instant to, Integer limit) {
        return eventRepository
                .findAllByEquipmentIdAndRecordedAtBetweenOrderByRecordedAtDesc(equipmentId, from, to, clamp(limit))
                .stream().map(LocationEventView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationEventView> recent(Integer limit) {
        return eventRepository.findAllByOrderByRecordedAtDesc(clamp(limit))
                .stream().map(LocationEventView::from).toList();
    }

    private static Limit clamp(Integer limit) {
        int n = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return Limit.of(n);
    }
}
