package com.camillus.clinic.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientRegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 36) String hospitalId
) {}
