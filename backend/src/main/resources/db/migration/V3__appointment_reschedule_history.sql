CREATE TABLE appointment_reschedule_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    old_starts_at TIMESTAMP NOT NULL,
    old_ends_at TIMESTAMP NOT NULL,
    new_starts_at TIMESTAMP NOT NULL,
    new_ends_at TIMESTAMP NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_reschedule_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_reschedule_history_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT chk_reschedule_history_old_time CHECK (old_ends_at > old_starts_at),
    CONSTRAINT chk_reschedule_history_new_time CHECK (new_ends_at > new_starts_at)
);

CREATE INDEX idx_reschedule_history_appointment_time
    ON appointment_reschedule_history(appointment_id, changed_at);
