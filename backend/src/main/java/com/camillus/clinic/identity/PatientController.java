package com.camillus.clinic.identity;

import com.camillus.clinic.hospital.ApiErrorResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse getOwnProfile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return patients.getOwnProfile(currentUser);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientResponse> putOwnProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody PatientProfileRequest request) {
        try {
            PatientWriteResult result = patients.upsertOwnProfile(currentUser, request);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(result.profile());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient profile could not be saved");
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public Page<PatientResponse> listPatients(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patients.listPatients(currentUser, page, size);
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public PatientResponse getPatient(@PathVariable String patientId,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID publicId;
        try {
            publicId = UUID.fromString(patientId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId must be a valid UUID");
        }
        if (!publicId.toString().equalsIgnoreCase(patientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId must be a valid UUID");
        }
        return patients.getPatient(publicId, currentUser);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidProfile(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid patient profile"));
    }
}
