CREATE TABLE proximity_pair_state (
                                 id                  BIGINT       NOT NULL AUTO_INCREMENT,
                                 equipment_a_id      BIGINT       NOT NULL,
                                 equipment_b_id      BIGINT       NOT NULL,
                                 since               DATETIME(6)  NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_proximity_pair_equipment_a FOREIGN KEY (equipment_a_id) REFERENCES equipment (id),
                                 CONSTRAINT fk_proximity_pair_equipment_b FOREIGN KEY (equipment_b_id) REFERENCES equipment (id),
                                 UNIQUE KEY uk_pair (equipment_a_id, equipment_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
