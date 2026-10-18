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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DepartmentManagementApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;

    @Test
    void adminCreatesDepartmentForExistingHospitalAndPublicEndpointReturnsIt() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Department setup hospital", null, null, null, null, null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardiology\",\"description\":\"Heart care\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cardiology"))
                .andExpect(jsonPath("$.description").value("Heart care"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mvc.perform(get("/api/hospitals/{hospitalId}/departments", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cardiology"));
        assertEquals(1, departments.findAllByHospital_PublicIdOrderByNameAsc(hospital.getPublicId()).size());
    }

    @Test
    void rejectsDuplicateDepartmentNamesWithinHospital() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Duplicate department hospital", null, null, null, null, null));
        departments.saveAndFlush(new Department(hospital, "Cardiology", null));
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);

        mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" cardiology \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Department already exists for this hospital"));
        assertEquals(1, departments.findAllByHospital_PublicIdOrderByNameAsc(hospital.getPublicId()).size());
    }

    @Test
    void rejectsMalformedAndUnknownHospitalPublicIds() throws Exception {
        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf csrf = fetchCsrf(admin);
        String body = "{\"name\":\"Medicine\"}";

        mvc.perform(post("/api/hospitals/{hospitalId}/departments", "bad-id")
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("hospitalId must be a valid UUID"));

        mvc.perform(post("/api/hospitals/{hospitalId}/departments", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
                        .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Hospital not found")));
    }

    @Test
    void departmentCreationRequiresAdminAuthorizationAndValidName() throws Exception {
        Hospital hospital = hospitals.saveAndFlush(new Hospital("Protected department hospital", null, null, null, null, null));
        Csrf anonymousCsrf = fetchCsrf(null);
        String body = "{\"name\":\"Medicine\"}";

        mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .cookie(anonymousCsrf.cookie()).header("X-XSRF-TOKEN", anonymousCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        MockHttpSession patient = session(UserAccount.Role.PATIENT);
        Csrf patientCsrf = fetchCsrf(patient);
        mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .session(patient).cookie(patientCsrf.cookie()).header("X-XSRF-TOKEN", patientCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        MockHttpSession admin = session(UserAccount.Role.ADMIN);
        Csrf adminCsrf = fetchCsrf(admin);
        mvc.perform(post("/api/hospitals/{hospitalId}/departments", hospital.getPublicId())
                        .session(admin).cookie(adminCsrf.cookie()).header("X-XSRF-TOKEN", adminCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid department details"));
    }

    private MockHttpSession session(UserAccount.Role role) {
        UserAccount account = users.save(new UserAccount("department-management-" + role.name().toLowerCase()
                + "-" + System.nanoTime() + "@example.test", "test-only", role));
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
