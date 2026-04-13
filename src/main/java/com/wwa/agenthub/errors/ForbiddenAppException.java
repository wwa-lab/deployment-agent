package com.wwa.agenthub.errors;

public class ForbiddenAppException extends AppException {
    public ForbiddenAppException(String action) {
        super("FORBIDDEN", 403, "Access denied: insufficient role for action '" + action + "'");
    }
}
