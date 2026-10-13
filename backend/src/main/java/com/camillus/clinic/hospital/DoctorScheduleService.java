package com.camillus.clinic.hospital;

import com.camillus.clinic.identity.AuthenticatedUser;
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

    public DoctorScheduleService(DoctorRepository doctors, AvailabilitySlotRepository slots, ScheduleAccessService access) {
        this.doctors = doctors;
        this.slots = slots;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public DoctorScheduleResponse get(String doctorId, AuthenticatedUser user) {
        Doctor doctor = access.requireAccess(doctorId, user);
        return response(doctor);
    }

    @Transactional
    public DoctorScheduleResponse setDuration(String doctorId, int duration, AuthenticatedUser user) {
        Doctor doctor = lockedDoctor(doctorId);
        access.requireAccess(doctor, user);
        doctor.setAppointmentDurationMinutes(duration);
        return response(doctor);
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

    private DoctorScheduleResponse response(Doctor doctor) {
        List<AvailabilityRuleResponse> rules = slots.findAllByDoctor_PublicIdOrderByDayOfWeekAscStartTimeAsc(doctor.getPublicId())
                .stream().map(AvailabilityRuleResponse::from).toList();
        return new DoctorScheduleResponse(doctor.getPublicId(), doctor.getAppointmentDurationMinutes(), rules);
    }
}
