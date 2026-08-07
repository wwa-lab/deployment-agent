package com.wwa.agenthub.platform.domain.integration;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class IntegrationApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    public IntegrationApiException(HttpStatus status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public static IntegrationApiException badRequest(String code, String message) {
        return new IntegrationApiException(HttpStatus.BAD_REQUEST, code, message, false);
    }

    public static IntegrationApiException unprocessable(String code, String message) {
        return new IntegrationApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, false);
    }

    public static IntegrationApiException forbidden(String message) {
        return new IntegrationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message, false);
    }

    public static IntegrationApiException notFound(String code, String resource) {
        return new IntegrationApiException(HttpStatus.NOT_FOUND, code, resource + " was not found.", false);
    }

    public static IntegrationApiException conflict(String code, String message, boolean retryable) {
        return new IntegrationApiException(HttpStatus.CONFLICT, code, message, retryable);
    }
}
