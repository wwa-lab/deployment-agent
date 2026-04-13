package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.contracts.enums.ExternalStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Result of polling an active external execution for its current state.
 *
 * <p>Returned by {@link AutoExecutionAdapter#pollStatus} and consumed by the
 * {@link ExternalExecutionMonitorService} to update
 * {@code TaskExecutionHistory} and (for terminal states) the parent {@code Task}.
 *
 * @param externalStatus      normalized remote state
 * @param terminal            whether the remote execution has reached a terminal state
 * @param executionStatus     Deployment Agent {@link ExecutionStatus} to persist on terminal
 * @param statusMessage       human-readable explanation of the remote state
 * @param externalExecutionId latest tool-native execution ID (if updated since submission)
 * @param jobUrl              primary external job/build URL
 * @param logUrl              direct log/console URL when available
 * @param approvalUrl         direct approval URL when applicable (workflow approvals)
 * @param resultSummary       normalized structured summary for terminal states
 * @param resultLogs          raw or normalized log excerpt when stored locally
 * @param observedAt          time the adapter observed the remote state
 */
public record AutoPollResult(
        ExternalStatus externalStatus,
        boolean terminal,
        ExecutionStatus executionStatus,
        String statusMessage,
        String externalExecutionId,
        String jobUrl,
        String logUrl,
        String approvalUrl,
        Map<String, Object> resultSummary,
        String resultLogs,
        Instant observedAt
) {

    /** Convenience factory for non-terminal polling results. */
    public static AutoPollResult running(ExternalStatus status, String message, String jobUrl, String logUrl) {
        return new AutoPollResult(
                status, false, ExecutionStatus.Running, message,
                null, jobUrl, logUrl, null, null, null, Instant.now());
    }

    /** Convenience factory for terminal success (remote SUCCEEDED). */
    public static AutoPollResult succeeded(String message, String jobUrl, String logUrl,
                                           Map<String, Object> resultSummary, String resultLogs) {
        return new AutoPollResult(
                ExternalStatus.SUCCEEDED, true, ExecutionStatus.Completed, message,
                null, jobUrl, logUrl, null, resultSummary, resultLogs, Instant.now());
    }

    /** Convenience factory for terminal failure (remote FAILED or ABORTED). */
    public static AutoPollResult failed(ExternalStatus status, String message, String jobUrl, String logUrl) {
        return new AutoPollResult(
                status, true, ExecutionStatus.Failed, message,
                null, jobUrl, logUrl, null, null, null, Instant.now());
    }

    /** Convenience factory for timed-out polling. */
    public static AutoPollResult timedOut(String message) {
        return new AutoPollResult(
                ExternalStatus.TIMED_OUT, true, ExecutionStatus.Timed_Out, message,
                null, null, null, null, null, null, Instant.now());
    }

    /** Convenience factory for UNKNOWN (non-terminal, keep prior state). */
    public static AutoPollResult unknown(String message) {
        return new AutoPollResult(
                ExternalStatus.UNKNOWN, false, ExecutionStatus.Running, message,
                null, null, null, null, null, null, Instant.now());
    }
}
