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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
                .andExpect(jsonPath("$.availableSlots").isEmpty())
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

        mvc.perform(get(path(doctor) + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringWeeklyRules.length()").value(1))
                .andExpect(jsonPath("$.recurringWeeklyRules[0].availabilityId").value(ruleId))
                .andExpect(jsonPath("$.recurringWeeklyRules[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.recurringWeeklyRules[0].startTime").value("09:00:00"));

        mvc.perform(put(path(doctor) + "/availability/" + ruleId).session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"startTime\":\"10:00:00\",\"endTime\":\"13:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dayOfWeek").value(2));

        mvc.perform(get(path(doctor) + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringWeeklyRules[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$.recurringWeeklyRules[0].startTime").value("10:00:00"));

        mvc.perform(delete(path(doctor) + "/availability/" + ruleId).session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());
        assertEquals(0, slots.count());
    }

    @Test
    void publicCanReadSchedulesButDoctorsCannotManageOtherOrUnlinkedProfiles() throws Exception {
        UserAccount firstUser = account(UserAccount.Role.DOCTOR);
        UserAccount secondUser = account(UserAccount.Role.DOCTOR);
        UserAccount unlinkedUser = account(UserAccount.Role.DOCTOR);
        Doctor first = doctor(firstUser, "First doctor");
        Doctor second = doctor(secondUser, "Second doctor");
        MockHttpSession session = login(firstUser);
        Csrf csrf = csrf();

        mvc.perform(get(path(second) + "/availability")).andExpect(status().isOk());
        mvc.perform(post(path(second) + "/availability").session(session)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isForbidden());

        Doctor unlinked = doctor(null, "Unlinked doctor");
        mvc.perform(get(path(unlinked) + "/availability")).andExpect(status().isOk());
        Csrf unlinkedCsrf = csrf();
        mvc.perform(post(path(unlinked) + "/availability").session(login(unlinkedUser))
                        .cookie(unlinkedCsrf.cookie()).header("X-XSRF-TOKEN", unlinkedCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Doctor profile is not linked to this account"));
        mvc.perform(get("/api/doctors/me").session(login(unlinkedUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Doctor profile is not linked to this account"));
        assertEquals(0, slots.count());
    }

    @Test
    void publicScheduleListsOnlyTheSelectedDoctorsOpenSlots() throws Exception {
        LocalDate nextDay = Instant.now().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(1);
        int weekday = nextDay.getDayOfWeek().getValue();
        Doctor selectedDoctor = doctor(null, "Selected schedule doctor");
        Doctor otherDoctor = doctor(null, "Other schedule doctor");
        slots.save(new AvailabilitySlot(selectedDoctor, weekday,
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
        slots.save(new AvailabilitySlot(otherDoctor, weekday,
                LocalTime.of(14, 0), LocalTime.of(15, 0)));

        mvc.perform(get(path(selectedDoctor) + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots.length()").value(2))
                .andExpect(jsonPath("$.availableSlots[0].date").value(nextDay.toString()))
                .andExpect(jsonPath("$.availableSlots[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.availableSlots[1].startTime").value("09:30:00"))
                .andExpect(jsonPath("$.availableSlots[0].appointmentId").doesNotExist());

        mvc.perform(get(path(selectedDoctor) + "/availability?date={date}", nextDay))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots.length()").value(2))
                .andExpect(jsonPath("$.availableSlots[0].date").value(nextDay.toString()))
                .andExpect(jsonPath("$.availableSlots[0].startTime").value("09:00:00"));

        mvc.perform(get(path(selectedDoctor) + "/availability?date={date}", nextDay.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots").isEmpty());
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
        mvc.perform(get(path(otherDoctor) + "/availability")).andExpect(status().isOk());

        mvc.perform(put(path(assignedDoctor) + "/appointment-duration").session(adminSession)
                        .cookie(adminCsrf.cookie()).header("X-XSRF-TOKEN", adminCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\":45}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentDurationMinutes").value(45));
    }

    @Test
    void validatesRulesAndDurationAndRequiresCsrf() throws Exception {
        UserAccount doctorUser = account(UserAccount.Role.DOCTOR);
        Doctor doctor = doctor(doctorUser, "Validation doctor");
        mvc.perform(get(path(doctor) + "/availability")).andExpect(status().isOk());
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
        return com.camillus.clinic.TestSessions.forAccount(account);
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String value = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return new Csrf(value, cookie);
    }

    private record Csrf(String token, Cookie cookie) {}
}
