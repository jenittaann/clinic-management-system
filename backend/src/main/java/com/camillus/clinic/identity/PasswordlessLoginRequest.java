package com.camillus.clinic.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordlessLoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String role,
        @NotBlank String hospitalId,
        Boolean rememberMe,
        Boolean registration
) {}
