package com.camillus.clinic.appointment;

import java.time.Instant;
import java.time.LocalDate;

public record QueueItemResponse(String appointmentId, String patientId, String doctorId,
                                LocalDate serviceDate, int queuePosition, Instant checkedInAt,
                                Instant startsAt, Instant endsAt, Appointment.Status status) {
    public static QueueItemResponse from(Appointment appointment) {
        return new QueueItemResponse(appointment.getPublicId(), appointment.getPatient().getUser().getPublicId(),
                appointment.getDoctor().getPublicId(), appointment.getServiceDate(), appointment.getQueuePosition(),
                appointment.getCheckedInAt(), appointment.getStartsAt(), appointment.getEndsAt(), appointment.getStatus());
    }
}
