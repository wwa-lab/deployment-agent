package com.wwa.deploymentagent.errors;

/**
 * Base exception for all application-level errors.
 * Carries an error code and HTTP status for mapping in GlobalExceptionHandler.
 */
public class AppException extends RuntimeException {

    private final String code;
    private final int statusCode;
    private final Object details;

    public AppException(String code, int statusCode, String message, Object details) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.details = details;
    }

    public AppException(String code, int statusCode, String message) {
        this(code, statusCode, message, null);
    }

    public String getCode() { return code; }
    public int getStatusCode() { return statusCode; }
    public Object getDetails() { return details; }
}
