package io.github.hyunjyyyy.heavyeqtracker.location;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationIngestService service;

    public LocationController(LocationIngestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LocationAccepted> report(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Equipment-Id") Long equipmentIdHeader,
            @Valid @RequestBody LocationReport report) {
        if (!equipmentIdHeader.equals(report.equipmentId())) {
            throw new EquipmentIdMismatchException(equipmentIdHeader, report.equipmentId());
        }
        return ResponseEntity.accepted().body(service.ingest(report, idempotencyKey));
    }
}
