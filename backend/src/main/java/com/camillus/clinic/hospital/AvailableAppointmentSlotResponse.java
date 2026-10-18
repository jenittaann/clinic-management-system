package com.camillus.clinic.hospital;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailableAppointmentSlotResponse(LocalDate date, LocalTime startTime, LocalTime endTime) {}
