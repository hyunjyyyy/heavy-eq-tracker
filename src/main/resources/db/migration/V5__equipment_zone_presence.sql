CREATE TABLE equipment_zone_presence (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    equipment_id        BIGINT          NOT NULL,
    zone_id             BIGINT          NOT NULL,
    entered_at          DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_presence_equipment_zone (equipment_id, zone_id),
    CONSTRAINT fk_presence_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_presence_zone FOREIGN KEY (zone_id) REFERENCES zone (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
