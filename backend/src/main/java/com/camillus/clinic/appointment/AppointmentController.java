package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.ApiErrorResponse;
import com.camillus.clinic.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) { this.appointments = appointments; }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> book(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                     @Valid @RequestBody AppointmentBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.book(currentUser, request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentResponse> getMine(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.getMine(currentUser);
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public AppointmentResponse cancel(@PathVariable String appointmentId,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.cancel(appointmentId, currentUser);
    }

    @PostMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public AppointmentResponse reschedule(@PathVariable String appointmentId,
            @Valid @RequestBody AppointmentRescheduleRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.reschedule(appointmentId, request, currentUser);
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN')")
    public AppointmentResponse getById(@PathVariable String appointmentId,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.getByPublicId(appointmentId, currentUser);
    }

    @PostMapping("/{appointmentId}/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public CheckInResponse checkIn(@PathVariable String appointmentId,
                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.checkIn(appointmentId, currentUser);
    }

    @PostMapping("/{appointmentId}/start")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public AppointmentResponse start(@PathVariable String appointmentId,
                                     @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.start(appointmentId, currentUser);
    }

    @PostMapping("/{appointmentId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public AppointmentResponse complete(@PathVariable String appointmentId,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.complete(appointmentId, currentUser);
    }

    @PostMapping("/{appointmentId}/no-show")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    public AppointmentResponse noShow(@PathVariable String appointmentId,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return appointments.noShow(appointmentId, currentUser);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid appointment request"));
    }
}
