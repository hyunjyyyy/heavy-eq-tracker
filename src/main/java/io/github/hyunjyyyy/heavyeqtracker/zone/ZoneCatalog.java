package io.github.hyunjyyyy.heavyeqtracker.zone;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ZoneCatalog {

    private static final Logger log = LoggerFactory.getLogger(ZoneCatalog.class);

    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper;
    private final AtomicReference<List<ZoneShape>> active = new AtomicReference<>(List.of());

    public ZoneCatalog(ZoneRepository zoneRepository, ObjectMapper objectMapper) {
        this.zoneRepository = zoneRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void reload() {
        List<ZoneShape> shapes = zoneRepository.findAll().stream()
                .filter(Zone::isActive)
                .map(this::toShape)
                .toList();
        active.set(shapes);
        log.info("zone catalog loaded: {} active zones", shapes.size());
    }

    public List<ZoneShape> active() {
        return active.get();
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
