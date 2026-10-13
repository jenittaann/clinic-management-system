package com.camillus.clinic.hospital;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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
