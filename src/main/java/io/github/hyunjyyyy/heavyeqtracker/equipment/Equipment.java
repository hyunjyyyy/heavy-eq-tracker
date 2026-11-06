package io.github.hyunjyyyy.heavyeqtracker.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용. 네 코드에선 못 부른다
public class Equipment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "positioning_source", nullable = false, length = 20)
    private PositioningSource positioningSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Equipment(String name, EquipmentType type, PositioningSource positioningSource) {
        this.name = name;
        this.type = type;
        this.positioningSource = positioningSource;
        this.createdAt = Instant.now();
    }
}
