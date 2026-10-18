package com.camillus.clinic.hospital;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

public record DoctorCreateRequest(
        @NotBlank @Size(max = 36) String hospitalId,
        @NotBlank @Size(max = 36) String departmentId,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 160) String specialization,
        @Min(0) Integer experienceYears,
        @NotNull @Positive Integer appointmentDurationMinutes
) {}
