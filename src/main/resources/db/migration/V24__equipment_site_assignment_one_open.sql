-- 장비당 열린 배정(끝이 빈 배정)은 하나. MySQL엔 부분 유일 인덱스가 없어 생성 열로 흉내 낸다:
-- 열려 있으면 1, 닫혔으면 NULL. 유일 인덱스는 NULL끼리 겹치는 건 허용한다
ALTER TABLE equipment_site_assignment
    ADD COLUMN open_marker TINYINT AS (IF(assigned_to IS NULL, 1, NULL)) VIRTUAL, ALGORITHM=INSTANT;

CREATE UNIQUE INDEX uk_assignment_one_open ON equipment_site_assignment (equipment_id, open_marker) ALGORITHM=INPLACE;
