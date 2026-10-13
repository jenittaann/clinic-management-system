package com.camillus.clinic.hospital;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorScheduleApiIntegrationTest {
    private static final String PASSWORD = "schedule-test-password";
    private static final AtomicInteger EMAIL_COUNTER = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HospitalRepository hospitals;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AvailabilitySlotRepository slots;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void linkedDoctorCanCreateUpdateListAndDeleteOwnRecurringRules() throws Exception {
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor(doctorUser, "Schedule doctor");
        MockHttpSession session = login(doctorUser);
        Csrf csrf = csrf();

        mvc.perform(get(path(doctor) + "/availability").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctor.getPublicId()))
                .andExpect(jsonPath("$.appointmentDurationMinutes").value(30))
                .andExpect(jsonPath("$.recurringWeeklyRules").isEmpty())
                .andExpect(jsonPath("$.id").doesNotExist());

        MvcResult created = mvc.perform(post(path(doctor) + "/availability").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00:00\",\"endTime\":\"12:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availabilityId").exists())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn();
        String ruleId = JsonPath.read(created.getResponse().getContentAsString(), "$.availabilityId");

        mvc.perform(put(path(doctor) + "/availability/" + ruleId).session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"startTime\":\"10:00:00\",\"endTime\":\"13:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dayOfWeek").value(2));

        mvc.perform(delete(path(doctor) + "/availability/" + ruleId).session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());
        assertEquals(0, slots.count());
    }

    @Test
    void doctorsCannotManageOtherOrUnlinkedProfiles() throws Exception {
        UserAccount firstUser = account(UserAccount.Role.DOCTOR);
        UserAccount secondUser = account(UserAccount.Role.DOCTOR);
        Doctor first = doctor(firstUser, "First doctor");
        Doctor second = doctor(secondUser, "Second doctor");
        MockHttpSession session = login(firstUser);
        Csrf csrf = csrf();

        mvc.perform(get(path(second) + "/availability").session(session)).andExpect(status().isForbidden());
        mvc.perform(post(path(second) + "/availability").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isForbidden());

        Doctor unlinked = doctor(null, "Unlinked doctor");
        mvc.perform(get(path(unlinked) + "/availability").session(session)).andExpect(status().isForbidden());
        assertEquals(0, slots.count());
    }

    @Test
    void receptionistIsLimitedToAssignedHospitalAndAdminCanConfigureDuration() throws Exception {
        UserAccount admin = account(UserAccount.Role.ADMIN);
        UserAccount receptionist = account(UserAccount.Role.RECEPTIONIST);
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor assignedDoctor = doctor(null, "Assigned doctor");
        Hospital otherHospital = hospitals.save(new Hospital("Other hospital", null, null, null, null, null));
        Department otherDepartment = departments.save(new Department(otherHospital, "General", null));
        Doctor otherDoctor = doctors.save(new Doctor(otherDepartment, "Other doctor", null, null));
        MockHttpSession adminSession = login(admin);
        Csrf adminCsrf = csrf();

        mvc.perform(put("/api/admin/receptionists/{userId}/hospitals/{hospitalId}", receptionist.getPublicId(),
                        assignedDoctor.getDepartment().getHospital().getPublicId())
                        .session(adminSession).cookie(adminCsrf.cookie()).header("X-XSRF-TOKEN", adminCsrf.token()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/doctors/{doctorId}/account/{userId}", assignedDoctor.getPublicId(), doctorUser.getPublicId())
                        .session(adminSession).cookie(adminCsrf.cookie()).header("X-XSRF-TOKEN", adminCsrf.token()))
                .andExpect(status().isNoContent());

        MockHttpSession receptionistSession = login(receptionist);
        mvc.perform(get(path(assignedDoctor) + "/availability").session(receptionistSession)).andExpect(status().isOk());
        mvc.perform(get(path(otherDoctor) + "/availability").session(receptionistSession)).andExpect(status().isForbidden());

        mvc.perform(put(path(assignedDoctor) + "/appointment-duration").session(adminSession)
                        .cookie(adminCsrf.cookie()).header("X-XSRF-TOKEN", adminCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\":45}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentDurationMinutes").value(45));
    }

    @Test
    void validatesRulesAndDurationAndRequiresCsrf() throws Exception {
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor(doctorUser, "Validation doctor");
        mvc.perform(get(path(doctor) + "/availability")).andExpect(status().isUnauthorized());
        MockHttpSession session = login(doctorUser);

        mvc.perform(post(path(doctor) + "/availability").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":0,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isForbidden()); // CSRF protects the request before validation.
        Csrf csrf = csrf();
        mvc.perform(post(path(doctor) + "/availability").session(session).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":0,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest());
        Csrf next = csrf();
        mvc.perform(post(path(doctor) + "/availability").session(session).cookie(next.cookie())
                        .header("X-XSRF-TOKEN", next.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"11:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("endTime must be after startTime"));
        Csrf durationCsrf = csrf();
        mvc.perform(put(path(doctor) + "/appointment-duration").session(session).cookie(durationCsrf.cookie())
                        .header("X-XSRF-TOKEN", durationCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":0}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, slots.count());
    }

    @Test
    void rejectsOverlappingRulesAndMalformedPublicIds() throws Exception {
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor(doctorUser, "Overlap doctor");
        MockHttpSession session = login(doctorUser);
        Csrf first = csrf();
        mvc.perform(post(path(doctor) + "/availability").session(session).cookie(first.cookie())
                        .header("X-XSRF-TOKEN", first.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":3,\"startTime\":\"09:00\",\"endTime\":\"11:00\"}"))
                .andExpect(status().isCreated());
        Csrf second = csrf();
        mvc.perform(post(path(doctor) + "/availability").session(session).cookie(second.cookie())
                        .header("X-XSRF-TOKEN", second.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":3,\"startTime\":\"10:30\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/doctors/not-a-uuid/availability").session(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("doctorId must be a valid UUID"));
        assertEquals(1, slots.count());
    }

    private String path(Doctor doctor) { return "/api/doctors/" + doctor.getPublicId(); }

    private Doctor doctor(UserAccount account, String name) {
        Hospital hospital = hospitals.save(new Hospital(name + " hospital", null, null, null, null, null));
        Department department = departments.save(new Department(hospital, "General", null));
        Doctor doctor = new Doctor(department, name, null, null);
        doctor.assignUser(account);
        return doctors.save(doctor);
    }

    private UserAccount account(UserAccount.Role role) {
        return users.save(new UserAccount("schedule-" + EMAIL_COUNTER.incrementAndGet() + "@example.test",
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

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String value = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return new Csrf(value, cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
