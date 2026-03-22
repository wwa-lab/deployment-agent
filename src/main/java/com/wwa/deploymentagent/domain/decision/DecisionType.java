package com.wwa.deploymentagent.domain.decision;

/** Supported task-level decision types (task owner or DEVOPS_ADMIN required). */
public enum DecisionType {
    approve,
    reject,
    rerun,
    skip
}
