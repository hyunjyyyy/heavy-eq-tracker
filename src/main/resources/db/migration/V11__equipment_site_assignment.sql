CREATE TABLE equipment_site_assignment (
                                           id             BIGINT       NOT NULL AUTO_INCREMENT,
                                           equipment_id   BIGINT       NOT NULL,
                                           site_id        BIGINT       NOT NULL,
                                           assigned_from  DATETIME(6)  NOT NULL,
                                           assigned_to    DATETIME(6),
                                           PRIMARY KEY (id),
                                           KEY idx_assignment_equipment_from (equipment_id, assigned_from),
                                           CONSTRAINT fk_assignment_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
                                           CONSTRAINT fk_assignment_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기존 장비 전부 기본 현장으로. 시작은 생성 시각과 가장 이른 위치 중 빠른 쪽
INSERT INTO equipment_site_assignment (equipment_id, site_id, assigned_from)
SELECT e.id, 1, LEAST(e.created_at, COALESCE(MIN(l.recorded_at), e.created_at))
FROM equipment e
         LEFT JOIN location_event l ON l.equipment_id = e.id
GROUP BY e.id, e.created_at;
