package com.camillus.clinic.hospital;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoctorProfileUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 160) String specialization,
        @Min(0) Integer experienceYears
) {}
