-- 현장별 근접 설정. 열이 NULL이면 yml 기본값을 따른다(#32)
CREATE TABLE site_proximity_settings (
                                         site_id                BIGINT       NOT NULL,
                                         threshold_m            DOUBLE,
                                         max_error_m            DOUBLE,
                                         stale_after_seconds    INT,
                                         default_phone_error_m  DOUBLE,
                                         default_rtk_error_m    DOUBLE,
                                         updated_at             DATETIME(6)  NOT NULL,
                                         PRIMARY KEY (site_id),
                                         CONSTRAINT fk_site_proximity_settings_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
