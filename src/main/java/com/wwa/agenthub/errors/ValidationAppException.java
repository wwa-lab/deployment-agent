package com.wwa.agenthub.errors;

public class ValidationAppException extends AppException {
    public ValidationAppException(String message) {
        super("VALIDATION_ERROR", 400, message);
    }

    public ValidationAppException(String message, Object details) {
        super("VALIDATION_ERROR", 400, message, details);
    }
}
