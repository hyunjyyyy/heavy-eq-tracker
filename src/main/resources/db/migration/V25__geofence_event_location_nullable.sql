-- NULL = 위치가 아닌 이유(장비 이동)로 닫힘 (#31 결정 5)
ALTER TABLE geofence_event MODIFY location_event_id BIGINT NULL, ALGORITHM=INPLACE;
