package com.camillus.clinic.hospital;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AppointmentDurationRequest(@NotNull @Positive Integer durationMinutes) {}
