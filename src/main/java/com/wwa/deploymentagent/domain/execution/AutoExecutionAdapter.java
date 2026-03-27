package com.wwa.deploymentagent.domain.execution;

import com.wwa.deploymentagent.domain.configuration.ConfigurationScope;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;

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
}
