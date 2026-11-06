CREATE TABLE location_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_id    BIGINT       NOT NULL,
    lat             DOUBLE       NOT NULL,
    lon             DOUBLE       NOT NULL,
    speed_kmh       DOUBLE,
    heading         DOUBLE,
    fix_type        VARCHAR(20)  NOT NULL,
    h_accuracy_m    DOUBLE,
    recorded_at     DATETIME(6)  NOT NULL,
    received_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    idempotency_key VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_event_idempotency (idempotency_key),
    CONSTRAINT fk_location_event_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
