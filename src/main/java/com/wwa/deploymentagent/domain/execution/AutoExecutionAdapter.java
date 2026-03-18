package com.wwa.deploymentagent.domain.execution;

import java.util.Map;

/**
 * Adapter interface for submitting AUTO tasks to external execution systems.
 * Implementations handle the specifics of each system (Jenkins, Ansible, etc.).
 */
public interface AutoExecutionAdapter {

    /** The system identifier this adapter handles (e.g. "JENKINS", "ANSIBLE"). */
    String systemType();

    /**
     * Submit a task for execution.
     *
     * @param inputParameters task input parameters (contains script, parameters, etc.)
     * @return result with external execution ID and job URL on success
     */
    AutoSubmissionResult submit(Map<String, Object> inputParameters);
}
