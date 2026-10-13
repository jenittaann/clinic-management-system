package com.camillus.clinic.appointment;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AppointmentRescheduleRequest(@NotNull LocalDateTime startsAt) {}
