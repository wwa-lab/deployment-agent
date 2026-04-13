package com.wwa.agenthub.contracts.enums;

/**
 * Task lifecycle status.
 * Constant names intentionally match DB column values for schema compatibility.
 *
 * Valid transitions (see TaskStateMachine):
 *   Pending            → Ready_For_Execution, Skipped
 *   Ready_For_Execution → Executing, Skipped
 *   Executing          → Awaiting_Review, Failed
 *   Awaiting_Review    → Approved, Rejected
 *   Rejected           → Ready_For_Execution  (rerun)
 *   Failed             → Ready_For_Execution  (rerun)
 *   Approved, Skipped  → (terminal – no outgoing transitions)
 */
@SuppressWarnings("java:S115") // naming convention: intentional for schema compatibility
public enum TaskStatus {
    Pending,
    Ready_For_Execution,
    Executing,
    Awaiting_Review,
    Approved,
    Rejected,
    Skipped,
    Failed
}
