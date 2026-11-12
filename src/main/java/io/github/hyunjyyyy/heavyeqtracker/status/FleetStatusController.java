package io.github.hyunjyyyy.heavyeqtracker.status;

import io.github.hyunjyyyy.heavyeqtracker.site.SiteContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FleetStatusController {

    private final FleetStatusService fleet;

    public FleetStatusController(FleetStatusService fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/api/v1/equipment")
    public List<EquipmentStatus> fleet() {
        return fleet.fleet(SiteContext.current());   // 5단계에서 경로의 {siteId}로 바꾼다
    }
}
