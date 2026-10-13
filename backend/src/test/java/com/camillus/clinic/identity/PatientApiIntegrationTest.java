package com.camillus.clinic.identity;

import com.jayway.jsonpath.JsonPath;
import com.camillus.clinic.appointment.Appointment;
import com.camillus.clinic.appointment.AppointmentRepository;
import com.camillus.clinic.hospital.Department;
import com.camillus.clinic.hospital.DepartmentRepository;
import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.Hospital;
import com.camillus.clinic.hospital.HospitalRepository;
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
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientApiIntegrationTest {
    private static final String PASSWORD = "patient-api-test-7842";
    private static final AtomicInteger EMAIL_COUNTER = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
    @Autowired ReceptionistHospitalAssignmentRepository assignments;

    @Test
    void rejectsAnonymousPatientRequests() throws Exception {
        mvc.perform(get("/api/patients/me")).andExpect(status().isUnauthorized());
        Csrf csrf = fetchCsrf();
        mvc.perform(put("/api/patients/me")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCanCreateAndReadOnlyTheirOwnProfile() throws Exception {
        UserAccount patientAccount = createAccount(UserAccount.Role.PATIENT);
        UserAccount anotherAccount = createAccount(UserAccount.Role.PATIENT);
        MockHttpSession session = login(patientAccount);
        Csrf csrf = fetchCsrf();

        mvc.perform(put("/api/patients/me")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"   \",\"dateOfBirth\":\"1990-03-14\",\"userId\":999,\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientAccount.getPublicId()))
                .andExpect(jsonPath("$.email").value(patientAccount.getEmail()))
                .andExpect(jsonPath("$.phone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-03-14"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/patients/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientAccount.getPublicId()))
                .andExpect(jsonPath("$.email").value(patientAccount.getEmail()));

        mvc.perform(get("/api/patients").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/{patientId}", anotherAccount.getPublicId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void putUpdatesExistingProfileInsteadOfCreatingDuplicates() throws Exception {
        UserAccount account = createAccount(UserAccount.Role.PATIENT);
        Patient original = patients.save(new Patient(account, "+91 98765 43210", LocalDate.of(1988, 1, 2)));
        MockHttpSession session = login(account);
        Csrf csrf = fetchCsrf();

        mvc.perform(put("/api/patients/me")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\" +91 90000 11111 \",\"dateOfBirth\":\"1988-01-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(account.getPublicId()))
                .andExpect(jsonPath("$.phone").value("+91 90000 11111"));

        org.junit.jupiter.api.Assertions.assertEquals(1, patients.count());
        org.junit.jupiter.api.Assertions.assertNotNull(original.getUser());
    }

    @Test
    void patientReceivesNotFoundWhenProfileDoesNotExist() throws Exception {
        MockHttpSession session = login(createAccount(UserAccount.Role.PATIENT));
        mvc.perform(get("/api/patients/me").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Patient profile not found"));
    }

    @Test
    void receptionistAccessIsScopedToAssignedHospitalAppointmentsAndAdminIsGlobal() throws Exception {
        UserAccount assignedPatientAccount = createAccount(UserAccount.Role.PATIENT);
        UserAccount unrelatedPatientAccount = createAccount(UserAccount.Role.PATIENT);
        UserAccount noAppointmentPatientAccount = createAccount(UserAccount.Role.PATIENT);
        Patient assignedPatient = patients.save(new Patient(assignedPatientAccount, "111", null));
        Patient unrelatedPatient = patients.save(new Patient(unrelatedPatientAccount, "222", null));
        patients.save(new Patient(noAppointmentPatientAccount, "333", null));

        Hospital assignedHospital = createHospital("Assigned");
        Hospital secondAssignedHospital = createHospital("Second Assigned");
        Hospital unassignedHospital = createHospital("Unassigned");
        UserAccount receptionist = createAccount(UserAccount.Role.RECEPTIONIST);
        assignments.save(new ReceptionistHospitalAssignment(receptionist, assignedHospital));
        assignments.save(new ReceptionistHospitalAssignment(receptionist, secondAssignedHospital));

        Doctor assignedDoctor = createDoctor(assignedHospital);
        Doctor secondAssignedDoctor = createDoctor(secondAssignedHospital);
        Doctor unrelatedDoctor = createDoctor(unassignedHospital);
        Instant start = Instant.parse("2030-03-14T04:00:00Z");
        appointments.save(new Appointment(assignedPatient, assignedDoctor, start, start.plusSeconds(1800), Appointment.Status.CONFIRMED));
        appointments.save(new Appointment(assignedPatient, assignedDoctor, start.plusSeconds(3600), start.plusSeconds(5400), Appointment.Status.CONFIRMED));
        appointments.save(new Appointment(assignedPatient, secondAssignedDoctor, start.plusSeconds(7200), start.plusSeconds(9000), Appointment.Status.CONFIRMED));
        appointments.save(new Appointment(unrelatedPatient, unrelatedDoctor, start, start.plusSeconds(1800), Appointment.Status.CONFIRMED));

        MockHttpSession receptionistSession = login(receptionist);
        mvc.perform(get("/api/patients?page=0&size=1").session(receptionistSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patientId").value(assignedPatientAccount.getPublicId()))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/patients/{patientId}", assignedPatientAccount.getPublicId()).session(receptionistSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(assignedPatientAccount.getPublicId()));
        mvc.perform(get("/api/patients/{patientId}", unrelatedPatientAccount.getPublicId()).session(receptionistSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/patients/{patientId}", noAppointmentPatientAccount.getPublicId()).session(receptionistSession))
                .andExpect(status().isNotFound());

        MockHttpSession adminSession = login(createAccount(UserAccount.Role.ADMIN));
        mvc.perform(get("/api/patients?page=0&size=2").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/patients/{patientId}", unrelatedPatient.getUser().getPublicId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(unrelatedPatientAccount.getPublicId()))
                .andExpect(jsonPath("$.email").value(unrelatedPatientAccount.getEmail()))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void doctorCannotListOrViewPatientRecords() throws Exception {
        UserAccount patient = createAccount(UserAccount.Role.PATIENT);
        patients.save(new Patient(patient, "111", null));
        MockHttpSession session = login(createAccount(UserAccount.Role.DOCTOR));

        mvc.perform(get("/api/patients").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/{patientId}", patient.getPublicId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTooLongPhoneAndFutureDateOfBirth() throws Exception {
        MockHttpSession session = login(createAccount(UserAccount.Role.PATIENT));
        Csrf csrf = fetchCsrf();

        mvc.perform(put("/api/patients/me")
                        .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123456789012345678901234567890123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid patient profile"));

        Csrf newCsrf = fetchCsrf();
        mvc.perform(put("/api/patients/me")
                        .session(session).cookie(newCsrf.cookie()).header("X-XSRF-TOKEN", newCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid patient profile"));
    }

    @Test
    void validatesPatientPublicUuidAndPaginationBounds() throws Exception {
        MockHttpSession staffSession = login(createAccount(UserAccount.Role.ADMIN));
        mvc.perform(get("/api/patients/not-a-uuid").session(staffSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("patientId must be a valid UUID"));
        mvc.perform(get("/api/patients?page=-1").session(staffSession))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/patients?size=101").session(staffSession))
                .andExpect(status().isBadRequest());
    }

    private UserAccount createAccount(UserAccount.Role role) {
        String email = "patient-api-" + EMAIL_COUNTER.incrementAndGet() + "@example.test";
        return users.save(new UserAccount(email, passwordEncoder.encode(PASSWORD), role));
    }

    private Hospital createHospital(String label) {
        return hospitals.save(new Hospital("Patient scope " + label + " " + EMAIL_COUNTER.incrementAndGet(), null, null, null, null, null));
    }

    private Doctor createDoctor(Hospital hospital) {
        Department department = departments.save(new Department(hospital, "Department " + EMAIL_COUNTER.incrementAndGet(), null));
        return doctors.save(new Doctor(department, "Doctor " + EMAIL_COUNTER.incrementAndGet(), null, null));
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        Csrf csrf = fetchCsrf();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf fetchCsrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        return new Csrf(token, cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
