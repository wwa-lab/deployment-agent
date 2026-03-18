package com.wwa.deploymentagent.errors;

public class UnauthorizedAppException extends AppException {
    public UnauthorizedAppException() {
        super("UNAUTHORIZED", 401, "Authentication required");
    }
}
