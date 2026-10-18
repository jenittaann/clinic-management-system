package com.camillus.clinic.hospital;

import com.camillus.clinic.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/doctors/{doctorId}")
public class DoctorScheduleController {
    private final DoctorScheduleService schedules;

    public DoctorScheduleController(DoctorScheduleService schedules) { this.schedules = schedules; }

    @GetMapping("/availability")
    public DoctorScheduleResponse get(@PathVariable String doctorId,
                                      @RequestParam(required = false) LocalDate date) {
        return schedules.getPublic(uuid(doctorId, "doctorId"), date);
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    @PutMapping("/appointment-duration")
    public DoctorScheduleResponse setDuration(@PathVariable String doctorId,
            @Valid @RequestBody AppointmentDurationRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return schedules.setDuration(uuid(doctorId, "doctorId"), request.durationMinutes(), user);
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    @PostMapping("/availability")
    public ResponseEntity<AvailabilityRuleResponse> create(@PathVariable String doctorId,
            @Valid @RequestBody AvailabilityRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schedules.create(uuid(doctorId, "doctorId"), request, user));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    @PutMapping("/availability/{availabilityId}")
    public AvailabilityRuleResponse update(@PathVariable String doctorId, @PathVariable String availabilityId,
            @Valid @RequestBody AvailabilityRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return schedules.update(uuid(doctorId, "doctorId"), uuid(availabilityId, "availabilityId"), request, user);
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    @DeleteMapping("/availability/{availabilityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String doctorId, @PathVariable String availabilityId,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        schedules.delete(uuid(doctorId, "doctorId"), uuid(availabilityId, "availabilityId"), user);
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

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid schedule request"));
    }
}
