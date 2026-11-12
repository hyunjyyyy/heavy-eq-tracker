package io.github.hyunjyyyy.heavyeqtracker.proximity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

@Entity
@Table(name = "proximity_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProximityEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(name = "equipment_a_id", nullable = false)
    private Long equipmentAId;

    @Column(name = "equipment_b_id", nullable = false)
    private Long equipmentBId;

    @Column(name = "location_event_id", nullable = false)
    private Long locationEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProximityEventType type;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProximityEvent(Long equipmentAId, Long equipmentBId, Long locationEventId,
                          ProximityEventType type, Double distanceM, Instant recordedAt) {
        this.equipmentAId = equipmentAId;
        this.equipmentBId = equipmentBId;
        this.locationEventId = locationEventId;
        this.type = type;
        this.distanceM = distanceM;
        this.recordedAt = recordedAt;
        this.createdAt = Instant.now();
    }
}
