package io.github.hyunjyyyy.heavyeqtracker.location.history;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sites/{siteId:\\d+}")
public class LocationEventController {

    private final LocationHistoryService history;

    public LocationEventController(LocationHistoryService history) {
        this.history = history;
    }

    @GetMapping("/events/recent")
    public List<LocationEventView> recent(@RequestParam(required = false) Integer limit) {
        return history.recent(limit);
    }

    @GetMapping("/equipment/{id}/events")
    public List<LocationEventView> events(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                          @RequestParam(required = false) Integer limit) {
        return history.history(id, from, to, limit);
    }
}
