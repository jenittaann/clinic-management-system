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
