package com.wwa.deploymentagent.contracts.enums;

/**
 * Execution type for a task – determines automated vs manual execution path.
 *   MANUAL: human-executed externally; owner or DEVOPS_ADMIN records result via "Record Result" action.
 *   AUTO:   system-submitted to execution pipeline; result received via callback webhook.
 */
public enum ExecutionType {
    MANUAL,
    AUTO
}
