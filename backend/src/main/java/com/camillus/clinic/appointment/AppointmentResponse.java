package com.camillus.clinic.appointment;

import java.time.Instant;

public record AppointmentResponse(String appointmentId, String doctorId, String doctorName,
                                  String departmentId, String departmentName,
                                  String hospitalId, String hospitalName,
                                  Instant startsAt, Instant endsAt, Appointment.Status status) {
    public static AppointmentResponse from(Appointment appointment) {
        var doctor = appointment.getDoctor();
        var department = doctor.getDepartment();
        var hospital = department.getHospital();
        return new AppointmentResponse(appointment.getPublicId(), doctor.getPublicId(), doctor.getName(),
                department.getPublicId(), department.getName(), hospital.getPublicId(), hospital.getName(),
                appointment.getStartsAt(), appointment.getEndsAt(), appointment.getStatus());
    }
}
