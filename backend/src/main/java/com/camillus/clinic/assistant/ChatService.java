package com.camillus.clinic.assistant;

import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final String SETUP_MESSAGE =
            "The AI assistant is not configured yet. Set OPENROUTER_API_KEY in the backend environment and restart the service.";
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public ChatService(RestClient.Builder restClientBuilder,
                       @Value("${camillus.ai.openrouter.api-key:}") String apiKey,
                       @Value("${camillus.ai.openrouter.model:openai/gpt-4o-mini}") String model) {
        this.restClient = restClientBuilder.baseUrl("https://openrouter.ai/api/v1").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public ChatResponse respond(AuthenticatedUser user, ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) return new ChatResponse(user.role(), SETUP_MESSAGE);

        Map<String, Object> payload = Map.of(
                "model", model,
                "max_tokens", 180,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(user.role())),
                        Map.of("role", "user", "content", safeTopic(request.message(), user.role()))
                )
        );

        try {
            Map<?, ?> result = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("X-Title", "CAMILLUS Clinic Management")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        throw providerError(response.getStatusCode());
                    })
                    .body(Map.class);
            String answer = readAnswer(result);
            if (answer.isBlank()) {
                throw new AssistantException(HttpStatus.BAD_GATEWAY,
                        "OpenRouter returned an empty response. Check the configured model and try again.");
            }
            return new ChatResponse(user.role(), answer);
        } catch (AssistantException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new AssistantException(HttpStatus.GATEWAY_TIMEOUT,
                        "The AI assistant request timed out. Please try again.");
            }
            throw new AssistantException(HttpStatus.BAD_GATEWAY,
                    "The backend could not connect to OpenRouter. Check network access and try again.");
        } catch (RestClientException exception) {
            throw new AssistantException(HttpStatus.BAD_GATEWAY,
                    "The AI assistant could not read OpenRouter's response. Please try again.");
        }
    }

    private AssistantException providerError(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return new AssistantException(HttpStatus.BAD_GATEWAY,
                    "OpenRouter rejected the backend API key or its permissions. Verify OPENROUTER_API_KEY.");
        }
        if (status.value() == 402) {
            return new AssistantException(HttpStatus.BAD_GATEWAY,
                    "The OpenRouter account has insufficient credits. Check its billing balance.");
        }
        if (status.value() == 404) {
            return new AssistantException(HttpStatus.BAD_GATEWAY,
                    "The configured OpenRouter model was not found or is unavailable. Verify OPENROUTER_MODEL.");
        }
        if (status.value() == 429) {
            return new AssistantException(HttpStatus.TOO_MANY_REQUESTS,
                    "OpenRouter rate limit reached. Wait briefly and try again.");
        }
        if (status.value() == 408 || status.value() == 504) {
            return new AssistantException(HttpStatus.GATEWAY_TIMEOUT,
                    "OpenRouter timed out while processing the request. Please try again.");
        }
        if (status.is5xxServerError()) {
            return new AssistantException(HttpStatus.BAD_GATEWAY,
                    "OpenRouter is temporarily unavailable. Please try again later.");
        }
        return new AssistantException(HttpStatus.BAD_GATEWAY,
                "OpenRouter rejected the chat request (HTTP " + status.value()
                        + "). Verify the backend model configuration.");
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) return true;
        }
        return false;
    }

    private String systemPrompt(UserAccount.Role role) {
        String focus = switch (role) {
            case ADMIN -> "hospital and doctor management";
            case DOCTOR -> "doctor schedules and appointment workflow";
            case PATIENT -> "appointment booking and appointment status";
            case RECEPTIONIST -> "appointment coordination and check-in workflow";
        };
        return "You are the CAMILLUS clinic software help assistant. The signed-in user's role is "
                + role + ". Help only with general " + focus + " in the application. "
                + "Do not provide medical advice, request or reveal patient data, claim to modify records, "
                + "or perform actions. Explain that changes must be made in the application. "
                + "Keep the answer concise (under 90 words).";
    }

    String safeTopic(String message, UserAccount.Role role) {
        String text = message.toLowerCase(java.util.Locale.ROOT);
        if (contains(text, "appointment", "book", "booking", "cancel", "reschedule", "status", "slot", "time"))
            return "Explain the general appointment booking, cancellation, rescheduling, or status workflow.";
        if (contains(text, "schedule", "availability", "available", "hours", "weekday", "duration"))
            return "Explain the general doctor schedule and availability workflow.";
        if (contains(text, "hospital", "department", "doctor", "profile", "account", "link"))
            return "Explain the general hospital, department, doctor, or account management workflow.";
        if (contains(text, "check-in", "check in", "checkin", "queue", "reception"))
            return "Explain the general receptionist check-in and queue workflow.";
        if (contains(text, "patient", "record", "privacy", "dashboard"))
            return switch (role) {
                case ADMIN -> "Explain general patient-record access and privacy controls in a clinic application.";
                case DOCTOR -> "Explain the general doctor patient-list workflow and privacy expectations.";
                case PATIENT -> "Explain where a patient can view their own appointments and profile.";
                case RECEPTIONIST -> "Explain general receptionist patient-record access boundaries.";
            };
        return "Give a brief overview of how a " + role.name().toLowerCase(java.util.Locale.ROOT)
                + " can use the CAMILLUS clinic management dashboard.";
    }

    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private String readAnswer(Map<?, ?> response) {
        if (response == null) return "";
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> choice))
            return "";
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) return "";
        Object content = messageMap.get("content");
        return content instanceof String text ? text.trim() : "";
    }
}
