package com.wwa.deploymentagent.errors;

public class InvalidStateTransitionException extends AppException {
    public InvalidStateTransitionException(String from, String to, String resource) {
        super("INVALID_STATE_TRANSITION", 409,
              "Invalid state transition on " + resource + ": " + from + " → " + to);
    }

    public InvalidStateTransitionException(String from, String to) {
        super("INVALID_STATE_TRANSITION", 409,
              "Invalid state transition: " + from + " → " + to);
    }
}
