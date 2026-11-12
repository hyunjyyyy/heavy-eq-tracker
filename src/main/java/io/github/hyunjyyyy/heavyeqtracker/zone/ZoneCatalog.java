package io.github.hyunjyyyy.heavyeqtracker.zone;

import io.github.hyunjyyyy.heavyeqtracker.site.SiteContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class ZoneCatalog {

    private static final Logger log = LoggerFactory.getLogger(ZoneCatalog.class);

    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<Long, List<ZoneShape>>> active = new AtomicReference<>(Map.of());

    public ZoneCatalog(ZoneRepository zoneRepository, ObjectMapper objectMapper) {
        this.zoneRepository = zoneRepository;
        this.objectMapper = objectMapper;
    }

    /* 전 현장 구역을 읽어야 해서 현장 조건 없이(root) 읽는다. 기동 때는 현재 현장이 없다 */
    @PostConstruct
    public void reload() {
        List<Zone> zones = SiteContext.call(SiteContext.ROOT, zoneRepository::findAll);
        Map<Long, List<ZoneShape>> bySite = zones.stream()
                .filter(Zone::isActive)
                .collect(Collectors.groupingBy(Zone::getSiteId,
                        Collectors.mapping(this::toShape, Collectors.toList())));
        active.set(bySite);
        log.info("zone catalog loaded: {} sites, {} active zones",
                bySite.size(), bySite.values().stream().mapToInt(List::size).sum());
    }

    public List<ZoneShape> active(long siteId) {
        return active.get().getOrDefault(siteId, List.of());
    }

    private ZoneShape toShape(Zone zone) {
        double[][] polygon = objectMapper.readValue(zone.getPolygon(), double[][].class);
        if (polygon.length < 3) {
            throw new IllegalStateException("zone " + zone.getId() + ": polygon needs >= 3 points");
        }
        for (double[] p : polygon) {
            if (p.length != 2 || p[0] < -90 || p[0] > 90 || p[1] < -180 || p[1] > 180) {
                throw new IllegalStateException("zone " + zone.getId() + ": bad point " + java.util.Arrays.toString(p)
                        + " (expected [lat, lon])");
            }
        }
        return new ZoneShape(zone.getId(), zone.getName(), zone.getKind(), polygon);
    }
}
