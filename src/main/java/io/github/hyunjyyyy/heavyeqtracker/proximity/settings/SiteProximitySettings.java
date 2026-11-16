package io.github.hyunjyyyy.heavyeqtracker.proximity.settings;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/* 현장마다 yml 기본값을 덮어쓰는 값. NULL은 "기본값 따름". 현장 id가 곧 PK라 @TenantId는 붙이지 않는다 */
@Entity
@Table(name = "site_proximity_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteProximitySettings {

    @Id
    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "threshold_m")
    private Double thresholdM;

    @Column(name = "max_error_m")
    private Double maxErrorM;

    @Column(name = "stale_after_seconds")
    private Integer staleAfterSeconds;

    @Column(name = "default_phone_error_m")
    private Double defaultPhoneErrorM;

    @Column(name = "default_rtk_error_m")
    private Double defaultRtkErrorM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SiteProximitySettings(Long siteId) {
        this.siteId = siteId;
        this.updatedAt = Instant.now();
    }

    public void update(Double thresholdM, Double maxErrorM, Integer staleAfterSeconds,
                       Double defaultPhoneErrorM, Double defaultRtkErrorM) {
        this.thresholdM = thresholdM;
        this.maxErrorM = maxErrorM;
        this.staleAfterSeconds = staleAfterSeconds;
        this.defaultPhoneErrorM = defaultPhoneErrorM;
        this.defaultRtkErrorM = defaultRtkErrorM;
        this.updatedAt = Instant.now();
    }
}
