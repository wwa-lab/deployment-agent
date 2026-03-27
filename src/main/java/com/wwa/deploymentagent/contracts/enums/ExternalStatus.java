package com.wwa.deploymentagent.contracts.enums;

/**
 * Normalized remote-execution state used by the polling monitor and UI.
 *
 * <p>Terminal states ({@link #SUCCEEDED}, {@link #FAILED}, {@link #ABORTED},
 * {@link #TIMED_OUT}) drive task-lifecycle transitions inside Deployment Agent.
 * Non-terminal states keep the task in {@code Executing} and inform the UI
 * about what is happening in the external tool.
 */
public enum ExternalStatus {

    /** Accepted by external tool; not yet executing. */
    QUEUED,

    /** External job is currently executing. */
    RUNNING,

    /** External execution paused waiting for approval in the external tool. */
    WAITING_APPROVAL,

    /** Remote execution finished successfully (terminal). */
    SUCCEEDED,

    /** Remote execution failed (terminal). */
    FAILED,

    /** Remote execution was canceled/aborted (terminal). */
    ABORTED,

    /** Monitor or remote job timed out (terminal). */
    TIMED_OUT,

    /** Remote state could not be normalized safely; keep prior state. */
    UNKNOWN;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == ABORTED || this == TIMED_OUT;
    }
}
