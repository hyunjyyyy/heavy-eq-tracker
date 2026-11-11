package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/equipment")
public class LatestLocationController {

    private final LatestLocationQueryService service;
    private final LocationHistoryService history;

    public LatestLocationController(LatestLocationQueryService service,
                                    LocationHistoryService history) {
        this.service = service;
        this.history = history;
    }

    @GetMapping("/{id}/latest")
    public LatestLocation latest(@PathVariable Long id) {
        return service.latestOf(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }

    @GetMapping("/{id}/events")
    public List<LocationEventView> events(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                          @RequestParam(required = false) Integer limit) {
        return history.history(id, from, to, limit);
    }
}
