package com.camillus.clinic.identity;

public record PasswordlessSessionResponse(IdentityResponse identity, String hospitalId) {}
