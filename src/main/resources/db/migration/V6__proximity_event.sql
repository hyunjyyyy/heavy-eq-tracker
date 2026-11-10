CREATE TABLE proximity_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_a_id      BIGINT       NOT NULL,
    equipment_b_id      BIGINT       NOT NULL,
    location_event_id   BIGINT       NOT NULL,
    type                VARCHAR(10)  NOT NULL,
    distance_m          DOUBLE       NOT NULL,
    recorded_at         DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_proximity_event_equipment_a FOREIGN KEY (equipment_a_id) REFERENCES equipment (id),
    CONSTRAINT fk_proximity_event_equipment_b FOREIGN KEY (equipment_b_id) REFERENCES equipment (id),
    CONSTRAINT fk_proximity_event_location    FOREIGN KEY (location_event_id) REFERENCES location_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
