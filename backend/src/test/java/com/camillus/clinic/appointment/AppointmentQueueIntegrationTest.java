package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.Department;
import com.camillus.clinic.hospital.DepartmentRepository;
import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.Hospital;
import com.camillus.clinic.hospital.HospitalRepository;
import com.camillus.clinic.identity.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentQueueIntegrationTest {
    private static final String PASSWORD = "queue-test-password";
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PatientRepository patients;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentStatusHistoryRepository statusHistory;
    @Autowired ReceptionistHospitalAssignmentRepository assignments;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void checkInIsScopedAuditedAndCreatesOrderedQueueEntry() throws Exception {
        Fixture fixture = fixture(true, Instant.now().plusSeconds(900));
        MockHttpSession receptionist = login(fixture.receptionist());
        mvc.perform(post("/api/appointments/{id}/check-in", fixture.appointment().getPublicId()).session(receptionist))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors/{id}/queue?serviceDate=invalid", fixture.doctor().getPublicId()).session(receptionist))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("serviceDate must be an ISO date (YYYY-MM-DD)"));
        mvc.perform(get(queuePath(fixture.doctor(), LocalDate.now(ZONE))))
                .andExpect(status().isUnauthorized());
        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.serviceDate").value(LocalDateTime.ofInstant(fixture.appointment().getStartsAt(), ZONE).toLocalDate().toString()))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.patientId").doesNotExist());

        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isConflict());
        LocalDate serviceDate = LocalDateTime.ofInstant(fixture.appointment().getStartsAt(), ZONE).toLocalDate();
        mvc.perform(get(queuePath(fixture.doctor(), serviceDate)).session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appointmentId").value(fixture.appointment().getPublicId()))
                .andExpect(jsonPath("$[0].patientId").value(fixture.patient().getPublicId()))
                .andExpect(jsonPath("$[0].queuePosition").value(1))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].id").doesNotExist());

        UserAccount unassigned = account(UserAccount.Role.RECEPTIONIST);
        mvc.perform(get(queuePath(fixture.doctor(), serviceDate)).session(login(unassigned)))
                .andExpect(status().isForbidden());
        postAction(login(unassigned), "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isForbidden());
        mvc.perform(get(queuePath(fixture.doctor(), serviceDate)).session(login(account(UserAccount.Role.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].appointmentId").value(fixture.appointment().getPublicId()));
        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/cancel")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get(queuePath(fixture.doctor(), serviceDate)).session(receptionist))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(2, statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointment().getPublicId()).size());
    }

    @Test
    @Transactional
    void doctorCanStartAndCompleteOnlyTheirQueueAndEveryTransitionIsAudited() throws Exception {
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Fixture fixture = fixture(true, Instant.now().plusSeconds(900), doctorUser);
        MockHttpSession receptionist = login(fixture.receptionist());
        MockHttpSession doctor = login(doctorUser);
        postAction(doctor, "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isForbidden());
        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isOk());
        mvc.perform(get(queuePath(fixture.doctor(), LocalDateTime.ofInstant(fixture.appointment().getStartsAt(), ZONE).toLocalDate())).session(doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].appointmentId").value(fixture.appointment().getPublicId()));
        postAction(doctor, "/api/appointments/" + fixture.appointment().getPublicId() + "/start")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAction(doctor, "/api/appointments/" + fixture.appointment().getPublicId() + "/complete")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        postAction(doctor, "/api/appointments/" + fixture.appointment().getPublicId() + "/complete")
                .andExpect(status().isConflict());
        assertEquals(3, statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointment().getPublicId()).size());

        UserAccount unrelatedDoctor = account(UserAccount.Role.DOCTOR);
        postAction(login(unrelatedDoctor), "/api/appointments/" + fixture.appointment().getPublicId() + "/start")
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void noShowRequiresConfirmedAppointmentAndIsAudited() throws Exception {
        Fixture fixture = fixture(true, Instant.now().minusSeconds(16 * 60L));
        MockHttpSession receptionist = login(fixture.receptionist());
        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/no-show")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        postAction(receptionist, "/api/appointments/" + fixture.appointment().getPublicId() + "/check-in")
                .andExpect(status().isConflict());
        assertEquals(1, statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointment().getPublicId()).size());
    }

    @Test
    @Transactional
    void checkInEnforcesThirtyMinuteOpeningAndServiceDateCutoff() throws Exception {
        Instant now = Instant.now();
        Fixture tooEarly = fixture(true, now.plusSeconds(31 * 60L));
        Fixture inWindow = fixture(true, now.plusSeconds(29 * 60L), tooEarly.doctor(), tooEarly.receptionist());
        Fixture expiredServiceDate = fixture(true,
                LocalDate.now(ZONE).minusDays(1).atTime(12, 0).atZone(ZONE).toInstant(),
                tooEarly.doctor(), tooEarly.receptionist());
        MockHttpSession receptionist = login(tooEarly.receptionist());

        postAction(receptionist, "/api/appointments/" + tooEarly.appointment().getPublicId() + "/check-in")
                .andExpect(status().isConflict());
        postAction(receptionist, "/api/appointments/" + expiredServiceDate.appointment().getPublicId() + "/check-in")
                .andExpect(status().isConflict());
        postAction(receptionist, "/api/appointments/" + inWindow.appointment().getPublicId() + "/check-in")
                .andExpect(status().isOk()).andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCheckInsAllocateUniqueQueuePositions() throws Exception {
        Fixture first = fixture(true, Instant.now().plusSeconds(900));
        Fixture second = fixture(true, Instant.now().plusSeconds(1200), first.doctor(), first.receptionist());
        MockHttpSession receptionist = login(first.receptionist());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> one = executor.submit(() -> concurrentCheckIn(receptionist, first.appointment(), ready, fire));
            Future<Integer> two = executor.submit(() -> concurrentCheckIn(receptionist, second.appointment(), ready, fire));
            ready.await();
            fire.countDown();
            List<Integer> results = new ArrayList<>(List.of(one.get(), two.get()));
            assertEquals(List.of(200, 200), results.stream().sorted().toList());
            LocalDate serviceDate = LocalDateTime.ofInstant(first.appointment().getStartsAt(), ZONE).toLocalDate();
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT queue_position) FROM appointments WHERE service_date = ?",
                    Integer.class, serviceDate));
            assertEquals(2, jdbc.queryForObject("SELECT MAX(queue_position) FROM appointments WHERE service_date = ?",
                    Integer.class, serviceDate));
            mvc.perform(get(queuePath(first.doctor(), serviceDate)).session(receptionist))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].queuePosition").value(1))
                    .andExpect(jsonPath("$[1].queuePosition").value(2));
        } finally {
            jdbc.update("DELETE FROM appointment_status_history");
            appointments.deleteAll();
            assignments.deleteAll();
            patients.deleteAll();
            doctors.deleteAll();
            departments.deleteAll();
            hospitals.deleteAll();
            users.deleteAll();
        }
    }

    private int concurrentCheckIn(MockHttpSession session, Appointment appointment,
                                  CountDownLatch ready, CountDownLatch fire) throws Exception {
        Csrf token = csrf(session);
        ready.countDown();
        fire.await();
        return mvc.perform(post("/api/appointments/{id}/check-in", appointment.getPublicId()).session(session)
                        .cookie(token.cookie()).header("X-XSRF-TOKEN", token.value()))
                .andReturn().getResponse().getStatus();
    }

    private Fixture fixture(boolean assigned, Instant start) { return fixture(assigned, start, null); }

    private Fixture fixture(boolean assigned, Instant start, UserAccount doctorUser) {
        UserAccount patientUser = account(UserAccount.Role.PATIENT);
        Patient patient = patients.saveAndFlush(new Patient(patientUser, null, null));
        UserAccount receptionist = account(UserAccount.Role.RECEPTIONIST);
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Queue Hospital " + SEQUENCE.incrementAndGet(), null, null, null, null, null));
        Department department = departments.saveAndFlush(new Department(hospital, "General " + SEQUENCE.incrementAndGet(), null));
        Doctor doctor = new Doctor(department, "Queue Doctor", null, null);
        doctor.assignUser(doctorUser);
        doctor = doctors.saveAndFlush(doctor);
        if (assigned) assignments.saveAndFlush(new ReceptionistHospitalAssignment(receptionist, hospital));
        Appointment appointment = appointments.saveAndFlush(new Appointment(patient, doctor, start, start.plusSeconds(1800), Appointment.Status.CONFIRMED));
        return new Fixture(patientUser, receptionist, doctor, appointment);
    }

    private Fixture fixture(boolean assigned, Instant start, Doctor doctor, UserAccount receptionist) {
        UserAccount patientUser = account(UserAccount.Role.PATIENT);
        Patient patient = patients.saveAndFlush(new Patient(patientUser, null, null));
        Appointment appointment = appointments.saveAndFlush(new Appointment(patient, doctor, start, start.plusSeconds(1800), Appointment.Status.CONFIRMED));
        return new Fixture(patientUser, receptionist, doctor, appointment);
    }

    private UserAccount account(UserAccount.Role role) {
        return users.saveAndFlush(new UserAccount("queue-" + SEQUENCE.incrementAndGet() + "@example.test", encoder.encode(PASSWORD), role));
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        return com.camillus.clinic.TestSessions.forAccount(account);
    }

    private org.springframework.test.web.servlet.ResultActions postAction(MockHttpSession session, String path) throws Exception {
        Csrf token = csrf(session);
        return mvc.perform(post(path).session(session).cookie(token.cookie()).header("X-XSRF-TOKEN", token.value()));
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Csrf(JsonPath.read(result.getResponse().getContentAsString(), "$.token"), result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private String queuePath(Doctor doctor, LocalDate date) { return "/api/doctors/" + doctor.getPublicId() + "/queue?serviceDate=" + date; }
    private record Fixture(UserAccount patient, UserAccount receptionist, Doctor doctor, Appointment appointment) {}
    private record Csrf(String value, Cookie cookie) {}
}
