ALTER TABLE doctors
    ADD COLUMN user_id BIGINT NULL;
ALTER TABLE doctors
    ADD COLUMN appointment_duration_minutes INT NULL;
ALTER TABLE doctors
    ADD CONSTRAINT uq_doctors_user UNIQUE (user_id);
ALTER TABLE doctors
    ADD CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE doctors
    ADD CONSTRAINT chk_doctors_appointment_duration
        CHECK (appointment_duration_minutes IS NULL OR appointment_duration_minutes > 0);

CREATE TABLE receptionist_hospital_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    receptionist_user_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    CONSTRAINT uq_receptionist_hospital UNIQUE (receptionist_user_id, hospital_id),
    CONSTRAINT fk_receptionist_hospital_user FOREIGN KEY (receptionist_user_id) REFERENCES users(id),
    CONSTRAINT fk_receptionist_hospital_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE INDEX idx_receptionist_assignments_hospital
    ON receptionist_hospital_assignments(hospital_id, receptionist_user_id);
CREATE INDEX idx_availability_doctor_weekday_time
    ON availability_slots(doctor_id, day_of_week, start_time);
