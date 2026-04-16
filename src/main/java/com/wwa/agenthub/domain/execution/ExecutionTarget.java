package com.wwa.agenthub.domain.execution;

/**
 * Normalized target descriptor produced by {@link ExecutionTargetResolver}.
 *
 * @param systemType       "JENKINS" or "ANSIBLE"
 * @param targetKind       Tool-specific classification (e.g. JENKINS_JOB_PATH, ANSIBLE_JOB_TEMPLATE)
 * @param rawScript        Original {@code script} value from task input
 * @param normalizedTarget Canonical identifier used by the adapter (job path, template ID)
 * @param displayUrl       Preferred external click-through URL; null for legacy plain targets
 * @param explicitOverride True when the {@code system} field forced the resolution
 */
public record ExecutionTarget(
        String systemType,
        String targetKind,
        String rawScript,
        String normalizedTarget,
        String displayUrl,
        boolean explicitOverride
) {

    // ─── Target kind constants ─────────────────────────────────────────────────

    public static final String KIND_JENKINS_JOB_URL  = "JENKINS_JOB_URL";
    public static final String KIND_JENKINS_JOB_PATH = "JENKINS_JOB_PATH";
    public static final String KIND_ANSIBLE_JOB_TEMPLATE      = "ANSIBLE_JOB_TEMPLATE";
    public static final String KIND_ANSIBLE_WORKFLOW_TEMPLATE  = "ANSIBLE_WORKFLOW_TEMPLATE";
}
