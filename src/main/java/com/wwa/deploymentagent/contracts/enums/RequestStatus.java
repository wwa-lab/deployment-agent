package com.wwa.deploymentagent.contracts.enums;

/** Per-stage Request lifecycle status. */
@SuppressWarnings("java:S115")
public enum RequestStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Skipped,
    Rejected
}
