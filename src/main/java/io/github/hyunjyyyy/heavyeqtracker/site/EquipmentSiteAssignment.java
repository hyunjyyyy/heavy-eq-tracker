package io.github.hyunjyyyy.heavyeqtracker.site;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "equipment_site_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentSiteAssignment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "assigned_from", nullable = false)
    private Instant assignedFrom;

    @Column(name = "assigned_to")
    private Instant assignedTo;

    public EquipmentSiteAssignment(Long equipmentId, Long siteId, Instant assignedFrom, Instant assignedTo) {
        this.equipmentId = equipmentId;
        this.siteId = siteId;
        this.assignedFrom = assignedFrom;
        this.assignedTo = assignedTo;
    }
}
