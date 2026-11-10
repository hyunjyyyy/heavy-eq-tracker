package io.github.hyunjyyyy.heavyeqtracker.proximity;

import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocation;
import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ProximityService {

    private static final Logger log = LoggerFactory.getLogger(ProximityService.class);

    static final double THRESHOLD_M = 10.0;
    static final double SEARCH_RADIUS_M = 25.0;
    static final double DEFAULT_PHONE_ERROR_M = 5.0;
    static final double DEFAULT_RTK_ERROR_M = 0.05;

    private final StringRedisTemplate redis;
    private final LatestLocationCache cache;
    private final ProximityEventRepository eventRepository;
    private final ProximityPairStateRepository pairRepository;

    public ProximityService(StringRedisTemplate redis, LatestLocationCache cache,
                            ProximityEventRepository eventRepository, ProximityPairStateRepository pairRepository) {
        this.redis = redis;
        this.cache = cache;
        this.eventRepository = eventRepository;
        this.pairRepository = pairRepository;
    }

    @Transactional
    public void judge(LocationEvent event) {
        Long me = event.getEquipment().getId();
        double myError = errorOf(event.getHorizontalAccuracyM(), event.getEquipment().getPositioningSource());

        // 1. 반경 안 후보를 Redis GEO에서. 자기 위치는 아직 GEO에 없으니 좌표로 검색
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                LatestLocationCache.FLEET_KEY,
                GeoReference.fromCoordinate(event.getLon(), event.getLat()),
                new Distance(SEARCH_RADIUS_M, RedisGeoCommands.DistanceUnit.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance());

        // 2. 정확한 임계값(오차 합)으로 거른다
        Map<Long, Double> nearNow = new HashMap<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                Long other = Long.valueOf(r.getContent().getName());
                if (other.equals(me)) {
                    continue;
                }
                double distance = r.getDistance().getValue();   // METERS
                double otherError = cache.get(other)
                        .map(l -> errorOf(l.horizontalAccuracyM(), l.positioningSource()))
                        .orElse(DEFAULT_PHONE_ERROR_M);
                if (distance < THRESHOLD_M + myError + otherError) {
                    nearNow.put(other, distance);
                }
            }
        }

        // 3. 이전 상태와 비교
        Set<Long> nearBefore = new HashSet<>();
        for (ProximityPairState s : pairRepository.findAllByEquipmentAIdOrEquipmentBId(me, me)) {
            nearBefore.add(s.getEquipmentAId().equals(me) ? s.getEquipmentBId() : s.getEquipmentAId());
        }

        for (Map.Entry<Long, Double> e : nearNow.entrySet()) {
            if (!nearBefore.contains(e.getKey())) {
                long a = Math.min(me, e.getKey()), b = Math.max(me, e.getKey());
                eventRepository.save(new ProximityEvent(a, b, event.getId(), ProximityEventType.NEAR,
                        e.getValue(), event.getRecordedAt()));
                pairRepository.save(new ProximityPairState(a, b, event.getRecordedAt()));
                log.info("NEAR {}-{} distance={}m", a, b, String.format("%.2f", e.getValue()));
            }
        }
        for (Long other : nearBefore) {
            if (!nearNow.containsKey(other)) {
                long a = Math.min(me, other), b = Math.max(me, other);
                eventRepository.save(new ProximityEvent(a, b, event.getId(), ProximityEventType.CLEAR,
                        -1, event.getRecordedAt()));
                pairRepository.deleteByEquipmentAIdAndEquipmentBId(a, b);
                log.info("CLEAR {}-{}", a, b);
            }
        }
    }

    static double errorOf(Double horizontalAccuracyM, PositioningSource source) {
        if (horizontalAccuracyM != null) {
            return horizontalAccuracyM;
        }
        return source == PositioningSource.PHONE_RTK ? DEFAULT_RTK_ERROR_M : DEFAULT_PHONE_ERROR_M;
    }
}
