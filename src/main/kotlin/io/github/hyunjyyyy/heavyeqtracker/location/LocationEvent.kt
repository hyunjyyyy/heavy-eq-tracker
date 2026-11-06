package io.github.hyunjyyyy.heavyeqtracker.location

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "location_event")
class LocationEvent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    var equipment: Equipment,

    @Column(nullable = false)
    var lat: Double,

    @Column(nullable = false)
    var lon: Double,

    @Column(name = "speed_kmh")
    var speedKmh: Double? = null,

    @Column
    var heading: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "fix_type", nullable = false, length = 20)
    var fixType: FixType,

    @Column(name = "h_accuracy_m")
    var horizontalAccuracyM: Double? = null,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant,

    @Column(name = "received_at", nullable = false, updatable = false)
    val receivedAt: Instant = Instant.now(),

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    val idempotencyKey: String,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
