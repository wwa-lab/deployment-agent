package com.wwa.deploymentagent.errors;

public class AccessSuspendedAppException extends AppException {
    public AccessSuspendedAppException() {
        super("ACCESS_SUSPENDED", 403, "Access suspended");
    }
}
