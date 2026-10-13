package com.camillus.clinic.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class PatientService {
    private final PatientRepository patients;
    private final UserRepository users;

    public PatientService(PatientRepository patients, UserRepository users) {
        this.patients = patients;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PatientResponse getOwnProfile(AuthenticatedUser currentUser) {
        return patients.findByUser_PublicIdIgnoreCase(currentUser.publicId())
                .map(PatientResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }

    @Transactional
    public PatientWriteResult upsertOwnProfile(AuthenticatedUser currentUser, PatientProfileRequest request) {
        UserAccount account = users.findByPublicIdIgnoreCase(currentUser.publicId())
                .filter(user -> user.getRole() == UserAccount.Role.PATIENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient account not found"));

        var existing = patients.findByUser_PublicIdIgnoreCase(currentUser.publicId());
        boolean created = existing.isEmpty();
        Patient patient = existing.orElseGet(() -> new Patient(account, null, null));
        patient.updateProfile(request.phone(), request.dateOfBirth());
        Patient saved = patients.saveAndFlush(patient);
        return new PatientWriteResult(PatientResponse.from(saved), created);
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> listPatients(AuthenticatedUser currentUser, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "user.email"));
        Page<Patient> visiblePatients = switch (currentUser.role()) {
            case ADMIN -> patients.findAll(pageable);
            case RECEPTIONIST -> patients.findAllVisibleToReceptionist(currentUser.publicId(), pageable);
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        };
        return visiblePatients
                .map(PatientResponse::from);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(UUID patientPublicId, AuthenticatedUser currentUser) {
        var visiblePatient = switch (currentUser.role()) {
            case ADMIN -> patients.findByUser_PublicIdIgnoreCase(patientPublicId.toString());
            case RECEPTIONIST -> patients.findVisibleToReceptionist(patientPublicId.toString(), currentUser.publicId());
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        };
        return visiblePatient
                .map(PatientResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }
}
