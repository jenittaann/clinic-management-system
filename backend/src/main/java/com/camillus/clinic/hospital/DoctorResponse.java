package com.camillus.clinic.hospital;

public record DoctorResponse(
        String id,
        String name,
        String specialization,
        Integer experienceYears,
        Integer appointmentDurationMinutes,
        String departmentId,
        String departmentName,
        String hospitalId,
        String hospitalName
) {
    static DoctorResponse from(Doctor doctor) {
        Department department = doctor.getDepartment();
        Hospital hospital = department.getHospital();
        return new DoctorResponse(
                doctor.getPublicId(),
                doctor.getName(),
                doctor.getSpecialization(),
                doctor.getExperienceYears(),
                doctor.getAppointmentDurationMinutes(),
                department.getPublicId(),
                department.getName(),
                hospital.getPublicId(),
                hospital.getName()
        );
    }
}
