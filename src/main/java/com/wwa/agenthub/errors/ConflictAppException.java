package com.wwa.agenthub.errors;

public class ConflictAppException extends AppException {
    public ConflictAppException(String message) {
        super("CONFLICT", 409, message);
    }

    public ConflictAppException(String message, Object details) {
        super("CONFLICT", 409, message, details);
    }
}
