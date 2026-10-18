package com.camillus.clinic.hospital;

import com.camillus.clinic.TestSessions;
import com.camillus.clinic.identity.UserAccount;
import com.camillus.clinic.identity.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorManagementApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;

    @Test
    void adminCreatesDoctorInSelectedHospitalAndPublicListingShowsSavedProfile() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Doctor creation hospital", null, null, null, null, null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);
        MvcResult departmentResult = mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cardiology\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String departmentId = JsonPath.read(departmentResult.getResponse().getContentAsString(), "$.id");
        Department department = departments.findByPublicIdIgnoreCase(departmentId).orElseThrow();

        MvcResult result = mvc.perform(post("/api/admin/doctors")
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hospitalId":"%s","departmentId":"%s","name":"Dr. Example","email":"doctor-example@example.test",
                                 "specialization":"Cardiology","experienceYears":8,"appointmentDurationMinutes":25}
                                """.formatted(hospital.getPublicId(), department.getPublicId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dr. Example"))
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(25))
                .andExpect(jsonPath("$.hospitalId").value(hospital.getPublicId()))
                .andReturn();

        String doctorId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Doctor savedDoctor = doctors.findByPublicIdIgnoreCase(doctorId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(department.getPublicId(), savedDoctor.getDepartment().getPublicId());
        org.junit.jupiter.api.Assertions.assertEquals("doctor-example@example.test", savedDoctor.getUser().getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(UserAccount.Role.DOCTOR, savedDoctor.getUser().getRole());
        org.junit.jupiter.api.Assertions.assertNotEquals("not-used-by-test", savedDoctor.getUser().getPasswordHash());
        mvc.perform(get("/api/hospitals/{hospitalId}/doctors", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doctorId))
                .andExpect(jsonPath("$[0].departmentId").value(department.getPublicId()));
    }

    @Test
    void linkedDoctorCanUpdateOwnPublicProfileFields() throws Exception {
        UserAccount account = users.save(new UserAccount("doctor-profile-" + System.nanoTime() + "@example.test",
                "not-used-by-test", UserAccount.Role.DOCTOR));
        Hospital hospital = hospitals.save(new Hospital("Profile update hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "Medicine", null));
        Doctor doctor = new Doctor(department, "Original name", "General practice", 2);
        doctor.assignUser(account);
        doctors.saveAndFlush(doctor);
        MockHttpSession session = TestSessions.forAccount(account);
        Csrf csrf = fetchCsrf(session);

        mvc.perform(put("/api/doctors/me").session(session).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dr. Updated\",\"specialization\":\"Cardiology\",\"experienceYears\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doctor.getPublicId()))
                .andExpect(jsonPath("$.name").value("Dr. Updated"))
                .andExpect(jsonPath("$.specialization").value("Cardiology"))
                .andExpect(jsonPath("$.experienceYears").value(7))
                .andExpect(jsonPath("$.hospitalId").value(hospital.getPublicId()));
    }

    @Test
    void administratorCanSafelyLinkAnExistingDoctorAccountToAnUnlinkedProfile() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Account linking hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "Medicine", null));
        UserAccount candidate = users.save(new UserAccount("unlinked-doctor-" + System.nanoTime() + "@example.test",
                "not-used-by-test", UserAccount.Role.DOCTOR));
        UserAccount alreadyLinked = users.save(new UserAccount("linked-doctor-" + System.nanoTime() + "@example.test",
                "not-used-by-test", UserAccount.Role.DOCTOR));
        UserAccount patient = users.save(new UserAccount("patient-" + System.nanoTime() + "@example.test",
                "not-used-by-test", UserAccount.Role.PATIENT));
        Doctor unlinkedProfile = doctors.save(new Doctor(department, "Existing unlinked profile", null, null));
        Doctor linkedProfile = new Doctor(department, "Existing linked profile", null, null);
        linkedProfile.assignUser(alreadyLinked);
        doctors.save(linkedProfile);
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(get("/api/admin/doctor-accounts/unlinked"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/doctor-accounts/unlinked").session(TestSessions.forAccount(patient)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/doctor-accounts/unlinked").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].publicId").value(candidate.getPublicId()))
                .andExpect(jsonPath("$[0].email").value(candidate.getEmail()))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mvc.perform(put("/api/admin/doctors/{doctorId}/account/{userId}",
                        unlinkedProfile.getPublicId(), candidate.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/doctors/me").session(TestSessions.forAccount(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unlinkedProfile.getPublicId()))
                .andExpect(jsonPath("$.name").value("Existing unlinked profile"));
        mvc.perform(put("/api/admin/doctors/{doctorId}/account/{userId}",
                        linkedProfile.getPublicId(), candidate.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/doctors/me").session(TestSessions.forAccount(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unlinkedProfile.getPublicId()));
        mvc.perform(get("/api/admin/doctor-accounts/unlinked").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void administratorCanCreateAndLinkAnAccountForAnExistingDoctorProfile() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Legacy doctor hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "Medicine", null));
        Doctor existingDoctor = doctors.saveAndFlush(new Doctor(department, "Dr. Legacy", null, null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(post("/api/admin/doctors/{doctorId}/account", existingDoctor.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"verified-legacy-doctor@example.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").exists())
                .andExpect(jsonPath("$.email").value("verified-legacy-doctor@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Doctor linked = doctors.findByPublicIdIgnoreCase(existingDoctor.getPublicId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(linked.getUser());
        org.junit.jupiter.api.Assertions.assertEquals(UserAccount.Role.DOCTOR, linked.getUser().getRole());
        mvc.perform(get("/api/doctors/me").session(TestSessions.forAccount(linked.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingDoctor.getPublicId()))
                .andExpect(jsonPath("$.name").value("Dr. Legacy"));
        mvc.perform(get("/api/admin/doctor-accounts/unlinked").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void doctorCreationRequiresAdminIdentity() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Protected doctor hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "Medicine", null));
        Csrf csrf = fetchCsrf(null);
        String request = """
                {"hospitalId":"%s","departmentId":"%s","name":"Dr. Protected","email":"protected-doctor@example.test","appointmentDurationMinutes":30}
                """.formatted(hospital.getPublicId(), department.getPublicId());

        mvc.perform(post("/api/admin/doctors").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());

        MockHttpSession patient = session(UserAccount.Role.PATIENT);
        Csrf patientCsrf = fetchCsrf(patient);
        mvc.perform(post("/api/admin/doctors").session(patient).cookie(patientCsrf.cookie())
                        .header("X-XSRF-TOKEN", patientCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDepartmentThatBelongsToAnotherHospital() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Selected hospital", null, null, null, null, null));
        Hospital otherHospital = hospitals.save(new Hospital("Other hospital", null, null, null, null, null));
        Department department = departments.save(new Department(otherHospital, "Medicine", null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(post("/api/admin/doctors").session(admin).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hospitalId":"%s","departmentId":"%s","name":"Dr. Wrong hospital","email":"wrong-hospital@example.test","appointmentDurationMinutes":30}
                                """.formatted(hospital.getPublicId(), department.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Department does not belong to selected hospital"));
    }

    @Test
    void validatesRequiredDoctorFieldsAndDuration() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Validation hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "Medicine", null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(post("/api/admin/doctors").session(admin).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hospitalId":"%s","departmentId":"%s","name":"   ","appointmentDurationMinutes":0}
                                """.formatted(hospital.getPublicId(), department.getPublicId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid doctor profile"));
    }

    private MockHttpSession session(UserAccount.Role role) {
        UserAccount account = users.save(new UserAccount("doctor-management-" + role.name().toLowerCase() + "-"
                + System.nanoTime() + "@example.test", "not-used-by-test", role));
        return TestSessions.forAccount(account);
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Csrf(JsonPath.read(result.getResponse().getContentAsString(), "$.token"),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private record Csrf(String token, Cookie cookie) {}
}
