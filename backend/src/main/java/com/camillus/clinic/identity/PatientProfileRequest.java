package com.camillus.clinic.identity;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientProfileRequest(
        @Size(max = 32) String phone,
        @PastOrPresent LocalDate dateOfBirth
) {
    public PatientProfileRequest {
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
