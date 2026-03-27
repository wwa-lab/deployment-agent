package com.wwa.deploymentagent.domain.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.deploymentagent.contracts.enums.ExternalStatus;
import com.wwa.deploymentagent.domain.configuration.ConfigurationComponentService;
import com.wwa.deploymentagent.domain.configuration.ConfigurationScope;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submits and polls AUTO tasks via the Ansible Tower/AWX REST API.
 *
 * <h3>Submit</h3>
 * <p>Launches a job template or workflow job template. The template ID is taken
 * from {@link ExecutionTarget#normalizedTarget()}.
 *
 * <h3>Poll</h3>
 * <p>Polls the AWX job or workflow-job status endpoint and maps AWX-native statuses
 * to the normalized {@link ExternalStatus} model.
 *
 * <h3>State mapping</h3>
 * <ul>
 *   <li>new, pending, waiting → {@link ExternalStatus#QUEUED}</li>
 *   <li>running → {@link ExternalStatus#RUNNING}</li>
 *   <li>successful → {@link ExternalStatus#SUCCEEDED}</li>
 *   <li>failed, error → {@link ExternalStatus#FAILED}</li>
 *   <li>canceled → {@link ExternalStatus#ABORTED}</li>
 * </ul>
 *
 * <h3>Workflow approvals</h3>
 * <p>When the workflow-job kind is detected and an approval node is pending, the
 * approval URL is surfaced in {@link AutoPollResult#approvalUrl()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnsibleExecutionAdapter implements AutoExecutionAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigurationComponentService configurationComponentService;
    private final RestTemplate restTemplate;

    @Override
    public String systemType() {
        return "ANSIBLE";
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public AutoSubmissionResult submit(
            ExecutionTarget target,
            Map<String, Object> inputParameters,
            ConfigurationScope scope
    ) {
        try {
            var config = configurationComponentService.resolveForSystem(systemType(), scope);
            String baseUrl = config.endpoint();
            String token   = config.credential();

            boolean isWorkflow = ExecutionTarget.KIND_ANSIBLE_WORKFLOW_TEMPLATE.equals(target.targetKind());
            String templateId = target.normalizedTarget();

            String apiPath = isWorkflow
                    ? "/api/v2/workflow_job_templates/" + templateId + "/launch/"
                    : "/api/v2/job_templates/" + templateId + "/launch/";
            String launchUrl = baseUrl + apiPath;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            String body = buildRequestBody(inputParameters);
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(launchUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Object jobId = responseBody.get("id");
                String executionId = jobId != null ? jobId.toString() : "unknown";

                String jobUrlPath = isWorkflow ? "workflow" : "playbook";
                String jobUrl = baseUrl + "/#/jobs/" + jobUrlPath + "/" + executionId;
                String logUrl = baseUrl + "/api/v2/" + (isWorkflow ? "workflow_jobs" : "jobs")
                        + "/" + executionId + "/stdout/?format=txt";

                return AutoSubmissionResult.ok(executionId, jobUrl, logUrl, null);
            } else {
                return AutoSubmissionResult.failure(
                        "Ansible returned HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Ansible submission failed", e);
            return AutoSubmissionResult.failure("Ansible submission failed: " + e.getMessage());
        }
    }

    // ─── Poll ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public AutoPollResult pollStatus(TaskExecutionHistory executionHistory) {
        try {
            var config = configurationComponentService.resolveForSystem(
                    systemType(),
                    ConfigurationScope.from(executionHistory)
            );
            String baseUrl = config.endpoint();
            String token   = config.credential();

            String executionId = executionHistory.getExternalExecutionId();
            if (executionId == null) {
                return AutoPollResult.unknown("No external execution ID recorded; cannot poll");
            }

            // Determine job type from the stored job URL (workflow vs regular)
            boolean isWorkflow = isWorkflowJob(executionHistory.getExternalJobUrl());
            String apiEndpoint = isWorkflow ? "workflow_jobs" : "jobs";
            String statusUrl = baseUrl + "/api/v2/" + apiEndpoint + "/" + executionId + "/";
            String logUrl = baseUrl + "/api/v2/" + apiEndpoint + "/" + executionId + "/stdout/?format=txt";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(statusUrl, HttpMethod.GET, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return AutoPollResult.unknown("Could not poll Ansible job; HTTP "
                        + response.getStatusCode().value());
            }

            Map<String, Object> job = response.getBody();
            String status = (String) job.get("status");
            String jobUrl = executionHistory.getExternalJobUrl();

            ExternalStatus externalStatus = mapAnsibleStatus(status);

            if (externalStatus == ExternalStatus.WAITING_APPROVAL) {
                String approvalUrl = resolveApprovalUrl(baseUrl, token, executionId);
                return new AutoPollResult(
                        ExternalStatus.WAITING_APPROVAL, false, com.wwa.deploymentagent.contracts.enums.ExecutionStatus.Running,
                        "Waiting for approval in Ansible workflow",
                        null, jobUrl, logUrl, approvalUrl, null, null, java.time.Instant.now());
            }

            if (externalStatus.isTerminal()) {
                return switch (externalStatus) {
                    case SUCCEEDED -> AutoPollResult.succeeded("Ansible job succeeded", jobUrl, logUrl,
                            buildResultSummary(job), null);
                    case ABORTED -> AutoPollResult.failed(ExternalStatus.ABORTED,
                            "Ansible job was canceled", jobUrl, logUrl);
                    default -> AutoPollResult.failed(externalStatus,
                            "Ansible job failed: " + status, jobUrl, logUrl);
                };
            }

            return AutoPollResult.running(externalStatus,
                    "Ansible job status: " + status, jobUrl, logUrl);

        } catch (Exception e) {
            log.warn("Ansible poll error for execution {}: {}", executionHistory.getId(), e.getMessage());
            return AutoPollResult.unknown("Ansible poll error: " + e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ExternalStatus mapAnsibleStatus(String status) {
        if (status == null) return ExternalStatus.UNKNOWN;
        return switch (status.toLowerCase()) {
            case "new", "pending", "waiting" -> ExternalStatus.QUEUED;
            case "running" -> ExternalStatus.RUNNING;
            case "successful" -> ExternalStatus.SUCCEEDED;
            case "failed", "error" -> ExternalStatus.FAILED;
            case "canceled" -> ExternalStatus.ABORTED;
            default -> ExternalStatus.UNKNOWN;
        };
    }

    private boolean isWorkflowJob(String jobUrl) {
        return jobUrl != null && jobUrl.contains("workflow");
    }

    @SuppressWarnings("unchecked")
    private String resolveApprovalUrl(String baseUrl, String token, String workflowJobId) {
        try {
            String approvalListUrl = baseUrl + "/api/v2/workflow_approvals/?workflow_job__id=" + workflowJobId
                    + "&status=pending&order_by=-created";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(approvalListUrl, HttpMethod.GET, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object results = response.getBody().get("results");
                if (results instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> approvalMap) {
                        Object approvalId = approvalMap.get("id");
                        if (approvalId != null) {
                            return baseUrl + "/#/jobs/workflow-approvals/" + approvalId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve Ansible approval URL for workflow job {}: {}", workflowJobId, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResultSummary(Map<String, Object> job) {
        Map<String, Object> summary = new HashMap<>();
        for (String key : new String[]{"elapsed", "started", "finished", "status",
                "failed", "changed", "skipped", "ok", "dark"}) {
            Object val = job.get(key);
            if (val != null) summary.put(key, val);
        }
        return summary.isEmpty() ? null : summary;
    }

    private String buildRequestBody(Map<String, Object> inputParameters) throws JsonProcessingException {
        Object params = inputParameters.get("parameters");
        if (params == null) {
            return "{}";
        }
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("extra_vars", params.toString());
        return OBJECT_MAPPER.writeValueAsString(requestBody);
    }

}
