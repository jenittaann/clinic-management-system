package com.camillus.clinic.assistant;

import com.camillus.clinic.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT', 'RECEPTIONIST')")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthenticatedUser user,
                             @Valid @RequestBody ChatRequest request) {
        return chat.respond(user, request);
    }

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<AssistantErrorResponse> assistantError(AssistantException exception) {
        return ResponseEntity.status(exception.status())
                .body(new AssistantErrorResponse(exception.getMessage()));
    }

    public record AssistantErrorResponse(String error) {}
}
