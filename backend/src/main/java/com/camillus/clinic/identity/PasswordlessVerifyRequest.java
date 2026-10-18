package com.camillus.clinic.identity;

import jakarta.validation.constraints.NotBlank;

public record PasswordlessVerifyRequest(@NotBlank String token) {}
