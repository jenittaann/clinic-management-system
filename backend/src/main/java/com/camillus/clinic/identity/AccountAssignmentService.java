package com.camillus.clinic.identity;

import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.Hospital;
import com.camillus.clinic.hospital.HospitalRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class AccountAssignmentService {
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final HospitalRepository hospitals;
    private final ReceptionistHospitalAssignmentRepository assignments;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountAssignmentService(UserRepository users, DoctorRepository doctors, HospitalRepository hospitals,
                                    ReceptionistHospitalAssignmentRepository assignments, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.doctors = doctors;
        this.hospitals = hospitals;
        this.assignments = assignments;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<DoctorAccountOption> unlinkedDoctorAccounts() {
        return users.findUnlinkedAccountsByRole(UserAccount.Role.DOCTOR);
    }

    @Transactional
    public void linkDoctor(String doctorId, String userId) {
        Doctor doctor = doctors.findByPublicIdForUpdate(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        UserAccount user = users.findByPublicIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        if (user.getRole() != UserAccount.Role.DOCTOR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account must have the DOCTOR role");
        if (doctor.getUser() != null && !doctor.getUser().getPublicId().equalsIgnoreCase(userId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor profile is already linked");
        if (doctors.findByUser_PublicIdIgnoreCase(userId).filter(existing -> !existing.getPublicId().equalsIgnoreCase(doctorId)).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor account is already linked to a profile");
        doctor.assignUser(user);
    }

    @Transactional
    public DoctorAccountOption createAndLinkDoctorAccount(String doctorId, String email) {
        Doctor doctor = doctors.findByPublicIdForUpdate(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        if (doctor.getUser() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor profile is already linked");
        String normalizedEmail = email.trim();
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");

        byte[] randomCredential = new byte[48];
        RANDOM.nextBytes(randomCredential);
        UserAccount account;
        try {
            account = users.saveAndFlush(new UserAccount(normalizedEmail,
                    passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(randomCredential)),
                    UserAccount.Role.DOCTOR));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }
        doctor.assignUser(account);
        return new DoctorAccountOption(account.getPublicId(), account.getEmail());
    }

    @Transactional
    public void unlinkDoctor(String doctorId) {
        Doctor doctor = doctors.findByPublicIdIgnoreCase(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        doctor.assignUser(null);
    }

    @Transactional
    public void assignHospital(String userId, String hospitalId) {
        UserAccount user = users.findByPublicIdIgnoreCase(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        if (user.getRole() != UserAccount.Role.RECEPTIONIST)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account must have the RECEPTIONIST role");
        Hospital hospital = hospitals.findByPublicId(hospitalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
        if (assignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(userId, hospitalId)) return;
        try {
            assignments.saveAndFlush(new ReceptionistHospitalAssignment(user, hospital));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hospital assignment already exists");
        }
    }

    @Transactional
    public void removeHospital(String userId, String hospitalId) {
        UserAccount user = users.findByPublicIdIgnoreCase(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        if (user.getRole() != UserAccount.Role.RECEPTIONIST)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account must have the RECEPTIONIST role");
        if (hospitals.findByPublicId(hospitalId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found");
        assignments.deleteByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(userId, hospitalId);
    }
}
