package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sites/{siteId:\\d+}/events")
public class LocationEventController {

    private final LocationHistoryService history;

    public LocationEventController(LocationHistoryService history) {
        this.history = history;
    }

    @GetMapping("/recent")
    public List<LocationEventView> recent(@RequestParam(required = false) Integer limit) {
        return history.recent(limit);
    }
}
