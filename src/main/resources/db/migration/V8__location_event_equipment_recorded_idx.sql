CREATE INDEX idx_location_event_equipment_recorded
    ON location_event (equipment_id, recorded_at);
