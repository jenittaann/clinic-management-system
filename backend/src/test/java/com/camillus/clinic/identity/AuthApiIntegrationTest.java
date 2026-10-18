package com.camillus.clinic.identity;

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
import org.springframework.transaction.annotation.Transactional;
import com.camillus.clinic.hospital.Department;
import com.camillus.clinic.hospital.DepartmentRepository;
import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.Hospital;
import com.camillus.clinic.hospital.HospitalRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTest {
    private static final String EMAIL = "auth-test@example.test";
    private static final String PASSWORD = "test-password-7491";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PatientRepository patients;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired ReceptionistHospitalAssignmentRepository assignments;

    @Test
    void logsInWithStoredPasswordAndReturnsOnlySafeIdentityFields() throws Exception {
        UserAccount account = createTestAccount();
        Csrf csrf = fetchCsrf();

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"AUTH-TEST@example.test\",\"password\":\"test-password-7491\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getPublicId()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
    }

    @Test
    void rejectsInvalidCredentialsWithoutSensitiveDetails() throws Exception {
        createTestAccount();
        Csrf csrf = fetchCsrf();

        mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"auth-test@example.test\",\"password\":\"wrong-password-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void returnsCurrentIdentityForAuthenticatedSession() throws Exception {
        UserAccount account = createTestAccount();
        MockHttpSession session = loginAndGetSession();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getPublicId()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsMeWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void logoutInvalidatesSessionAndRequiresCsrf() throws Exception {
        createTestAccount();
        MockHttpSession session = loginAndGetSession();
        Csrf csrf = fetchCsrf(session);

        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfTokenForLogin() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"irrelevant-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void emailSignInCreatesPatientSessionOnlyForExistingLinkedPatientAccount() throws Exception {
        UserAccount patient = users.saveAndFlush(new UserAccount("direct-patient@example.test",
                passwordEncoder.encode("unused"), UserAccount.Role.PATIENT));
        patients.saveAndFlush(new Patient(patient, null, null));
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Direct sign-in hospital", null, null, null, null, null));
        Csrf csrf = fetchCsrf();

        MvcResult result = mvc.perform(post("/api/auth/email-sign-in")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"direct-patient@example.test\",\"role\":\"PATIENT\",\"hospitalId\":\"" + hospital.getPublicId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        mvc.perform(get("/api/patients/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void registersNewPatientWithoutEmailVerificationAndCreatesAuthenticatedSession() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Patient registration hospital", null, null, null, null, null));
        Csrf csrf = fetchCsrf();

        MvcResult result = mvc.perform(post("/api/auth/patient-register")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new-patient@example.test\",\"hospitalId\":\"" + hospital.getPublicId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-patient@example.test"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn();

        UserAccount created = users.findByEmailIgnoreCase("new-patient@example.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(patients.findByUser_PublicIdIgnoreCase(created.getPublicId()).isPresent());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        mvc.perform(get("/api/patients/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void receptionistEmailRegistrationCreatesOneAccountScopedToSelectedHospital() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Receptionist registration hospital", null, null, null, null, null));
        Hospital otherHospital = hospitals.saveAndFlush(new Hospital("Other receptionist hospital", null, null, null, null, null));
        String email = "new-receptionist@example.test";
        Csrf csrf = fetchCsrf();
        String request = "{\"email\":\"" + email + "\",\"hospitalId\":\"" + hospital.getPublicId() + "\"}";

        MvcResult first = mvc.perform(post("/api/auth/receptionist-register")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        UserAccount account = users.findByEmailIgnoreCase(email).orElseThrow();
        MockHttpSession firstSession = (MockHttpSession) first.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(firstSession);
        org.junit.jupiter.api.Assertions.assertTrue(assignments
                .existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(
                        account.getPublicId(), hospital.getPublicId()));
        org.junit.jupiter.api.Assertions.assertFalse(assignments
                .existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(
                        account.getPublicId(), otherHospital.getPublicId()));
        mvc.perform(get("/api/patients?page=0&size=10").session(firstSession)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/receptionist-register")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getPublicId()));
        org.junit.jupiter.api.Assertions.assertEquals(1, users.findAll().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email)).count());
        org.junit.jupiter.api.Assertions.assertEquals(1, assignments.findAll().stream()
                .filter(assignment -> assignment.getReceptionist().getPublicId().equalsIgnoreCase(account.getPublicId()))
                .count());

        mvc.perform(post("/api/auth/receptionist-register")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"hospitalId\":\"" + otherHospital.getPublicId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not assigned to the selected hospital")));
        org.junit.jupiter.api.Assertions.assertEquals(1, users.findAll().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email)).count());
    }

    @Test
    void patientRegistrationRejectsEmailAlreadyOwnedByAnotherRoleWithoutChangingAccount() throws Exception {
        UserAccount doctor = users.saveAndFlush(new UserAccount("existing-doctor@example.test",
                passwordEncoder.encode("unused"), UserAccount.Role.DOCTOR));
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Role mismatch hospital", null, null, null, null, null));
        Csrf csrf = fetchCsrf();

        mvc.perform(post("/api/auth/patient-register")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"existing-doctor@example.test\",\"hospitalId\":\"" + hospital.getPublicId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("different account role")));

        org.junit.jupiter.api.Assertions.assertEquals(UserAccount.Role.DOCTOR,
                users.findByEmailIgnoreCase("existing-doctor@example.test").orElseThrow().getRole());
        org.junit.jupiter.api.Assertions.assertFalse(patients.findByUser_PublicIdIgnoreCase(doctor.getPublicId()).isPresent());
    }

    @Test
    void emailSignInRequiresMatchingDoctorOrReceptionistLinkageAndRejectsRoleSwitches() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Staff sign-in hospital", null, null, null, null, null));
        Hospital otherHospital = hospitals.saveAndFlush(new Hospital("Other sign-in hospital", null, null, null, null, null));
        Department department = departments.saveAndFlush(new Department(hospital, "General Medicine", null));
        UserAccount doctorAccount = users.saveAndFlush(new UserAccount("direct-doctor@example.test",
                passwordEncoder.encode("unused"), UserAccount.Role.DOCTOR));
        Doctor doctor = new Doctor(department, "Direct Doctor", null, null);
        doctor.assignUser(doctorAccount);
        doctors.saveAndFlush(doctor);
        UserAccount receptionist = users.saveAndFlush(new UserAccount("direct-receptionist@example.test",
                passwordEncoder.encode("unused"), UserAccount.Role.RECEPTIONIST));
        assignments.saveAndFlush(new ReceptionistHospitalAssignment(receptionist, hospital));
        UserAccount admin = users.saveAndFlush(new UserAccount("direct-admin@example.test",
                passwordEncoder.encode("unused"), UserAccount.Role.ADMIN));
        Csrf csrf = fetchCsrf();

        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("direct-doctor@example.test", "DOCTOR", hospital.getPublicId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DOCTOR"));
        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("direct-receptionist@example.test", "RECEPTIONIST", hospital.getPublicId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("RECEPTIONIST"));
        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("direct-doctor@example.test", "PATIENT", hospital.getPublicId())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("direct-doctor@example.test", "DOCTOR", otherHospital.getPublicId())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("direct-admin@example.test", "ADMIN", hospital.getPublicId())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-sign-in").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest("missing@example.test", "PATIENT", hospital.getPublicId())))
                .andExpect(status().isUnauthorized());
    }

    private String emailRequest(String email, String role, String hospitalId) {
        return "{\"email\":\"" + email + "\",\"role\":\"" + role + "\",\"hospitalId\":\"" + hospitalId + "\"}";
    }

    @Test
    void permitsOnlySpecifiedPublicReadRoutesAndProtectsOtherApiRoutes() throws Exception {
        mvc.perform(get("/api/hospitals")).andExpect(status().isOk());
        mvc.perform(get("/api/hospitals/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee/departments"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/doctors")).andExpect(status().isOk());
        mvc.perform(get("/api/hospitals/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee/doctors"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/appointments")).andExpect(status().isForbidden());
    }

    private UserAccount createTestAccount() {
        return users.save(new UserAccount(EMAIL, passwordEncoder.encode(PASSWORD), UserAccount.Role.PATIENT));
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        Csrf csrf = fetchCsrf();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"auth-test@example.test\",\"password\":\"test-password-7491\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf fetchCsrf() throws Exception {
        return fetchCsrf(null);
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        return new Csrf(token, cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
