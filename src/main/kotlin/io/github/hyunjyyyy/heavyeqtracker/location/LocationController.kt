package io.github.hyunjyyyy.heavyeqtracker.location

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(private val service: LocationIngestService) {

    @PostMapping
    fun report(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody report: LocationReport,
    ): ResponseEntity<LocationAck> {
        val result = service.ingest(report, idempotencyKey)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.ack)
    }
}
