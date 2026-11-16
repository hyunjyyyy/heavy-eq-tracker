package io.github.hyunjyyyy.heavyeqtracker.proximity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sites/{siteId:\\d+}/settings/proximity")
public class ProximitySettingsController {

    private final ProximitySettingsService service;

    public ProximitySettingsController(ProximitySettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ProximitySettingsView get(@PathVariable long siteId) {
        return service.get(siteId);
    }

    @PutMapping
    public ProximitySettingsView put(@PathVariable long siteId, @Valid @RequestBody ProximitySettingsRequest request) {
        return service.update(siteId, request);
    }
}
