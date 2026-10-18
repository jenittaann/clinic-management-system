package com.camillus.clinic.hospital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentCreateRequest(
        @NotBlank @Size(max = 160) String name,
        String description
) {}
