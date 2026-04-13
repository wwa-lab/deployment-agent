package com.wwa.agenthub.errors;

public class UnauthorizedAppException extends AppException {
    public UnauthorizedAppException() {
        super("UNAUTHORIZED", 401, "Authentication required");
    }

    public UnauthorizedAppException(String message) {
        super("UNAUTHORIZED", 401, message);
    }
}
