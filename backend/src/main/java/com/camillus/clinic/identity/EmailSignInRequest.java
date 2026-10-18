package com.camillus.clinic.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailSignInRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String role,
        @NotBlank @Size(max = 36) String hospitalId
) {}
