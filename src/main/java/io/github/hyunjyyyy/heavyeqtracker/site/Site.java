package io.github.hyunjyyyy.heavyeqtracker.site;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "site")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Site {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Site(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }
}
