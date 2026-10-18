package com.camillus.clinic.identity;

record PasswordlessVerificationResult(AuthenticatedUser principal, String hospitalId, boolean rememberMe) {}
