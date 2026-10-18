package com.camillus.clinic.assistant;

import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServiceTest {
    @Test
    void returnsSetupMessageWhenProviderKeyIsMissing() {
        ChatService service = new ChatService(RestClient.builder(), "", "test-model");
        AuthenticatedUser user = user(UserAccount.Role.DOCTOR);

        ChatResponse response = service.respond(user, new ChatRequest("How do I update my schedule?"));

        assertEquals(UserAccount.Role.DOCTOR, response.role());
        org.junit.jupiter.api.Assertions.assertTrue(response.reply().contains("OPENROUTER_API_KEY"));
    }

    @Test
    void sendsOnlyAClassifiedTopicAndSessionRoleToProvider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().string(not(containsString("Jane Doe"))))
                .andExpect(content().string(not(containsString("jane@example.test"))))
                .andExpect(content().string(not(containsString("cardiology"))))
                .andExpect(content().string(containsString("PATIENT")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Use the Book Appointment page to select a doctor and an open time."}}]}
                        """, MediaType.APPLICATION_JSON));
        ChatService service = new ChatService(builder, "test-api-key", "test-model");

        ChatResponse response = service.respond(user(UserAccount.Role.PATIENT),
                new ChatRequest("Please book cardiology with Jane Doe, jane@example.test"));

        assertEquals(UserAccount.Role.PATIENT, response.role());
        assertEquals("Use the Book Appointment page to select a doctor and an open time.", response.reply());
        server.verify();
    }

    @Test
    void explainsInvalidOpenRouterApiKeyWithoutExposingIt() {
        AssistantException exception = providerFailure(HttpStatus.UNAUTHORIZED);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.status());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("OPENROUTER_API_KEY"));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("test-api-key"));
    }

    @Test
    void explainsUnavailableConfiguredModel() {
        AssistantException exception = providerFailure(HttpStatus.NOT_FOUND);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.status());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("OPENROUTER_MODEL"));
    }

    @Test
    void returnsRateLimitMessageForOpenRouter429() {
        AssistantException exception = providerFailure(HttpStatus.TOO_MANY_REQUESTS);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("rate limit"));
    }

    @Test
    void reportsOpenRouterTimeoutClearly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out",
                            new java.net.SocketTimeoutException("Read timed out"));
                });
        ChatService service = new ChatService(builder, "test-api-key", "test-model");

        AssistantException exception = assertThrows(AssistantException.class,
                () -> service.respond(user(UserAccount.Role.PATIENT), new ChatRequest("How do I book?")));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("timed out"));
        server.verify();
    }

    private AssistantException providerFailure(HttpStatus providerStatus) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withStatus(providerStatus)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider detail\"}}"));
        ChatService service = new ChatService(builder, "test-api-key", "test-model");

        AssistantException exception = assertThrows(AssistantException.class,
                () -> service.respond(user(UserAccount.Role.PATIENT), new ChatRequest("How do I book?")));
        server.verify();
        return exception;
    }

    private AuthenticatedUser user(UserAccount.Role role) {
        return new AuthenticatedUser("user-id", "user@example.test", null, role);
    }
}
