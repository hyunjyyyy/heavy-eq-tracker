package io.github.hyunjyyyy.heavyeqtracker.zone;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "equipment_zone_presence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentZonePresence {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    public EquipmentZonePresence(Long equipmentId, Long zoneId, Instant enteredAt) {
        this.equipmentId = equipmentId;
        this.zoneId = zoneId;
        this.enteredAt = enteredAt;
    }
}
