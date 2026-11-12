package io.github.hyunjyyyy.heavyeqtracker.zone;

import io.github.hyunjyyyy.heavyeqtracker.location.LocationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationSaved;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    private final ZoneCatalog catalog;
    private final GeofenceEventRepository eventRepository;
    private final EquipmentZonePresenceRepository presenceRepository;

    public GeofenceService(ZoneCatalog catalog,
                           GeofenceEventRepository eventRepository,
                           EquipmentZonePresenceRepository presenceRepository) {
        this.catalog = catalog;
        this.eventRepository = eventRepository;
        this.presenceRepository = presenceRepository;
    }

    @EventListener
    @Transactional
    public void onLocationSaved(LocationSaved saved) {
        LocationEvent event = saved.event();
        Long equipmentId = event.getEquipment().getId();

        Set<Long> now = catalog.active(event.getSiteId()).stream()
                .filter(z -> GeoMath.contains(z.polygon(), event.getLat(), event.getLon()))
                .map(ZoneShape::id)
                .collect(Collectors.toSet());

        Set<Long> before = presenceRepository.findAllByEquipmentId(equipmentId).stream()
                .map(EquipmentZonePresence::getZoneId)
                .collect(Collectors.toSet());

        Set<Long> entered = new HashSet<>(now);
        entered.removeAll(before);
        Set<Long> exited = new HashSet<>(before);
        exited.removeAll(now);

        for (Long zoneId : entered) {
            eventRepository.save(new GeofenceEvent(equipmentId, zoneId, event.getId(),
                    GeofenceEventType.ENTER, event.getRecordedAt()));
            presenceRepository.save(new EquipmentZonePresence(equipmentId, zoneId, event.getRecordedAt()));
            log.info("ENTER equipment={} zone={}", equipmentId, zoneId);
        }
        for (Long zoneId : exited) {
            eventRepository.save(new GeofenceEvent(equipmentId, zoneId, event.getId(),
                    GeofenceEventType.EXIT, event.getRecordedAt()));
            presenceRepository.deleteByEquipmentIdAndZoneId(equipmentId, zoneId);
            log.info("EXIT equipment={} zone={}", equipmentId, zoneId);
        }
    }
}
