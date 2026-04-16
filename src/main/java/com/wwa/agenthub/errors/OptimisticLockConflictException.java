package com.wwa.agenthub.errors;

/** Thrown when a JPA optimistic lock (@Version) conflict is detected. */
public class OptimisticLockConflictException extends AppException {
    public OptimisticLockConflictException(String resource) {
        super("OPTIMISTIC_LOCK_CONFLICT", 409,
              "Concurrent update conflict on " + resource + ". Reload and retry.");
    }
}
