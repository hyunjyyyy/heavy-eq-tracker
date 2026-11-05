package io.github.hyunjyyyy.heavyeqtracker.equipment

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "equipment")
class Equipment(
    @Column(nullable = false, length = 50)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: EquipmentType,

    @Enumerated(EnumType.STRING)
    @Column(name = "positioning_source", nullable = false, length = 20)
    var positioningSource: PositioningSource,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
