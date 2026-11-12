package io.github.hyunjyyyy.heavyeqtracker.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FleetStatusController {

    private final FleetStatusService fleet;

    public FleetStatusController(FleetStatusService fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/api/v1/sites/{siteId:\\d+}/equipment")
    public List<EquipmentStatus> fleet(@PathVariable long siteId) {
        return fleet.fleet(siteId);
    }
}
