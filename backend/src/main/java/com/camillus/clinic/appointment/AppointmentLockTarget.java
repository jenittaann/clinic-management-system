package com.camillus.clinic.appointment;

/** Internal identifiers used only to acquire locks in the required order. */
public record AppointmentLockTarget(Long patientId, String doctorPublicId) {}
