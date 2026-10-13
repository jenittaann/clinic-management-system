package com.camillus.clinic.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AppointmentBookingRequest(@NotBlank String doctorId, @NotNull LocalDateTime startsAt) {}
