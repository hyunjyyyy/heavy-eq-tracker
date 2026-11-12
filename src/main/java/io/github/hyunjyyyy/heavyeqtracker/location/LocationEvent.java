package io.github.hyunjyyyy.heavyeqtracker.location;

import io.github.hyunjyyyy.heavyeqtracker.equipment.Equipment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

@Entity
@Table(name = "location_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column
    private Double heading;

    @Enumerated(EnumType.STRING)
    @Column(name = "fix_type", nullable = false, length = 20)
    private FixType fixType;

    @Column(name = "h_accuracy_m")
    private Double horizontalAccuracyM;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    public LocationEvent(Equipment equipment,
                         double lat,
                         double lon,
                         Double speedKmh,
                         Double heading,
                         FixType fixType,
                         Double horizontalAccuracyM,
                         Instant recordedAt,
                         Instant receivedAt,
                         String idempotencyKey) {
        this.equipment = equipment;
        this.lat = lat;
        this.lon = lon;
        this.speedKmh = speedKmh;
        this.heading = heading;
        this.fixType = fixType;
        this.horizontalAccuracyM = horizontalAccuracyM;
        this.recordedAt = recordedAt;
        this.receivedAt = receivedAt;
        this.idempotencyKey = idempotencyKey;
    }
}
