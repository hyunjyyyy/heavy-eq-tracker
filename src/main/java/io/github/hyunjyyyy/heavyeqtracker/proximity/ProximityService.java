package io.github.hyunjyyyy.heavyeqtracker.proximity;

import io.github.hyunjyyyy.heavyeqtracker.equipment.PositioningSource;
import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocation;
import io.github.hyunjyyyy.heavyeqtracker.location.LatestLocationCache;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationEvent;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationSaved;
import io.github.hyunjyyyy.heavyeqtracker.zone.GeoMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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
import java.util.Optional;
import java.util.Set;

@Service
public class ProximityService {

    private static final Logger log = LoggerFactory.getLogger(ProximityService.class);

    private final StringRedisTemplate redis;
    private final LatestLocationCache cache;
    private final ProximityEventRepository eventRepository;
    private final ProximityPairStateRepository pairRepository;
    private final ProximityProperties props;

    public ProximityService(StringRedisTemplate redis, LatestLocationCache cache,
                            ProximityEventRepository eventRepository, ProximityPairStateRepository pairRepository,
                            ProximityProperties props) {
        this.redis = redis;
        this.cache = cache;
        this.eventRepository = eventRepository;
        this.pairRepository = pairRepository;
        this.props = props;
    }

    @EventListener
    @Transactional
    public void onLocationSaved(LocationSaved saved) {
        LocationEvent event = saved.event();
        Long me = event.getEquipment().getId();
        double myError = errorOf(event.getHorizontalAccuracyM(), event.getEquipment().getPositioningSource());

        // 1. 반경 안 후보 (GEO). 자기 좌표로 검색, 자기 자신 제외
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                LatestLocationCache.fleetKey(event.getSiteId()),
                GeoReference.fromCoordinate(event.getLon(), event.getLat()),
                new Distance(props.searchRadiusM(),RedisGeoCommands.DistanceUnit.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs());

        // 2. 후보마다: 최신 위치가 있고 신선한지 → 원본 좌표로 거리 재계산 → 오차 합 임계
        Map<Long, Double> nearNow = new HashMap<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
                Long other = Long.valueOf(r.getContent().getName());
                if (other.equals(me)) {
                    continue;
                }
                Optional<LatestLocation> latest = cache.get(other);
                if (latest.isEmpty() || latest.get().recordedAt().isBefore(event.getRecordedAt().minus(props.staleAfter()))) {
                    continue;   // 유령 장비(loc 만료) 또는 오래된 위치
                }
                LatestLocation l = latest.get();
                double distance = GeoMath.distanceMeters(event.getLat(), event.getLon(), l.lat(), l.lon());
                double otherError = errorOf(l.horizontalAccuracyM(), l.positioningSource());
                if (distance < props.thresholdM() + myError + otherError) {
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
                Double distance = cache.get(other)
                        .map(l -> GeoMath.distanceMeters(event.getLat(), event.getLon(), l.lat(), l.lon()))
                        .orElse(null);
                eventRepository.save(new ProximityEvent(a, b, event.getId(), ProximityEventType.CLEAR,
                        distance, event.getRecordedAt()));
                pairRepository.deleteByEquipmentAIdAndEquipmentBId(a, b);
                log.info("CLEAR {}-{} distance={}", a, b, distance == null ? "unknown" : String.format("%.2fm", distance));
            }
        }
    }

    double errorOf(Double horizontalAccuracyM, PositioningSource source) {
        double raw = horizontalAccuracyM != null ? horizontalAccuracyM
                : (source == PositioningSource.PHONE_RTK ? props.defaultRtkErrorM() : props.defaultPhoneErrorM());
        return Math.min(raw, props.maxErrorM());
    }
}
