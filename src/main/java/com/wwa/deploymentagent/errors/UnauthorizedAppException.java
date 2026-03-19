package com.wwa.deploymentagent.errors;

public class UnauthorizedAppException extends AppException {
    public UnauthorizedAppException() {
        super("UNAUTHORIZED", 401, "Authentication required");
    }

    public UnauthorizedAppException(String message) {
        super("UNAUTHORIZED", 401, message);
    }
}
