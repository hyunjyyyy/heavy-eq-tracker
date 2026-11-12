package io.github.hyunjyyyy.heavyeqtracker.zone;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ZoneKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String polygon;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Zone(String name, ZoneKind kind, String polygon) {
        this.name = name;
        this.kind = kind;
        this.polygon = polygon;
        this.active = true;
        this.createdAt = Instant.now();
    }
}
