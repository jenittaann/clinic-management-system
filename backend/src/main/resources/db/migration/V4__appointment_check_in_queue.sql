ALTER TABLE appointments
    ADD COLUMN checked_in_at TIMESTAMP NULL;
ALTER TABLE appointments
    ADD COLUMN checked_in_by_user_id BIGINT NULL;
ALTER TABLE appointments
    ADD COLUMN service_date DATE NULL;
ALTER TABLE appointments
    ADD COLUMN queue_position INT NULL;
ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_checked_in_by FOREIGN KEY (checked_in_by_user_id) REFERENCES users(id);
ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_queue_position CHECK (queue_position IS NULL OR queue_position > 0);

CREATE UNIQUE INDEX uq_appointments_doctor_service_queue
    ON appointments(doctor_id, service_date, queue_position);
CREATE INDEX idx_appointments_doctor_service_status_queue
    ON appointments(doctor_id, service_date, status, queue_position);

CREATE TABLE appointment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    old_status VARCHAR(32) NOT NULL,
    new_status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_status_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_status_history_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_status_history_appointment_time
    ON appointment_status_history(appointment_id, changed_at);
