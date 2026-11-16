package io.github.hyunjyyyy.heavyeqtracker.site;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
public class EquipmentMoveController {

    public record MoveRequest(@NotNull Long toSiteId) {
    }

    public record AssignmentView(Long equipmentId, Long siteId, Instant assignedFrom) {
    }

    private final EquipmentMoveService service;

    public EquipmentMoveController(EquipmentMoveService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/sites/{siteId:\\d+}/equipment/{id}/move")
    public AssignmentView move(@PathVariable long siteId, @PathVariable Long id, @Valid @RequestBody MoveRequest request) {
        EquipmentSiteAssignment next = service.move(siteId, id, request.toSiteId());
        return new AssignmentView(next.getEquipmentId(), next.getSiteId(), next.getAssignedFrom());
    }
}
