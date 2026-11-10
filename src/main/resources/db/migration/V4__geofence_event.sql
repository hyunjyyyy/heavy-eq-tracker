CREATE TABLE geofence_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_id        BIGINT       NOT NULL,
    zone_id             BIGINT       NOT NULL,
    location_event_id   BIGINT       NOT NULL,
    type                VARCHAR(10)  NOT NULL,
    recorded_at         DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_geofence_event_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_geofence_event_zone FOREIGN KEY (zone_id) REFERENCES zone (id),
    CONSTRAINT fk_geofence_event_location FOREIGN KEY (location_event_id) REFERENCES location_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
