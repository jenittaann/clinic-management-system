package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.AvailabilitySlot;
import com.camillus.clinic.hospital.AvailabilitySlotRepository;
import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.Patient;
import com.camillus.clinic.identity.PatientRepository;
import com.camillus.clinic.identity.ReceptionistHospitalAssignmentRepository;
import com.camillus.clinic.identity.UserAccount;
import com.camillus.clinic.identity.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AppointmentService {
    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<Appointment.Status> ACTIVE_STATUSES =
            Set.of(Appointment.Status.REQUESTED, Appointment.Status.CONFIRMED,
                    Appointment.Status.WAITING, Appointment.Status.IN_PROGRESS);
    private static final Set<Appointment.Status> QUEUE_STATUSES =
            Set.of(Appointment.Status.WAITING, Appointment.Status.IN_PROGRESS);

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AvailabilitySlotRepository availability;
    private final ReceptionistHospitalAssignmentRepository receptionistAssignments;
    private final AppointmentRescheduleHistoryRepository rescheduleHistory;
    private final UserRepository users;
    private final AppointmentStatusHistoryRepository statusHistory;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, PatientRepository patients, DoctorRepository doctors,
                              AvailabilitySlotRepository availability,
                              ReceptionistHospitalAssignmentRepository receptionistAssignments,
                              AppointmentRescheduleHistoryRepository rescheduleHistory,
                              UserRepository users, AppointmentStatusHistoryRepository statusHistory, Clock clock) {
        this.appointments = appointments;
        this.patients = patients;
        this.doctors = doctors;
        this.availability = availability;
        this.receptionistAssignments = receptionistAssignments;
        this.rescheduleHistory = rescheduleHistory;
        this.users = users;
        this.statusHistory = statusHistory;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse book(AuthenticatedUser currentUser, AppointmentBookingRequest request) {
        UUID doctorPublicId = parseUuid(request.doctorId(), "doctorId");

        // All appointment writes acquire parent locks in this fixed order. Future
        // rescheduling must use this same service strategy (patient, then doctor).
        Patient patient = patients.findByUserPublicIdForUpdate(currentUser.publicId())
                .filter(profile -> profile.getUser().getRole() == UserAccount.Role.PATIENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
        Doctor doctor = doctors.findByPublicIdForUpdate(doctorPublicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        AppointmentInterval interval = validateRequestedTime(doctor, request.startsAt());

        // Parent locks serialize all API writes even when no overlapping row exists;
        // locking queries are current reads after the locks have been acquired.
        if (!appointments.findDoctorOverlapsForUpdate(doctor.getId(), ACTIVE_STATUSES,
                interval.startsAt(), interval.endsAt(), null).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor already has an overlapping appointment");
        if (!appointments.findPatientOverlapsForUpdate(patient, ACTIVE_STATUSES,
                interval.startsAt(), interval.endsAt(), null).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient already has an overlapping appointment");

        Appointment saved = appointments.saveAndFlush(new Appointment(patient, doctor, interval.startsAt(), interval.endsAt(), Appointment.Status.CONFIRMED));
        return AppointmentResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse cancel(String appointmentId, AuthenticatedUser currentUser) {
        Appointment appointment = lockAppointmentForWrite(appointmentId);
        authorizeManage(appointment, currentUser);
        ensureCanChange(appointment);
        Appointment.Status previous = appointment.getStatus();
        appointment.cancel();
        recordStatusChange(appointment, currentUser, previous, Appointment.Status.CANCELLED);
        appointments.flush();
        return AppointmentResponse.from(appointment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse reschedule(String appointmentId, AppointmentRescheduleRequest request,
                                          AuthenticatedUser currentUser) {
        UUID publicId = parseUuid(appointmentId, "appointmentId");
        AppointmentLockTarget target = appointments.findLockTargetByPublicId(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // The same lock order is used by booking, cancellation, and every future
        // rescheduling write: patient first, then doctor, then appointment.
        Patient patient = patients.findByIdForUpdate(target.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
        Doctor doctor = doctors.findByPublicIdForUpdate(target.doctorPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        Appointment appointment = appointments.findByPublicIdForUpdate(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        authorizeManage(appointment, currentUser);
        if (appointment.getStatus() != Appointment.Status.REQUESTED && appointment.getStatus() != Appointment.Status.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unqueued appointments can be rescheduled");

        AppointmentInterval interval = validateRequestedTime(doctor, request.startsAt());
        if (appointment.getStartsAt().equals(interval.startsAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New appointment time must differ from the current time");
        if (!appointments.findDoctorOverlapsForUpdate(doctor.getId(), ACTIVE_STATUSES,
                interval.startsAt(), interval.endsAt(), appointment.getId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor already has an overlapping appointment");
        if (!appointments.findPatientOverlapsForUpdate(patient, ACTIVE_STATUSES,
                interval.startsAt(), interval.endsAt(), appointment.getId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient already has an overlapping appointment");

        UserAccount actor = users.findByPublicIdIgnoreCase(currentUser.publicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        rescheduleHistory.saveAndFlush(new AppointmentRescheduleHistory(appointment, actor,
                appointment.getStartsAt(), appointment.getEndsAt(), interval.startsAt(), interval.endsAt(), clock.instant()));
        appointment.reschedule(interval.startsAt(), interval.endsAt());
        appointments.flush();
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMine(AuthenticatedUser currentUser) {
        if (patients.findByUser_PublicIdIgnoreCase(currentUser.publicId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found");
        return appointments.findAllByPatient_User_PublicIdIgnoreCaseOrderByStartsAtAsc(currentUser.publicId())
                .stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getByPublicId(String appointmentId, AuthenticatedUser currentUser) {
        UUID publicId = parseUuid(appointmentId, "appointmentId");
        Appointment appointment = appointments.findByPublicIdIgnoreCase(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        authorizeRead(appointment, currentUser);
        return AppointmentResponse.from(appointment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckInResponse checkIn(String appointmentId, AuthenticatedUser currentUser) {
        Appointment appointment = lockAppointmentForWrite(appointmentId);
        authorizeCheckIn(appointment, currentUser);
        if (appointment.getStatus() != Appointment.Status.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed appointments can be checked in");

        Instant now = clock.instant();
        LocalDate serviceDate = LocalDateTime.ofInstant(appointment.getStartsAt(), CLINIC_ZONE).toLocalDate();
        Instant earliest = appointment.getStartsAt().minus(Duration.ofMinutes(30));
        Instant endOfServiceDate = serviceDate.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        if (now.isBefore(earliest) || !now.isBefore(endOfServiceDate))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is outside the check-in window");

        UserAccount actor = authenticatedAccount(currentUser);
        int nextPosition = appointments.findMaxQueuePosition(appointment.getDoctor().getId(), serviceDate) + 1;
        Appointment.Status previous = appointment.getStatus();
        appointment.checkIn(actor, now, serviceDate, nextPosition);
        recordStatusChange(appointment, actor, previous, Appointment.Status.WAITING);
        appointments.flush();
        return CheckInResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<QueueItemResponse> getDoctorQueue(String doctorId, LocalDate serviceDate, AuthenticatedUser currentUser) {
        UUID publicId = parseUuid(doctorId, "doctorId");
        var doctor = doctors.findByPublicIdIgnoreCase(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        authorizeQueueAccess(doctor, currentUser);
        return appointments.findAllByDoctor_PublicIdIgnoreCaseAndServiceDateAndStatusInOrderByQueuePositionAsc(
                        publicId.toString(), serviceDate, QUEUE_STATUSES)
                .stream().map(QueueItemResponse::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse start(String appointmentId, AuthenticatedUser currentUser) {
        return transition(appointmentId, currentUser, Set.of(Appointment.Status.WAITING), Appointment.Status.IN_PROGRESS,
                Set.of(UserAccount.Role.DOCTOR, UserAccount.Role.ADMIN), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse complete(String appointmentId, AuthenticatedUser currentUser) {
        return transition(appointmentId, currentUser, Set.of(Appointment.Status.IN_PROGRESS), Appointment.Status.COMPLETED,
                Set.of(UserAccount.Role.DOCTOR, UserAccount.Role.ADMIN), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse noShow(String appointmentId, AuthenticatedUser currentUser) {
        return transition(appointmentId, currentUser, Set.of(Appointment.Status.CONFIRMED), Appointment.Status.NO_SHOW,
                Set.of(UserAccount.Role.RECEPTIONIST, UserAccount.Role.DOCTOR, UserAccount.Role.ADMIN), true);
    }

    private AppointmentResponse transition(String appointmentId, AuthenticatedUser currentUser,
            Set<Appointment.Status> allowedFrom, Appointment.Status target, Set<UserAccount.Role> allowedRoles,
            boolean allowAssignedReceptionist) {
        Appointment appointment = lockAppointmentForWrite(appointmentId);
        if (!allowedRoles.contains(currentUser.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment access denied");
        if (allowAssignedReceptionist) authorizeRead(appointment, currentUser);
        else authorizeDoctorOrAdmin(appointment, currentUser);
        if (!allowedFrom.contains(appointment.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment status transition is not allowed");
        if (target == Appointment.Status.NO_SHOW &&
                appointment.getStartsAt().plus(Duration.ofMinutes(15)).isAfter(clock.instant()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is not yet eligible to be marked no-show");
        Appointment.Status previous = appointment.getStatus();
        appointment.transitionTo(target);
        recordStatusChange(appointment, currentUser, previous, target);
        appointments.flush();
        return AppointmentResponse.from(appointment);
    }

    private void authorizeCheckIn(Appointment appointment, AuthenticatedUser user) {
        if (user.role() == UserAccount.Role.ADMIN) return;
        if (user.role() == UserAccount.Role.RECEPTIONIST && receptionistAssignments
                .existsByReceptionist_PublicIdIgnoreCaseAndHospital_Id(user.publicId(),
                        appointment.getDoctor().getDepartment().getHospital().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment access denied");
    }

    private void authorizeQueueAccess(com.camillus.clinic.hospital.Doctor doctor, AuthenticatedUser user) {
        if (user.role() == UserAccount.Role.ADMIN) return;
        if (user.role() == UserAccount.Role.DOCTOR && doctor.getUser() != null &&
                doctor.getUser().getPublicId().equalsIgnoreCase(user.publicId())) return;
        if (user.role() == UserAccount.Role.RECEPTIONIST && receptionistAssignments
                .existsByReceptionist_PublicIdIgnoreCaseAndHospital_Id(user.publicId(), doctor.getDepartment().getHospital().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Queue access denied");
    }

    private void authorizeDoctorOrAdmin(Appointment appointment, AuthenticatedUser user) {
        if (user.role() == UserAccount.Role.ADMIN) return;
        if (user.role() == UserAccount.Role.DOCTOR && appointment.getDoctor().getUser() != null &&
                appointment.getDoctor().getUser().getPublicId().equalsIgnoreCase(user.publicId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment access denied");
    }

    private UserAccount authenticatedAccount(AuthenticatedUser currentUser) {
        return users.findByPublicIdIgnoreCase(currentUser.publicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private void recordStatusChange(Appointment appointment, AuthenticatedUser actor,
                                    Appointment.Status previous, Appointment.Status next) {
        recordStatusChange(appointment, authenticatedAccount(actor), previous, next);
    }

    private void recordStatusChange(Appointment appointment, UserAccount actor,
                                    Appointment.Status previous, Appointment.Status next) {
        statusHistory.save(new AppointmentStatusHistory(appointment, actor, previous, next, clock.instant()));
    }

    private AvailabilitySlot findMatchingRule(Doctor doctor, LocalDateTime start, LocalDateTime end, int durationMinutes) {
        if (!start.toLocalDate().equals(end.toLocalDate())) return null;
        int weekday = start.getDayOfWeek().getValue();
        LocalTime localStart = start.toLocalTime();
        LocalTime localEnd = end.toLocalTime();
        List<AvailabilitySlot> rules = availability.findAllByDoctor_PublicIdOrderByDayOfWeekAscStartTimeAsc(doctor.getPublicId());
        for (AvailabilitySlot rule : rules) {
            if (rule.getDayOfWeek() != weekday || localStart.isBefore(rule.getStartTime()) || localEnd.isAfter(rule.getEndTime())) continue;
            long secondsFromAnchor = Duration.between(rule.getStartTime(), localStart).getSeconds();
            if (secondsFromAnchor >= 0 && secondsFromAnchor % (durationMinutes * 60L) == 0) return rule;
        }
        return null;
    }

    private AppointmentInterval validateRequestedTime(Doctor doctor, LocalDateTime localStart) {
        Integer durationMinutes = doctor.getAppointmentDurationMinutes();
        if (durationMinutes == null || durationMinutes <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor appointment duration is not configured");
        if (localStart.getNano() != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt must have whole-second precision");
        LocalDateTime localEnd;
        try {
            localEnd = localStart.plusMinutes(durationMinutes);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment time is invalid");
        }
        Instant startsAt = localStart.atZone(CLINIC_ZONE).toInstant();
        Instant endsAt = localEnd.atZone(CLINIC_ZONE).toInstant();
        if (!startsAt.isAfter(clock.instant()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment must be in the future");
        if (findMatchingRule(doctor, localStart, localEnd, durationMinutes) == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested time is outside doctor availability or fixed appointment intervals");
        return new AppointmentInterval(startsAt, endsAt);
    }

    private Appointment lockAppointmentForWrite(String appointmentId) {
        UUID publicId = parseUuid(appointmentId, "appointmentId");
        AppointmentLockTarget target = appointments.findLockTargetByPublicId(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        patients.findByIdForUpdate(target.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
        doctors.findByPublicIdForUpdate(target.doctorPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return appointments.findByPublicIdForUpdate(publicId.toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private void ensureCanChange(Appointment appointment) {
        if (appointment.getStatus() != Appointment.Status.REQUESTED && appointment.getStatus() != Appointment.Status.CONFIRMED &&
                appointment.getStatus() != Appointment.Status.WAITING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment cannot be cancelled in its current state");
    }

    private void authorizeManage(Appointment appointment, AuthenticatedUser currentUser) {
        authorizeRead(appointment, currentUser);
    }

    private void authorizeRead(Appointment appointment, AuthenticatedUser currentUser) {
        if (currentUser.role() == UserAccount.Role.ADMIN) return;
        if (currentUser.role() == UserAccount.Role.PATIENT &&
                appointment.getPatient().getUser().getPublicId().equalsIgnoreCase(currentUser.publicId())) return;
        if (currentUser.role() == UserAccount.Role.DOCTOR && appointment.getDoctor().getUser() != null &&
                appointment.getDoctor().getUser().getPublicId().equalsIgnoreCase(currentUser.publicId())) return;
        if (currentUser.role() == UserAccount.Role.RECEPTIONIST &&
                receptionistAssignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_Id(
                        currentUser.publicId(), appointment.getDoctor().getDepartment().getHospital().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment access denied");
    }

    private UUID parseUuid(String value, String field) {
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.toString().equalsIgnoreCase(value)) return parsed;
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a valid UUID");
    }

    private record AppointmentInterval(Instant startsAt, Instant endsAt) {}
}
