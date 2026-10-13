package com.camillus.clinic.hospital;

import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.ReceptionistHospitalAssignmentRepository;
import com.camillus.clinic.identity.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleAccessService {
    private final DoctorRepository doctors;
    private final ReceptionistHospitalAssignmentRepository assignments;

    public ScheduleAccessService(DoctorRepository doctors, ReceptionistHospitalAssignmentRepository assignments) {
        this.doctors = doctors;
        this.assignments = assignments;
    }

    public Doctor requireAccess(String doctorId, AuthenticatedUser user) {
        Doctor doctor = doctors.findByPublicIdIgnoreCase(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        requireAccess(doctor, user);
        return doctor;
    }

    public void requireAccess(Doctor doctor, AuthenticatedUser user) {
        if (user.role() == UserAccount.Role.ADMIN) return;
        if (user.role() == UserAccount.Role.DOCTOR) {
            if (doctor.getUser() != null && doctor.getUser().getPublicId().equalsIgnoreCase(user.publicId())) return;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor schedule access denied");
        }
        if (user.role() == UserAccount.Role.RECEPTIONIST &&
                assignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_Id(user.publicId(), doctor.getDepartment().getHospital().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor schedule access denied");
    }
}
