package io.github.hyunjyyyy.heavyeqtracker.status;

import io.github.hyunjyyyy.heavyeqtracker.location.LocationEventView;
import io.github.hyunjyyyy.heavyeqtracker.location.LocationHistoryService;
import io.github.hyunjyyyy.heavyeqtracker.site.SiteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/* 본사 전체 보기. 인터셉터가 /api/v1/hq/** 를 root로 정해 현장 조건 없이 조회한다(#36에서 본사 역할만) */
@RestController
@RequestMapping("/api/v1/hq")
public class HqController {

    public record SiteView(Long id, String name) {
    }

    private final SiteRepository sites;
    private final LocationHistoryService history;

    public HqController(SiteRepository sites, LocationHistoryService history) {
        this.sites = sites;
        this.history = history;
    }

    @GetMapping("/sites")
    public List<SiteView> sites() {
        return sites.findAll(Sort.by("id")).stream()
                .map(s -> new SiteView(s.getId(), s.getName()))
                .toList();
    }

    @GetMapping("/events/recent")
    public List<LocationEventView> recent(@RequestParam(required = false) Integer limit) {
        return history.recent(limit);
    }
}
