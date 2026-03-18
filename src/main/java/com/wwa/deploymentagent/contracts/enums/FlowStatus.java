package com.wwa.deploymentagent.contracts.enums;

/** Internal Release Flow lifecycle status. */
@SuppressWarnings("java:S115")
public enum FlowStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Rejected
}
