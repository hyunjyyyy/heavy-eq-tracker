package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class LatestLocationRebuilder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LatestLocationRebuilder.class);
    private static final Duration WINDOW = Duration.ofHours(24);     // loc: TTL과 같게. 더 오래된 장비는 유령이 된다
    private static final String LEGACY_FLEET_KEY = "fleet";          // #30 전의 현장 구분 없는 키

    private final EquipmentRepository equipmentRepository;
    private final LocationEventRepository eventRepository;
    private final LatestLocationCache cache;
    private final StringRedisTemplate redis;

    public LatestLocationRebuilder(EquipmentRepository equipmentRepository, LocationEventRepository eventRepository,
                                   LatestLocationCache cache, StringRedisTemplate redis) {
        this.equipmentRepository = equipmentRepository;
        this.eventRepository = eventRepository;
        this.cache = cache;
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        rebuild();
    }

    /* Redis는 DB 사본이라 비면 여기서 다시 채운다. put이 recordedAt을 비교하므로 컨슈머의 실시간 갱신과 겹쳐도
       옛 값이 새 값을 덮지 않는다. 장비마다 1번씩 묻는다(장비 수천 대로 기동이 느려지면 한 번에 묻는 쿼리로). */
    public int rebuild() {
        redis.delete(LEGACY_FLEET_KEY);
        Instant since = Instant.now().minus(WINDOW);
        List<LocationEvent> latest = SiteContext.call(SiteContext.ROOT, () -> equipmentRepository.findAll().stream()
                .map(eq -> eventRepository.findFirstByEquipmentIdOrderByRecordedAtDesc(eq.getId()))
                .flatMap(Optional::stream)
                .filter(e -> !e.getRecordedAt().isBefore(since))
                .toList());
        latest.forEach(e -> cache.put(LatestLocation.from(e)));
        log.info("latest-location cache rebuilt: {} equipment since {}", latest.size(), since);
        return latest.size();
    }
}
