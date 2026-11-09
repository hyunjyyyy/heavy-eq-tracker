package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
public class LatestLocationController {

    private final LatestLocationQueryService service;

    public LatestLocationController(LatestLocationQueryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/latest")
    public LatestLocation latest(@PathVariable Long id) {
        return service.latestOf(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }
}
