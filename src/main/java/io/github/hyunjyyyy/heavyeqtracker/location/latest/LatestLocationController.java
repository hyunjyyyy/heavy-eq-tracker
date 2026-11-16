package io.github.hyunjyyyy.heavyeqtracker.location.latest;

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites/{siteId:\\d+}/equipment")
public class LatestLocationController {

    private final LatestLocationQueryService service;

    public LatestLocationController(LatestLocationQueryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/latest")
    public LatestLocation latest(@PathVariable long siteId, @PathVariable Long id) {
        return service.latestOf(siteId, id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }
}
