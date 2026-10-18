package com.camillus.clinic.appointment;

import java.time.Instant;

public record AppointmentTimeWindow(Instant startsAt, Instant endsAt) {}
