package io.github.hyunjyyyy.heavyeqtracker.proximity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "proximity_pair_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProximityPairState {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_a_id", nullable = false)
    private Long equipmentAId;

    @Column(name = "equipment_b_id", nullable = false)
    private Long equipmentBId;

    @Column(nullable = false)
    private Instant since;

    public ProximityPairState(Long equipmentAId, Long equipmentBId, Instant since) {
        this.equipmentAId = equipmentAId;
        this.equipmentBId = equipmentBId;
        this.since = since;
    }
}
