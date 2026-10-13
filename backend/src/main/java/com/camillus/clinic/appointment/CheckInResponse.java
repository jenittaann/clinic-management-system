package com.camillus.clinic.appointment;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInResponse(String appointmentId, Appointment.Status status, Instant checkedInAt,
                              LocalDate serviceDate, int queuePosition) {
    public static CheckInResponse from(Appointment appointment) {
        return new CheckInResponse(appointment.getPublicId(), appointment.getStatus(), appointment.getCheckedInAt(),
                appointment.getServiceDate(), appointment.getQueuePosition());
    }
}
