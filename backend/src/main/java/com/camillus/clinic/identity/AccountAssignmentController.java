package com.camillus.clinic.identity;

import com.camillus.clinic.hospital.ApiErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AccountAssignmentController {
    private final AccountAssignmentService assignments;
    public AccountAssignmentController(AccountAssignmentService assignments) { this.assignments = assignments; }

    @GetMapping("/doctor-accounts/unlinked")
    public List<DoctorAccountOption> unlinkedDoctorAccounts() {
        return assignments.unlinkedDoctorAccounts();
    }

    @PutMapping("/doctors/{doctorId}/account/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkDoctor(@PathVariable String doctorId, @PathVariable String userId) {
        assignments.linkDoctor(uuid(doctorId, "doctorId"), uuid(userId, "userId"));
    }

    @PostMapping("/doctors/{doctorId}/account")
    public ResponseEntity<DoctorAccountOption> createAndLinkDoctor(
            @PathVariable String doctorId, @Valid @RequestBody DoctorAccountCreateRequest request) {
        DoctorAccountOption account = assignments.createAndLinkDoctorAccount(
                uuid(doctorId, "doctorId"), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @DeleteMapping("/doctors/{doctorId}/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkDoctor(@PathVariable String doctorId) {
        assignments.unlinkDoctor(uuid(doctorId, "doctorId"));
    }

    @PutMapping("/receptionists/{userId}/hospitals/{hospitalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignHospital(@PathVariable String userId, @PathVariable String hospitalId) {
        assignments.assignHospital(uuid(userId, "userId"), uuid(hospitalId, "hospitalId"));
    }

    @DeleteMapping("/receptionists/{userId}/hospitals/{hospitalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHospital(@PathVariable String userId, @PathVariable String hospitalId) {
        assignments.removeHospital(uuid(userId, "userId"), uuid(hospitalId, "hospitalId"));
    }

    private String uuid(String value, String name) {
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.toString().equalsIgnoreCase(value)) return parsed.toString();
        } catch (IllegalArgumentException ignored) {}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be a valid UUID");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid doctor account request"));
    }
}
