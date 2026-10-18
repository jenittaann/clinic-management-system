package com.camillus.clinic.hospital;

import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.appointment.Appointment;
import com.camillus.clinic.appointment.AppointmentRepository;
import com.camillus.clinic.appointment.AppointmentTimeWindow;
import com.camillus.clinic.appointment.AppointmentService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalTime;
import java.util.List;

@Service
public class DoctorScheduleService {
    private final DoctorRepository doctors;
    private final AvailabilitySlotRepository slots;
    private final ScheduleAccessService access;
    private final AppointmentRepository appointments;
    private final Clock clock;

    private static final Set<Appointment.Status> ACTIVE_STATUSES = Set.of(Appointment.Status.REQUESTED,
            Appointment.Status.CONFIRMED, Appointment.Status.WAITING, Appointment.Status.IN_PROGRESS);

    public DoctorScheduleService(DoctorRepository doctors, AvailabilitySlotRepository slots, ScheduleAccessService access,
                                AppointmentRepository appointments, Clock clock) {
        this.doctors = doctors;
        this.slots = slots;
        this.access = access;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DoctorScheduleResponse getPublic(String doctorId) {
        return getPublic(doctorId, null);
    }

    @Transactional(readOnly = true)
    public DoctorScheduleResponse getPublic(String doctorId, LocalDate date) {
        Doctor doctor = doctors.findByPublicIdIgnoreCase(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return response(doctor, date);
    }

    @Transactional
    public DoctorScheduleResponse setDuration(String doctorId, int duration, AuthenticatedUser user) {
        Doctor doctor = lockedDoctor(doctorId);
        access.requireAccess(doctor, user);
        doctor.setAppointmentDurationMinutes(duration);
        return response(doctor, null);
    }

    @Transactional
    public AvailabilityRuleResponse create(String doctorId, AvailabilityRuleRequest request, AuthenticatedUser user) {
        Doctor doctor = lockedDoctor(doctorId);
        access.requireAccess(doctor, user);
        validateTimes(request.startTime(), request.endTime());
        rejectOverlap(doctor, request, null);
        return AvailabilityRuleResponse.from(slots.save(new AvailabilitySlot(doctor, request.dayOfWeek(), request.startTime(), request.endTime())));
    }

    @Transactional
    public AvailabilityRuleResponse update(String doctorId, String availabilityId, AvailabilityRuleRequest request, AuthenticatedUser user) {
        Doctor doctor = lockedDoctor(doctorId);
        access.requireAccess(doctor, user);
        AvailabilitySlot slot = slots.findByPublicIdIgnoreCaseAndDoctor_PublicIdIgnoreCase(availabilityId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability rule not found"));
        validateTimes(request.startTime(), request.endTime());
        rejectOverlap(doctor, request, slot);
        slot.updateRule(request.dayOfWeek(), request.startTime(), request.endTime());
        return AvailabilityRuleResponse.from(slot);
    }

    @Transactional
    public void delete(String doctorId, String availabilityId, AuthenticatedUser user) {
        Doctor doctor = lockedDoctor(doctorId);
        access.requireAccess(doctor, user);
        AvailabilitySlot slot = slots.findByPublicIdIgnoreCaseAndDoctor_PublicIdIgnoreCase(availabilityId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability rule not found"));
        slots.delete(slot);
    }

    private Doctor lockedDoctor(String doctorId) {
        return doctors.findByPublicIdForUpdate(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
    }

    private void rejectOverlap(Doctor doctor, AvailabilityRuleRequest request, AvailabilitySlot current) {
        if (slots.hasOverlap(doctor.getId(), request.dayOfWeek(), request.startTime(), request.endTime(), current == null ? null : current.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Availability rule overlaps an existing rule");
    }

    private void validateTimes(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
    }

    private DoctorScheduleResponse response(Doctor doctor, LocalDate date) {
        List<AvailabilityRuleResponse> rules = slots.findAllByDoctor_PublicIdOrderByDayOfWeekAscStartTimeAsc(doctor.getPublicId())
                .stream().map(AvailabilityRuleResponse::from).toList();
        return new DoctorScheduleResponse(doctor.getPublicId(), doctor.getAppointmentDurationMinutes(), rules,
                availableSlots(doctor, date));
    }

    private List<AvailableAppointmentSlotResponse> availableSlots(Doctor doctor, LocalDate requestedDate) {
        Integer configuredDuration = doctor.getAppointmentDurationMinutes();
        if (configuredDuration == null || configuredDuration < 1) return List.of();
        ZoneId zone = AppointmentService.CLINIC_ZONE;
        Instant now = clock.instant();
        LocalDate firstDay = requestedDate == null ? now.atZone(zone).toLocalDate() : requestedDate;
        LocalDate endDay = requestedDate == null ? firstDay.plusDays(7) : firstDay.plusDays(1);
        Instant rangeStart = firstDay.atStartOfDay(zone).toInstant();
        Instant rangeEnd = endDay.atStartOfDay(zone).toInstant();
        List<AppointmentTimeWindow> occupied = appointments.findActiveAppointmentWindows(
                doctor.getId(), ACTIVE_STATUSES, rangeStart, rangeEnd);
        List<AvailabilitySlot> weeklyRules = slots.findAllByDoctor_PublicIdOrderByDayOfWeekAscStartTimeAsc(doctor.getPublicId());
        List<AvailableAppointmentSlotResponse> available = new java.util.ArrayList<>();

        for (LocalDate date = firstDay; date.isBefore(endDay); date = date.plusDays(1)) {
            for (AvailabilitySlot rule : weeklyRules) {
                if (rule.getDayOfWeek() != date.getDayOfWeek().getValue()) continue;
                int endSeconds = rule.getEndTime().toSecondOfDay();
                int firstSeconds = rule.getStartTime().toSecondOfDay();
                long durationSeconds = configuredDuration.longValue() * 60L;
                for (long offsetSeconds = 0; firstSeconds + offsetSeconds + durationSeconds <= endSeconds;
                     offsetSeconds += durationSeconds) {
                    LocalTime start = rule.getStartTime().plusSeconds(offsetSeconds);
                    LocalTime end = start.plusMinutes(configuredDuration);
                    Instant startsAt = LocalDateTime.of(date, start).atZone(zone).toInstant();
                    Instant endsAt = LocalDateTime.of(date, end).atZone(zone).toInstant();
                    if (!startsAt.isAfter(now)) continue;
                    boolean isBooked = occupied.stream().anyMatch(window -> window.startsAt().isBefore(endsAt)
                            && window.endsAt().isAfter(startsAt));
                    if (!isBooked) available.add(new AvailableAppointmentSlotResponse(date, start, end));
                }
            }
        }
        return List.copyOf(available);
    }
}
