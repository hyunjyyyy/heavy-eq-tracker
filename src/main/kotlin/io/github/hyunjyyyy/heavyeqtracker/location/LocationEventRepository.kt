package io.github.hyunjyyyy.heavyeqtracker.location

import org.springframework.data.jpa.repository.JpaRepository

interface LocationEventRepository : JpaRepository<LocationEvent, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): LocationEvent?
}