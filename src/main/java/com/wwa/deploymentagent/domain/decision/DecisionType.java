package com.wwa.deploymentagent.domain.decision;

/** Supported task-level decision types (TL role required for all). */
public enum DecisionType {
    approve,
    reject,
    rerun,
    skip
}
