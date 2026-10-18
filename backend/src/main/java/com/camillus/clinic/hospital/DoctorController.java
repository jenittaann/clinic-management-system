package com.camillus.clinic.hospital;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.camillus.clinic.identity.AuthenticatedUser;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DoctorController {
    private final DoctorService doctors;

    public DoctorController(DoctorService doctors) {
        this.doctors = doctors;
    }

    @GetMapping("/doctors")
    public List<DoctorResponse> listDoctors() {
        return doctors.listDoctors();
    }

    @GetMapping("/doctors/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorResponse getOwnDoctor(@AuthenticationPrincipal AuthenticatedUser user) {
        return doctors.getOwnDoctor(user);
    }

    @PutMapping("/doctors/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorResponse updateOwnDoctor(@AuthenticationPrincipal AuthenticatedUser user,
                                          @jakarta.validation.Valid @RequestBody DoctorProfileUpdateRequest request) {
        return doctors.updateOwnDoctor(user, request);
    }

    @GetMapping("/hospitals/{hospitalId}/doctors")
    public List<DoctorResponse> listDoctorsForHospital(@PathVariable String hospitalId) {
        UUID publicId;
        try {
            publicId = UUID.fromString(hospitalId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "hospitalId must be a valid UUID"
            );
        }

        if (!publicId.toString().equalsIgnoreCase(hospitalId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "hospitalId must be a valid UUID"
            );
        }
        return doctors.listDoctorsForHospital(publicId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }
}
