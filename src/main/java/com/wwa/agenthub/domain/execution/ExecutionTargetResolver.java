package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resolves the correct external tool and normalized target for an AUTO task.
 *
 * <h3>Resolution precedence</h3>
 * <ol>
 *   <li>Explicit {@code system} override + non-URL script → use override, treat script as native ID</li>
 *   <li>URL in {@code script} containing Ansible/AWX patterns → Ansible</li>
 *   <li>URL in {@code script} containing Jenkins patterns → Jenkins</li>
 *   <li>Explicit {@code system} override + URL → validate no conflict, then route</li>
 *   <li>Plain non-URL script with no override → Jenkins (legacy compatibility)</li>
 * </ol>
 *
 * <h3>Conflict detection</h3>
 * <p>If {@code system=JENKINS} and the script is an Ansible URL (or vice-versa),
 * a {@link ValidationAppException} is thrown.
 */
@Service
public class ExecutionTargetResolver {

    /**
     * Resolve the execution target from a task's {@code inputParameters}.
     *
     * @param inputParameters task input map; must contain at least {@code script}
     * @return normalized {@link ExecutionTarget}
     * @throws ValidationAppException if the script is blank, the URL is unsupported,
     *                                or the explicit system conflicts with the URL
     */
    public ExecutionTarget resolve(Map<String, Object> inputParameters) {
        if (inputParameters == null) {
            throw new ValidationAppException("Task input parameters are missing");
        }

        Object scriptObj = inputParameters.get("script");
        if (scriptObj == null || scriptObj.toString().isBlank()) {
            throw new ValidationAppException("AUTO task requires a non-blank 'script' value");
        }
        String script = scriptObj.toString().trim();

        String explicitSystem = extractExplicitSystem(inputParameters);
        boolean isUrl = isUrl(script);

        if (!isUrl) {
            return resolveFromPlainScript(script, explicitSystem);
        } else {
            return resolveFromUrl(script, explicitSystem);
        }
    }

    // ─── Resolution helpers ────────────────────────────────────────────────────

    private ExecutionTarget resolveFromPlainScript(String script, String explicitSystem) {
        String system = explicitSystem != null ? explicitSystem.toUpperCase() : "JENKINS";
        String kind = "ANSIBLE".equals(system)
                ? ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE
                : ExecutionTarget.KIND_JENKINS_JOB_PATH;
        return new ExecutionTarget(system, kind, script, script, null, explicitSystem != null);
    }

    private ExecutionTarget resolveFromUrl(String script, String explicitSystem) {
        String inferredSystem;
        String kind;
        String normalizedTarget;

        if (isAnsibleWorkflowUrl(script)) {
            inferredSystem = "ANSIBLE";
            kind = ExecutionTarget.KIND_ANSIBLE_WORKFLOW_TEMPLATE;
            normalizedTarget = extractAnsibleTemplateId(script);
        } else if (isAnsibleJobTemplateUrl(script)) {
            inferredSystem = "ANSIBLE";
            kind = ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE;
            normalizedTarget = extractAnsibleTemplateId(script);
        } else if (isJenkinsUrl(script)) {
            inferredSystem = "JENKINS";
            kind = ExecutionTarget.KIND_JENKINS_JOB_URL;
            normalizedTarget = extractJenkinsJobPath(script);
        } else {
            throw new ValidationAppException(
                    "Unsupported URL pattern in 'script': '" + script
                    + "'. Use a Jenkins job URL (/job/…) or an Ansible AWX URL (/api/v2/job_templates/… or /api/v2/workflow_job_templates/…).");
        }

        // Validate explicit system does not conflict with URL
        if (explicitSystem != null && !explicitSystem.equalsIgnoreCase(inferredSystem)) {
            throw new ValidationAppException(
                    "System override '" + explicitSystem + "' conflicts with the inferred tool from the script URL "
                    + "(detected: " + inferredSystem + "). Remove the 'system' override or use a matching URL.");
        }

        return new ExecutionTarget(inferredSystem, kind, script, normalizedTarget, script, explicitSystem != null);
    }

    // ─── Pattern helpers ───────────────────────────────────────────────────────

    private static boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static boolean isJenkinsUrl(String url) {
        return url.contains("/job/");
    }

    private static boolean isAnsibleJobTemplateUrl(String url) {
        return url.contains("/api/v2/job_templates/");
    }

    private static boolean isAnsibleWorkflowUrl(String url) {
        return url.contains("/api/v2/workflow_job_templates/");
    }

    private static String extractExplicitSystem(Map<String, Object> params) {
        Object system = params.get("system");
        if (system == null) return null;
        String s = system.toString().trim().toUpperCase();
        if ("JENKINS".equals(s) || "ANSIBLE".equals(s)) return s;
        throw new ValidationAppException(
                "Invalid 'system' value: '" + system + "'. Supported values: JENKINS, ANSIBLE.");
    }

    /**
     * Extracts the Jenkins job path from a full Jenkins URL.
     * E.g. {@code http://jenkins:8080/job/my-pipeline/build} → {@code my-pipeline}
     *      {@code http://jenkins:8080/job/team/job/my-pipeline/} → {@code team/job/my-pipeline}
     */
    static String extractJenkinsJobPath(String url) {
        int jobIdx = url.indexOf("/job/");
        if (jobIdx < 0) return url;
        String afterJob = url.substring(jobIdx + 5); // skip "/job/"
        // Strip trailing endpoint segments (build, buildWithParameters, api, etc.)
        String[] segments = afterJob.split("/");
        StringBuilder path = new StringBuilder();
        for (String seg : segments) {
            if (seg.isBlank()) continue;
            if (seg.equals("build") || seg.equals("buildWithParameters")
                    || seg.equals("api") || seg.equals("json")
                    || seg.equals("queue") || seg.equals("lastBuild")) {
                break;
            }
            if (path.length() > 0) path.append("/");
            path.append(seg);
        }
        return path.length() > 0 ? path.toString() : afterJob;
    }

    /**
     * Extracts the template ID from an Ansible AWX URL.
     * E.g. {@code http://awx/api/v2/job_templates/42/launch/} → {@code 42}
     */
    static String extractAnsibleTemplateId(String url) {
        // Pattern: .../job_templates/{id}/... or .../workflow_job_templates/{id}/...
        String[] parts = url.split("/");
        boolean nextIsId = false;
        for (String part : parts) {
            if (nextIsId && !part.isBlank() && !part.equals("launch")) {
                return part.replaceAll("[^0-9a-zA-Z_\\-]", "");
            }
            if ("job_templates".equals(part) || "workflow_job_templates".equals(part)) {
                nextIsId = true;
            }
        }
        return url; // fallback: return raw URL if pattern not matched
    }
}
