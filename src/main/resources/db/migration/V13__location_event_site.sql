ALTER TABLE location_event ADD COLUMN site_id BIGINT NOT NULL DEFAULT 1, ALGORITHM=INSTANT;

CREATE INDEX idx_location_event_site_recorded ON location_event (site_id, recorded_at) ALGORITHM=INPLACE;

SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE location_event ADD CONSTRAINT fk_location_event_site FOREIGN KEY (site_id) REFERENCES site (id), ALGORITHM=INPLACE;
SET FOREIGN_KEY_CHECKS = 1;
