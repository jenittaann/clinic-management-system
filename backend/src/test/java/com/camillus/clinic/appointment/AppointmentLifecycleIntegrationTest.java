package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.*;
import com.camillus.clinic.identity.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentLifecycleIntegrationTest {
    private static final String PASSWORD = "appointment-lifecycle-test-password";
    private static final AtomicInteger EMAIL_COUNTER = new AtomicInteger();
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PatientRepository patients;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AvailabilitySlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentRescheduleHistoryRepository histories;
    @Autowired ReceptionistHospitalAssignmentRepository assignments;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void patientCanCancelOwnAppointmentButNotAnotherPatients() throws Exception {
        Fixture own = book("Cancel Cardiology", 9, 0);
        Fixture other = book("Cancel Neurology", 9, 0);
        MockHttpSession stranger = login(other.patient());
        Csrf strangerCsrf = csrf(stranger);

        mvc.perform(post(cancelPath(other.appointmentId())).session(stranger).cookie(strangerCsrf.cookie())
                        .header("X-XSRF-TOKEN", strangerCsrf.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post(cancelPath(own.appointmentId())).session(stranger).cookie(strangerCsrf.cookie())
                        .header("X-XSRF-TOKEN", strangerCsrf.token()))
                .andExpect(status().isForbidden());
        assertEquals(Appointment.Status.CONFIRMED,
                appointments.findByPublicIdIgnoreCase(own.appointmentId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void doctorAssignedReceptionistAndAdminCanCancelWithinTheirScopes() throws Exception {
        UserAccount doctorAccount = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor("Staff Cancellation", doctorAccount);
        Fixture forDoctor = bookWithDoctor(doctor, 9, 0);
        Fixture forReceptionist = bookWithDoctor(doctor, 9, 30);
        Fixture forAdmin = bookWithDoctor(doctor, 10, 0);
        UserAccount receptionist = account(UserAccount.Role.RECEPTIONIST);
        assignments.saveAndFlush(new ReceptionistHospitalAssignment(receptionist, doctor.getDepartment().getHospital()));

        cancelAs(login(doctorAccount), forDoctor.appointmentId()).andExpect(status().isOk());
        cancelAs(login(receptionist), forReceptionist.appointmentId()).andExpect(status().isOk());
        cancelAs(login(account(UserAccount.Role.ADMIN)), forAdmin.appointmentId()).andExpect(status().isOk());

        Fixture restricted = book("Receptionist Scope", 9, 0);
        cancelAs(login(receptionist), restricted.appointmentId()).andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void cancellationRejectsInvalidTransitionsAndRequiresCsrf() throws Exception {
        Fixture cancelled = book("Repeat Cancellation", 9, 0);
        MockHttpSession owner = login(cancelled.patient());
        Csrf token = csrf(owner);
        mvc.perform(post(cancelPath(cancelled.appointmentId())).session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(post(cancelPath(cancelled.appointmentId())).session(owner).cookie(token.cookie())
                        .header("X-XSRF-TOKEN", token.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        Csrf retry = csrf(owner);
        mvc.perform(post(cancelPath(cancelled.appointmentId())).session(owner).cookie(retry.cookie())
                        .header("X-XSRF-TOKEN", retry.token()))
                .andExpect(status().isConflict());

        Fixture completed = book("Completed Appointment", 9, 0);
        jdbc.update("UPDATE appointments SET status = 'COMPLETED' WHERE public_id = ?", completed.appointmentId());
        entityManager.clear();
        MockHttpSession completedOwner = login(completed.patient());
        Csrf completedCsrf = csrf(completedOwner);
        mvc.perform(post(cancelPath(completed.appointmentId())).session(completedOwner).cookie(completedCsrf.cookie())
                        .header("X-XSRF-TOKEN", completedCsrf.token()))
                .andExpect(status().isConflict());
        Csrf rescheduleCsrf = csrf(completedOwner);
        mvc.perform(post(reschedulePath(completed.appointmentId())).session(completedOwner)
                        .cookie(rescheduleCsrf.cookie()).header("X-XSRF-TOKEN", rescheduleCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":\"" + futureTime(10, 0) + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Transactional
    void reschedulesAndAuditsOriginalAndNewTimesWithAuthenticatedActor() throws Exception {
        Fixture fixture = book("Reschedule Success", 9, 0);
        MockHttpSession owner = login(fixture.patient());
        LocalDateTime newStart = futureTime(10, 0);
        Csrf token = csrf(owner);

        mvc.perform(post(reschedulePath(fixture.appointmentId())).session(owner).cookie(token.cookie())
                        .header("X-XSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"" + newStart + "\",\"patientId\":999,\"durationMinutes\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startsAt").value(newStart.atZone(ZONE).toInstant().toString()))
                .andExpect(jsonPath("$.endsAt").value(newStart.plusMinutes(30).atZone(ZONE).toInstant().toString()))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.patientId").doesNotExist());

        AppointmentRescheduleHistory history = histories.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointmentId()).getFirst();
        assertEquals(fixture.startsAt().atZone(ZONE).toInstant(), history.getOldStartsAt());
        assertEquals(fixture.startsAt().plusMinutes(30).atZone(ZONE).toInstant(), history.getOldEndsAt());
        assertEquals(newStart.atZone(ZONE).toInstant(), history.getNewStartsAt());
        assertEquals(newStart.plusMinutes(30).atZone(ZONE).toInstant(), history.getNewEndsAt());
        assertEquals(fixture.patient().getPublicId(), history.getActor().getPublicId());
    }

    @Test
    @Transactional
    void failedRescheduleLeavesOriginalAndAuditHistoryUnchanged() throws Exception {
        Fixture original = book("Reschedule Validation", 9, 0);
        MockHttpSession owner = login(original.patient());
        LocalDateTime outside = futureTime(12, 0);

        reschedule(owner, original.appointmentId(), outside).andExpect(status().isConflict());
        reschedule(owner, original.appointmentId(), LocalDateTime.now(ZONE).minusDays(1).withSecond(0).withNano(0))
                .andExpect(status().isBadRequest());
        LocalDateTime doctorConflictTime = futureTime(9, 30);
        Fixture doctorConflict = bookWithDoctor(original.doctor(), 9, 30);
        reschedule(owner, original.appointmentId(), doctorConflictTime).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Doctor already has an overlapping appointment"));

        appointments.deleteById(appointments.findByPublicIdIgnoreCase(doctorConflict.appointmentId()).orElseThrow().getId());
        Doctor patientConflictDoctor = doctor("Patient Conflict", null);
        Appointment conflict = appointments.saveAndFlush(new Appointment(
                patients.findByUser_PublicIdIgnoreCase(original.patient().getPublicId()).orElseThrow(),
                patientConflictDoctor, doctorConflictTime.atZone(ZONE).toInstant(),
                doctorConflictTime.plusMinutes(30).atZone(ZONE).toInstant(), Appointment.Status.CONFIRMED));
        reschedule(owner, original.appointmentId(), doctorConflictTime).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Patient already has an overlapping appointment"));

        Appointment unchanged = appointments.findByPublicIdIgnoreCase(original.appointmentId()).orElseThrow();
        assertEquals(original.startsAt().atZone(ZONE).toInstant(), unchanged.getStartsAt());
        assertEquals(Appointment.Status.CONFIRMED, unchanged.getStatus());
        assertEquals(0, histories.count());
        appointments.delete(conflict);
    }

    @Test
    @Transactional
    void cancelledAppointmentNoLongerBlocksBookingAndRescheduleRequiresCsrf() throws Exception {
        Fixture fixture = book("Freed Cancellation Slot", 9, 0);
        MockHttpSession owner = login(fixture.patient());
        Csrf noCsrf = csrf(owner);
        mvc.perform(post(reschedulePath(fixture.appointmentId())).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":\"" + futureTime(10, 0) + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(cancelPath(fixture.appointmentId())).session(owner).cookie(noCsrf.cookie())
                        .header("X-XSRF-TOKEN", noCsrf.token())).andExpect(status().isOk());

        UserAccount nextPatient = account(UserAccount.Role.PATIENT);
        patients.saveAndFlush(new Patient(nextPatient, null, null));
        MockHttpSession nextSession = login(nextPatient);
        Csrf nextCsrf = csrf(nextSession);
        mvc.perform(post("/api/appointments").session(nextSession).cookie(nextCsrf.cookie())
                        .header("X-XSRF-TOKEN", nextCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"" + fixture.doctor().getPublicId() + "\",\"startsAt\":\"" + fixture.startsAt() + "\"}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions cancelAs(MockHttpSession session, String appointmentId) throws Exception {
        Csrf token = csrf(session);
        return mvc.perform(post(cancelPath(appointmentId)).session(session).cookie(token.cookie())
                .header("X-XSRF-TOKEN", token.token()));
    }

    private org.springframework.test.web.servlet.ResultActions reschedule(MockHttpSession session, String appointmentId,
                                                                           LocalDateTime start) throws Exception {
        Csrf token = csrf(session);
        return mvc.perform(post(reschedulePath(appointmentId)).session(session).cookie(token.cookie())
                .header("X-XSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + start + "\"}"));
    }

    private Fixture book(String label, int hour, int minute) throws Exception {
        return bookWithDoctor(doctor(label, null), hour, minute);
    }

    private Fixture bookWithDoctor(Doctor doctor, int hour, int minute) throws Exception {
        UserAccount patient = account(UserAccount.Role.PATIENT);
        patients.saveAndFlush(new Patient(patient, null, null));
        MockHttpSession session = login(patient);
        LocalDateTime startsAt = futureTime(hour, minute);
        Csrf token = csrf(session);
        MvcResult result = mvc.perform(post("/api/appointments").session(session).cookie(token.cookie())
                        .header("X-XSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"" + doctor.getPublicId() + "\",\"startsAt\":\"" + startsAt + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String appointmentId = JsonPath.read(result.getResponse().getContentAsString(), "$.appointmentId");
        return new Fixture(patient, doctor, startsAt, appointmentId);
    }

    private Doctor doctor(String label, UserAccount linkedAccount) {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Lifecycle Hospital " + EMAIL_COUNTER.incrementAndGet(), null, null, null, null, null));
        Department department = departments.saveAndFlush(new Department(hospital, label, null));
        Doctor doctor = new Doctor(department, "Dr " + label, null, null);
        doctor.assignUser(linkedAccount);
        doctor = doctors.saveAndFlush(doctor);
        int weekday = LocalDate.now(ZONE).plusDays(1).getDayOfWeek().getValue();
        slots.saveAndFlush(new AvailabilitySlot(doctor, weekday, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        return doctor;
    }

    private LocalDateTime futureTime(int hour, int minute) {
        LocalDate date = LocalDate.now(ZONE).plusDays(1);
        return date.atTime(hour, minute);
    }

    private String cancelPath(String appointmentId) { return "/api/appointments/" + appointmentId + "/cancel"; }
    private String reschedulePath(String appointmentId) { return "/api/appointments/" + appointmentId + "/reschedule"; }

    private UserAccount account(UserAccount.Role role) {
        return users.saveAndFlush(new UserAccount("lifecycle-" + EMAIL_COUNTER.incrementAndGet() + "@example.test",
                passwordEncoder.encode(PASSWORD), role));
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        Csrf token = csrf();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(token.cookie()).header("X-XSRF-TOKEN", token.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Csrf(JsonPath.read(result.getResponse().getContentAsString(), "$.token"),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }
    private Csrf csrf() throws Exception { return csrf(null); }

    private record Fixture(UserAccount patient, Doctor doctor, LocalDateTime startsAt, String appointmentId) {}
    private record Csrf(String token, Cookie cookie) {}
}
