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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class AppointmentApiIntegrationTest {
    private static final String PASSWORD = "appointment-test-password";
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
    @Autowired ReceptionistHospitalAssignmentRepository assignments;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void booksConfirmedAppointmentInsideRuleAtAnchoredInterval() throws Exception {
        UserAccount patientUser = account(UserAccount.Role.PATIENT);
        patients.save(new Patient(patientUser, null, null));
        Doctor doctor = doctor("Cardiology", 30, 9, 12);
        MockHttpSession session = login(patientUser);
        LocalDateTime start = futureWeekday(doctorDay(doctor), 9, 0);
        Csrf token = csrf(session);

        mvc.perform(post("/api/appointments").session(session).cookie(token.cookie())
                        .header("X-XSRF-TOKEN", token.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingJson(doctor, start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").exists())
                .andExpect(jsonPath("$.doctorId").value(doctor.getPublicId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startsAt").value(start.atZone(ZONE).toInstant().toString()))
                .andExpect(jsonPath("$.endsAt").value(start.plusMinutes(30).atZone(ZONE).toInstant().toString()))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertEquals(1, appointments.count());
    }

    @Test
    @Transactional
    void rejectsOutsideAvailabilityAndNonAnchoredStarts() throws Exception {
        UserAccount patientUser = account(UserAccount.Role.PATIENT);
        patients.save(new Patient(patientUser, null, null));
        Doctor doctor = doctor("Medicine", 30, 9, 11);
        MockHttpSession session = login(patientUser);

        postBooking(session, doctor, futureWeekday(doctorDay(doctor), 11, 0))
                .andExpect(status().isConflict());
        postBooking(session, doctor, futureWeekday(doctorDay(doctor), 9, 15))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Requested time is outside doctor availability or fixed appointment intervals"));
        assertEquals(0, appointments.count());
    }

    @Test
    @Transactional
    void rejectsMissingDurationPastTimeAndMalformedDoctorUuid() throws Exception {
        UserAccount patientUser = account(UserAccount.Role.PATIENT);
        patients.save(new Patient(patientUser, null, null));
        Doctor doctor = doctor("Surgery", null, 9, 12);
        MockHttpSession session = login(patientUser);

        postBooking(session, doctor, futureWeekday(doctorDay(doctor), 9, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Doctor appointment duration is not configured"));
        String past = LocalDateTime.now(ZONE).minusDays(1).withSecond(0).withNano(0).toString();
        Csrf pastCsrf = csrf(session);
        mvc.perform(post("/api/appointments").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"" + doctor.getPublicId() + "\",\"startsAt\":\"" + past + "\"}")
                        .cookie(pastCsrf.cookie()).header("X-XSRF-TOKEN", pastCsrf.token()))
                .andExpect(status().isConflict()); // duration validation precedes the past-time check
        Doctor configured = doctor("Radiology", 30, 9, 12);
        postBooking(session, configured, LocalDateTime.now(ZONE).minusDays(1).withSecond(0).withNano(0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Appointment must be in the future"));
        Csrf malformedCsrf = csrf(session);
        mvc.perform(post("/api/appointments").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"bad-id\",\"startsAt\":\"" + futureWeekday(1, 9, 0) + "\"}")
                        .cookie(malformedCsrf.cookie()).header("X-XSRF-TOKEN", malformedCsrf.token()))
                .andExpect(status().isBadRequest());
        assertEquals(0, appointments.count());
    }

    @Test
    @Transactional
    void rejectsDoctorAndPatientOverlaps() throws Exception {
        UserAccount firstUser = account(UserAccount.Role.PATIENT);
        UserAccount secondUser = account(UserAccount.Role.PATIENT);
        patients.save(new Patient(firstUser, null, null));
        patients.save(new Patient(secondUser, null, null));
        Doctor firstDoctor = doctor("Cardiology", 30, 9, 12);
        Doctor secondDoctor = doctor("Neurology", 30, 9, 12);
        LocalDateTime start = futureWeekday(doctorDay(firstDoctor), 9, 0);
        MockHttpSession firstSession = login(firstUser);
        MockHttpSession secondSession = login(secondUser);

        postBooking(firstSession, firstDoctor, start).andExpect(status().isCreated());
        postBooking(secondSession, firstDoctor, start).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Doctor already has an overlapping appointment"));
        postBooking(firstSession, secondDoctor, start).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Patient already has an overlapping appointment"));
        assertEquals(1, appointments.count());
    }

    @Test
    @Transactional
    void enforcesPatientOwnershipAndDoctorReceptionistAdminScopes() throws Exception {
        UserAccount owner = account(UserAccount.Role.PATIENT);
        UserAccount otherPatient = account(UserAccount.Role.PATIENT);
        Patient patientProfile = patients.save(new Patient(owner, null, null));
        patients.save(new Patient(otherPatient, null, null));
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor("Oncology", 30, 9, 12);
        doctor.assignUser(doctorUser);
        doctors.saveAndFlush(doctor);
        MockHttpSession patientSession = login(owner);
        LocalDateTime start = futureWeekday(doctorDay(doctor), 9, 0);
        MvcResult booking = postBooking(patientSession, doctor, start).andExpect(status().isCreated()).andReturn();
        String appointmentId = JsonPath.read(booking.getResponse().getContentAsString(), "$.appointmentId");

        mvc.perform(get("/api/appointments/me").session(patientSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appointmentId").value(appointmentId));
        MockHttpSession otherPatientSession = login(otherPatient);
        mvc.perform(get("/api/appointments/me").session(otherPatientSession)).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/appointments/{id}", appointmentId).session(otherPatientSession)).andExpect(status().isForbidden());

        mvc.perform(get("/api/appointments/{id}", appointmentId).session(login(doctorUser))).andExpect(status().isOk());
        UserAccount unrelatedDoctor = account(UserAccount.Role.DOCTOR);
        mvc.perform(get("/api/appointments/{id}", appointmentId).session(login(unrelatedDoctor))).andExpect(status().isForbidden());

        UserAccount receptionist = account(UserAccount.Role.RECEPTIONIST);
        UserAccount unassignedReceptionist = account(UserAccount.Role.RECEPTIONIST);
        assignments.saveAndFlush(new ReceptionistHospitalAssignment(receptionist, doctor.getDepartment().getHospital()));
        mvc.perform(get("/api/appointments/{id}", appointmentId).session(login(receptionist))).andExpect(status().isOk());
        mvc.perform(get("/api/appointments/{id}", appointmentId).session(login(unassignedReceptionist))).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/{id}", appointmentId).session(login(account(UserAccount.Role.ADMIN)))).andExpect(status().isOk());
        mvc.perform(post("/api/appointments").session(login(doctorUser))).andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void anonymousRequestsAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/{id}", java.util.UUID.randomUUID())).andExpect(status().isUnauthorized());
        UserAccount patient = account(UserAccount.Role.PATIENT);
        patients.save(new Patient(patient, null, null));
        mvc.perform(post("/api/appointments").session(login(patient)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentOverlappingPatientBookingsAllowOnlyOneCommit() throws Exception {
        UserAccount patientOne = account(UserAccount.Role.PATIENT);
        UserAccount patientTwo = account(UserAccount.Role.PATIENT);
        patients.saveAndFlush(new Patient(patientOne, null, null));
        patients.saveAndFlush(new Patient(patientTwo, null, null));
        Doctor doctor = doctor("Concurrent Cardiology", 30, 9, 12);
        MockHttpSession sessionOne = login(patientOne);
        MockHttpSession sessionTwo = login(patientTwo);
        LocalDateTime start = futureWeekday(doctorDay(doctor), 9, 0);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentPost(sessionOne, doctor, start, ready, fire));
            Future<Integer> second = executor.submit(() -> concurrentPost(sessionTwo, doctor, start, ready, fire));
            ready.await();
            fire.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            assertEquals(1, statuses.stream().filter(code -> code == 201).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
            assertEquals(1, appointments.count());
        } finally {
            appointments.deleteAll();
            slots.deleteAll();
            doctors.deleteAll();
            assignments.deleteAll();
            departments.deleteAll();
            hospitals.deleteAll();
            patients.deleteAll();
            users.deleteAll();
        }
    }

    private int concurrentPost(MockHttpSession session, Doctor doctor, LocalDateTime start,
                               CountDownLatch ready, CountDownLatch fire) throws Exception {
        Csrf token = csrf(session);
        ready.countDown();
        fire.await();
        return mvc.perform(post("/api/appointments").session(session).cookie(token.cookie())
                        .header("X-XSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(doctor, start)))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions postBooking(MockHttpSession session, Doctor doctor,
                                                                           LocalDateTime start) throws Exception {
        Csrf token = csrf(session);
        return mvc.perform(post("/api/appointments").session(session).cookie(token.cookie())
                .header("X-XSRF-TOKEN", token.token()).contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(doctor, start)));
    }

    private String bookingJson(Doctor doctor, LocalDateTime start) {
        return "{\"doctorId\":\"" + doctor.getPublicId() + "\",\"startsAt\":\"" + start + "\",\"patientId\":999,\"role\":\"ADMIN\"}";
    }

    private Doctor doctor(String departmentName, Integer duration, int startHour, int endHour) {
        Hospital hospital = hospitals.save(new Hospital("Appointment Hospital " + EMAIL_COUNTER.incrementAndGet(), null, null, null, null, null));
        Department department = departments.save(new Department(hospital, departmentName, null));
        Doctor doctor = new Doctor(department, "Dr " + departmentName, null, null);
        doctor.setAppointmentDurationMinutes(duration);
        doctor = doctors.saveAndFlush(doctor);
        int day = futureWeekdayNumber();
        slots.saveAndFlush(new AvailabilitySlot(doctor, day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0)));
        return doctor;
    }

    private int doctorDay(Doctor ignored) { return futureWeekdayNumber(); }

    private int futureWeekdayNumber() {
        return LocalDate.now(ZONE).plusDays(1).getDayOfWeek().getValue();
    }

    private LocalDateTime futureWeekday(int weekday, int hour, int minute) {
        LocalDate date = LocalDate.now(ZONE).plusDays(1);
        while (date.getDayOfWeek().getValue() != weekday) date = date.plusDays(1);
        return date.atTime(hour, minute);
    }

    private UserAccount account(UserAccount.Role role) {
        return users.saveAndFlush(new UserAccount("appointment-" + EMAIL_COUNTER.incrementAndGet() + "@example.test",
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

    private Csrf csrf() throws Exception { return csrf(null); }
    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        String value = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return new Csrf(value, cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
