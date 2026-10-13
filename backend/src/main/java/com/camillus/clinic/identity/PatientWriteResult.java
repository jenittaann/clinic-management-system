package com.camillus.clinic.identity;

public record PatientWriteResult(PatientResponse profile, boolean created) {}
