package com.camillus.clinic.hospital;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.UserAccount;
import com.camillus.clinic.identity.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DoctorService {
    private final DoctorRepository doctors;
    private final HospitalRepository hospitals;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();

    public DoctorService(DoctorRepository doctors, HospitalRepository hospitals, DepartmentRepository departments,
                         UserRepository users, PasswordEncoder passwordEncoder) {
        this.doctors = doctors;
        this.hospitals = hospitals;
        this.departments = departments;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors() {
        return doctors.findAllByOrderByNameAsc().stream()
                .map(DoctorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctorsForHospital(UUID hospitalPublicId) {
        String publicId = hospitalPublicId.toString();
        if (hospitals.findByPublicId(publicId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hospital not found for public ID " + publicId
            );
        }

        return doctors.findAllByDepartment_Hospital_PublicIdOrderByNameAsc(publicId).stream()
                .map(DoctorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse getOwnDoctor(AuthenticatedUser user) {
        if (user.role() != UserAccount.Role.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor access denied");
        }
        return doctors.findByUser_PublicIdIgnoreCase(user.publicId())
                .map(DoctorResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile is not linked to this account"));
    }

    @Transactional
    public DoctorResponse updateOwnDoctor(AuthenticatedUser user, DoctorProfileUpdateRequest request) {
        if (user.role() != UserAccount.Role.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor access denied");
        }
        Doctor doctor = doctors.findByUser_PublicIdIgnoreCase(user.publicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile is not linked to this account"));
        String specialization = request.specialization() == null || request.specialization().isBlank()
                ? null : request.specialization().trim();
        doctor.updateProfile(request.name().trim(), specialization, request.experienceYears());
        return DoctorResponse.from(doctor);
    }

    @Transactional
    public DoctorResponse createDoctor(DoctorCreateRequest request) {
        UUID hospitalId = parsePublicId(request.hospitalId(), "hospitalId");
        UUID departmentId = parsePublicId(request.departmentId(), "departmentId");
        Hospital hospital = hospitals.findByPublicId(hospitalId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
        Department department = departments.findByPublicIdIgnoreCase(departmentId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        if (!department.getHospital().getPublicId().equalsIgnoreCase(hospital.getPublicId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department does not belong to selected hospital");
        }
        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }

        String specialization = request.specialization() == null || request.specialization().isBlank()
                ? null : request.specialization().trim();
        Doctor doctor = new Doctor(department, request.name().trim(), specialization, request.experienceYears());
        doctor.setAppointmentDurationMinutes(request.appointmentDurationMinutes());
        byte[] randomCredential = new byte[48];
        RANDOM.nextBytes(randomCredential);
        UserAccount account = users.saveAndFlush(new UserAccount(email,
                passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(randomCredential)),
                UserAccount.Role.DOCTOR));
        doctor.assignUser(account);
        return DoctorResponse.from(doctors.saveAndFlush(doctor));
    }

    private UUID parsePublicId(String value, String field) {
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.toString().equalsIgnoreCase(value)) return parsed;
        } catch (IllegalArgumentException ignored) {}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a valid UUID");
    }
}
