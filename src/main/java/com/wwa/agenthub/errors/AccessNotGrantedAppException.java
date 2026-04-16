package com.wwa.agenthub.errors;

public class AccessNotGrantedAppException extends AppException {
    public AccessNotGrantedAppException() {
        super("ACCESS_NOT_GRANTED", 403, "Access not granted");
    }
}
