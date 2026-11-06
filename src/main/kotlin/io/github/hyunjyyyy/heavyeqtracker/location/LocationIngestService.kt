package io.github.hyunjyyyy.heavyeqtracker.location

import io.github.hyunjyyyy.heavyeqtracker.equipment.EquipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class EquipmentNotFoundException(id: Long) : RuntimeException("equipment $id not found")

data class IngestResult(val ack: LocationAck, val created: Boolean)

@Service
class LocationIngestService(
    private val equipmentRepository: EquipmentRepository,
    private val eventRepository: LocationEventRepository,
) {
    @Transactional
    fun ingest(report: LocationReport, idempotencyKey: String): IngestResult {
        val existing = eventRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            return IngestResult(LocationAck(existing.id!!), created = false)
        }
        val equipment = equipmentRepository.findById(report.equipmentId)
            .orElseThrow { EquipmentNotFoundException(report.equipmentId) }
        val saved = eventRepository.save(
            LocationEvent(
                equipment = equipment,
                lat = report.lat,
                lon = report.lon,
                speedKmh = report.speedKmh,
                heading = report.heading,
                fixType = report.fixType,
                horizontalAccuracyM = report.horizontalAccuracyM,
                recordedAt = report.recordedAt,
                idempotencyKey = idempotencyKey,
            )
        )
        return IngestResult(LocationAck(saved.id!!), created = true)
    }
}
