package com.camillus.clinic.hospital;

import jakarta.validation.Valid;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {
    private final HospitalService hospitals;
    public HospitalController(HospitalService hospitals) { this.hospitals = hospitals; }
    @GetMapping
    public List<HospitalResponse> listHospitals() { return hospitals.listHospitals(); }

    @GetMapping("/{hospitalId}/departments")
    public List<DepartmentResponse> listDepartments(@PathVariable String hospitalId) {
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
        return hospitals.listDepartments(publicId);
    }

    @PostMapping("/{hospitalId}/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> createDepartment(@PathVariable String hospitalId,
                                                                 @Valid @RequestBody DepartmentCreateRequest request) {
        UUID publicId = parseHospitalId(hospitalId);
        DepartmentResponse department = hospitals.createDepartment(publicId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(department);
    }

    private UUID parseHospitalId(String hospitalId) {
        UUID publicId;
        try {
            publicId = UUID.fromString(hospitalId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId must be a valid UUID");
        }
        if (!publicId.toString().equalsIgnoreCase(hospitalId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId must be a valid UUID");
        }
        return publicId;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidDepartment(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid department details"));
    }
}
