package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.domain.configuration.ConfigurationScope;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;

import java.util.Map;

/**
 * Adapter interface for submitting and polling AUTO tasks in external execution systems.
 *
 * <p>Implementations hide tool-specific API details from {@link AutoExecutionService}
 * and {@link ExternalExecutionMonitorService}.
 *
 * <h3>Contract rules</h3>
 * <ul>
 *   <li>{@link #submit} is called once per execution attempt.</li>
 *   <li>{@link #pollStatus} is called repeatedly until it returns a terminal result.</li>
 *   <li>Both methods must be idempotent with respect to side effects in Deployment Agent.</li>
 *   <li>Implementations must not expose credentials in return values or logs.</li>
 * </ul>
 *
 * <h3>MVP Foundation Seams</h3>
 * <p>{@link #supportsCancel()} and {@link #cancel} are reserved for a future
 * human-on-the-loop cancellation flow and SLA-timeout sweeper. MVP adapters
 * return {@code false} from {@link #supportsCancel()} by default and throw
 * {@link UnsupportedOperationException} from {@link #cancel}. No runtime code
 * calls either method yet — the default methods exist so adapters can opt in
 * later without a breaking interface change that would force every
 * implementation to update at once. See
 * {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
 */
public interface AutoExecutionAdapter {

    /** The system identifier this adapter handles (e.g. "JENKINS", "ANSIBLE"). */
    String systemType();

    /**
     * Submit a task for execution to the external tool.
     *
     * @param target          resolved target descriptor (tool type, normalized target ID, display URL)
     * @param inputParameters task input parameters (contains parameters, extra fields)
     * @param scope           normalized request scope snapshot for runtime config resolution
     * @return submission result with external execution ID and job URL on success
     */
    AutoSubmissionResult submit(ExecutionTarget target, Map<String, Object> inputParameters, ConfigurationScope scope);

    /**
     * Poll the current execution state for an active execution attempt.
     *
     * <p>Called by the monitor on each poll cycle. Must not throw — return
     * {@link AutoPollResult#unknown(String)} on transient errors so the monitor
     * can continue to the next batch item.
     *
     * @param executionHistory the active execution history record (contains external IDs and URLs)
     * @return normalized poll result; never null
     */
    AutoPollResult pollStatus(TaskExecutionHistory executionHistory);

    /**
     * MVP Foundation Seam — whether this adapter supports cancelling an
     * in-flight external execution. Default: {@code false}. Callers must
     * check this flag before invoking {@link #cancel}.
     */
    default boolean supportsCancel() {
        return false;
    }

    /**
     * MVP Foundation Seam — request cancellation of an in-flight external
     * execution. Default: throws {@link UnsupportedOperationException}. No
     * runtime code invokes this in MVP; it exists so a future human-on-the-loop
     * cancel button or SLA-timeout sweeper can opt-in per adapter without
     * forcing every adapter to change at once.
     *
     * @param executionHistory the active execution history record identifying
     *                         the external job to cancel
     * @throws UnsupportedOperationException if {@link #supportsCancel()} is
     *         {@code false} for this adapter
     */
    default void cancel(TaskExecutionHistory executionHistory) {
        throw new UnsupportedOperationException(
                "cancel() not supported by adapter " + systemType());
    }
}
