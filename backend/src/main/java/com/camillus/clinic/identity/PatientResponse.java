package com.camillus.clinic.identity;

import java.time.LocalDate;

public record PatientResponse(String patientId, String email, String phone, LocalDate dateOfBirth) {
    static PatientResponse from(Patient patient) {
        UserAccount user = patient.getUser();
        return new PatientResponse(user.getPublicId(), user.getEmail(), patient.getPhone(), patient.getDateOfBirth());
    }
}
