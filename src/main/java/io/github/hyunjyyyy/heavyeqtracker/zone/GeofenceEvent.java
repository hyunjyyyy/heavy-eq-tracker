package io.github.hyunjyyyy.heavyeqtracker.zone;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

@Entity
@Table(name = "geofence_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeofenceEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "location_event_id", nullable = false)
    private Long locationEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GeofenceEventType type;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GeofenceEvent(Long equipmentId, Long zoneId, Long locationEventId,
                         GeofenceEventType type, Instant recordedAt) {
        this.equipmentId = equipmentId;
        this.zoneId = zoneId;
        this.locationEventId = locationEventId;
        this.type = type;
        this.recordedAt = recordedAt;
        this.createdAt = Instant.now();
    }
}
