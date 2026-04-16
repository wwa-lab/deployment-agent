package com.wwa.agenthub.domain.execution;

/**
 * Result of submitting a task to an external execution system (Jenkins/Ansible).
 *
 * @param success     whether the submission was accepted by the external system
 * @param executionId external system's build/job ID (null on failure)
 * @param jobUrl      URL to view the job in the external system (null on failure)
 * @param logUrl      optional direct log/console URL (null if not available at submit time)
 * @param approvalUrl optional direct approval URL for workflow approvals (null if not applicable)
 * @param message     human-readable status or error message
 */
public record AutoSubmissionResult(
        boolean success,
        String executionId,
        String jobUrl,
        String logUrl,
        String approvalUrl,
        String message
) {
    public static AutoSubmissionResult ok(String executionId, String jobUrl) {
        return new AutoSubmissionResult(true, executionId, jobUrl, null, null, "Submitted successfully");
    }

    public static AutoSubmissionResult ok(String executionId, String jobUrl, String logUrl, String approvalUrl) {
        return new AutoSubmissionResult(true, executionId, jobUrl, logUrl, approvalUrl, "Submitted successfully");
    }

    public static AutoSubmissionResult failure(String message) {
        return new AutoSubmissionResult(false, null, null, null, null, message);
    }
}
