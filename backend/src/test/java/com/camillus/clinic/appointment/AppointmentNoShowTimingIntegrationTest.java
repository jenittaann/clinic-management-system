package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.Department;
import com.camillus.clinic.hospital.DepartmentRepository;
import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.Hospital;
import com.camillus.clinic.hospital.HospitalRepository;
import com.camillus.clinic.identity.Patient;
import com.camillus.clinic.identity.PatientRepository;
import com.camillus.clinic.identity.ReceptionistHospitalAssignment;
import com.camillus.clinic.identity.ReceptionistHospitalAssignmentRepository;
import com.camillus.clinic.identity.UserAccount;
import com.camillus.clinic.identity.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentNoShowTimingIntegrationTest {
    private static final String PASSWORD = "no-show-test-password";
    private static final Instant NOW = Instant.parse("2035-01-01T00:00:00Z");
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
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean Clock clock;

    @BeforeEach
    void fixApplicationTime() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void rejectsNoShowBeforeAppointmentStartAndDoesNotWriteHistory() throws Exception {
        Fixture fixture = fixture(NOW.plusSeconds(1), UserAccount.Role.RECEPTIONIST);

        noShow(login(fixture.actor()), fixture.appointment()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Appointment is not yet eligible to be marked no-show"));

        assertUnchangedAndUnaudited(fixture.appointment());
    }

    @Test
    void rejectsNoShowBeforeFifteenMinuteThreshold() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(14 * 60L + 59), UserAccount.Role.RECEPTIONIST);

        noShow(login(fixture.actor()), fixture.appointment()).andExpect(status().isConflict());

        assertUnchangedAndUnaudited(fixture.appointment());
    }

    @Test
    void permitsNoShowExactlyAtFifteenMinuteThresholdAndRecordsHistory() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(15 * 60L), UserAccount.Role.RECEPTIONIST);

        noShow(login(fixture.actor()), fixture.appointment()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertEquals(Appointment.Status.NO_SHOW, persisted(fixture.appointment()).getStatus());
        var history = statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointment().getPublicId());
        assertEquals(1, history.size());
        assertEquals(Appointment.Status.CONFIRMED, history.getFirst().getOldStatus());
        assertEquals(Appointment.Status.NO_SHOW, history.getFirst().getNewStatus());
        assertEquals(NOW, history.getFirst().getChangedAt());
    }

    @Test
    void permitsNoShowAfterFifteenMinuteThreshold() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(15 * 60L + 1), UserAccount.Role.RECEPTIONIST);

        noShow(login(fixture.actor()), fixture.appointment()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void rejectsInvalidAppointmentStatusWithoutChangingIt() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(30 * 60L), UserAccount.Role.RECEPTIONIST);
        fixture.appointment().transitionTo(Appointment.Status.WAITING);
        appointments.flush();

        noShow(login(fixture.actor()), fixture.appointment()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Appointment status transition is not allowed"));

        assertEquals(Appointment.Status.WAITING, persisted(fixture.appointment()).getStatus());
        assertEquals(0, statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                fixture.appointment().getPublicId()).size());
    }

    @Test
    void onlyReceptionistAssignedDoctorAndAdminCanMarkNoShow() throws Exception {
        Fixture receptionistFixture = fixture(NOW.minusSeconds(20 * 60L), UserAccount.Role.RECEPTIONIST);
        noShow(login(receptionistFixture.actor()), receptionistFixture.appointment()).andExpect(status().isOk());

        Fixture doctorFixture = fixture(NOW.minusSeconds(20 * 60L), UserAccount.Role.DOCTOR);
        doctorFixture.doctor().assignUser(doctorFixture.actor());
        doctors.saveAndFlush(doctorFixture.doctor());
        noShow(login(doctorFixture.actor()), doctorFixture.appointment()).andExpect(status().isOk());

        Fixture adminFixture = fixture(NOW.minusSeconds(20 * 60L), UserAccount.Role.ADMIN);
        noShow(login(adminFixture.actor()), adminFixture.appointment()).andExpect(status().isOk());

        Fixture protectedFixture = fixture(NOW.minusSeconds(20 * 60L), UserAccount.Role.RECEPTIONIST);
        UserAccount unassignedReceptionist = account(UserAccount.Role.RECEPTIONIST);
        noShow(login(unassignedReceptionist), protectedFixture.appointment()).andExpect(status().isForbidden());
        noShow(login(account(UserAccount.Role.DOCTOR)), protectedFixture.appointment()).andExpect(status().isForbidden());
        noShow(login(account(UserAccount.Role.PATIENT)), protectedFixture.appointment()).andExpect(status().isForbidden());
        assertUnchangedAndUnaudited(protectedFixture.appointment());
    }

    private Fixture fixture(Instant startsAt, UserAccount.Role actorRole) {
        UserAccount patientAccount = account(UserAccount.Role.PATIENT);
        Patient patient = patients.saveAndFlush(new Patient(patientAccount, null, null));
        UserAccount actor = account(actorRole);
        Hospital hospital = hospitals.saveAndFlush(new Hospital("No-show Hospital " + SEQUENCE.incrementAndGet(), null, null, null, null, null));
        Department department = departments.saveAndFlush(new Department(hospital, "General " + SEQUENCE.incrementAndGet(), null));
        Doctor doctor = doctors.saveAndFlush(new Doctor(department, "Doctor " + SEQUENCE.incrementAndGet(), null, null));
        if (actorRole == UserAccount.Role.RECEPTIONIST)
            assignments.saveAndFlush(new ReceptionistHospitalAssignment(actor, hospital));
        Appointment appointment = appointments.saveAndFlush(new Appointment(patient, doctor, startsAt,
                startsAt.plusSeconds(1800), Appointment.Status.CONFIRMED));
        return new Fixture(actor, doctor, appointment);
    }

    private void assertUnchangedAndUnaudited(Appointment appointment) {
        assertEquals(Appointment.Status.CONFIRMED, persisted(appointment).getStatus());
        assertEquals(0, statusHistory.findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(
                appointment.getPublicId()).size());
    }

    private Appointment persisted(Appointment appointment) {
        return appointments.findByPublicIdIgnoreCase(appointment.getPublicId()).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions noShow(MockHttpSession session, Appointment appointment)
            throws Exception {
        Csrf csrf = csrf(session);
        return mvc.perform(post("/api/appointments/{id}/no-show", appointment.getPublicId()).session(session)
                .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()));
    }

    private UserAccount account(UserAccount.Role role) {
        return users.saveAndFlush(new UserAccount("no-show-" + SEQUENCE.incrementAndGet() + "@example.test",
                passwordEncoder.encode(PASSWORD), role));
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        return com.camillus.clinic.TestSessions.forAccount(account);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Csrf(JsonPath.read(result.getResponse().getContentAsString(), "$.token"),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private record Fixture(UserAccount actor, Doctor doctor, Appointment appointment) {}
    private record Csrf(String token, Cookie cookie) {}
}
