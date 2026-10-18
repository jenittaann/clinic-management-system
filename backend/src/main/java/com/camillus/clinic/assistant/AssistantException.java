package com.camillus.clinic.assistant;

import org.springframework.http.HttpStatus;

public class AssistantException extends RuntimeException {
    private final HttpStatus status;

    public AssistantException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
