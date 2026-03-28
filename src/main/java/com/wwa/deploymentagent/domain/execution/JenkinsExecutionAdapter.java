package com.wwa.deploymentagent.domain.execution;

import com.wwa.deploymentagent.contracts.enums.ExternalStatus;
import com.wwa.deploymentagent.domain.configuration.ConfigurationComponentService;
import com.wwa.deploymentagent.domain.configuration.ConfigurationScope;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Submits and polls AUTO tasks via the Jenkins Remote Access API.
 *
 * <h3>Submit</h3>
 * <p>Triggers a parameterized build using the job path from {@link ExecutionTarget#normalizedTarget()}.
 * Stores the queue-item URL as the initial external job URL.
 *
 * <h3>Poll</h3>
 * <p>Polling is a two-phase process:
 * <ol>
 *   <li>If the stored URL is a queue URL ({@code /queue/item/…}), resolve it to a build URL.</li>
 *   <li>Once a build URL is known, poll the build JSON for status and derive log/console URLs.</li>
 * </ol>
 *
 * <h3>State mapping</h3>
 * <ul>
 *   <li>Queue item not yet executable → {@link ExternalStatus#QUEUED}</li>
 *   <li>Build {@code building=true} → {@link ExternalStatus#RUNNING}</li>
 *   <li>Build result {@code SUCCESS} → {@link ExternalStatus#SUCCEEDED}</li>
 *   <li>Build result {@code FAILURE} or {@code UNSTABLE} → {@link ExternalStatus#FAILED}</li>
 *   <li>Build result {@code ABORTED} → {@link ExternalStatus#ABORTED}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JenkinsExecutionAdapter implements AutoExecutionAdapter {

    private static final java.util.Set<String> RESERVED_KEYS =
            java.util.Set.of("script", "system");

    private final ConfigurationComponentService configurationComponentService;
    private final RestTemplate restTemplate;

    @Override
    public String systemType() {
        return "JENKINS";
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    @Override
    public AutoSubmissionResult submit(
            ExecutionTarget target,
            Map<String, Object> inputParameters,
            ConfigurationScope scope
    ) {
        try {
            var config = configurationComponentService.resolveForSystem(systemType(), scope);
            String baseUrl = config.endpoint();
            String user    = config.serviceUser();
            String token   = config.credential();

            String jobPath = target.normalizedTarget();
            String buildUrl = baseUrl + "/job/" + jobPath + "/buildWithParameters";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set(HttpHeaders.AUTHORIZATION, basicAuth(user, token));

            MultiValueMap<String, String> formParams = buildFormParameters(inputParameters);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()
                    || response.getStatusCode().value() == 303) {
                String queueUrl = response.getHeaders().getFirst("Location");
                String executionId = queueUrl != null ? extractQueueId(queueUrl) : "unknown";
                // Store the job page URL (not queue URL) so "View Job" opens the right page
                String jobUrl = baseUrl + "/job/" + jobPath;
                String logUrl = jobUrl + "/lastBuild/consoleText";
                return AutoSubmissionResult.ok(executionId, jobUrl, logUrl, null);
            } else {
                return AutoSubmissionResult.failure(
                        "Jenkins returned HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Jenkins submission failed", e);
            return AutoSubmissionResult.failure("Jenkins submission failed: " + e.getMessage());
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
            String user    = config.serviceUser();
            String token   = config.credential();
            String auth    = basicAuth(user, token);

            String jobUrl = executionHistory.getExternalJobUrl();
            if (jobUrl == null) {
                return AutoPollResult.unknown("No external job URL recorded; cannot poll");
            }

            // Phase 1: Resolve queue item to build URL using externalExecutionId (queue item number)
            String executionId = executionHistory.getExternalExecutionId();
            String buildUrl = null;

            if (executionId != null && !executionId.equals("unknown")) {
                String queueApiUrl = baseUrl + "/queue/item/" + executionId + "/api/json";
                try {
                    ResponseEntity<Map> queueResponse = getJson(queueApiUrl, auth, Map.class);
                    if (queueResponse.getStatusCode().is2xxSuccessful() && queueResponse.getBody() != null) {
                        Map<String, Object> body = queueResponse.getBody();
                        Object executable = body.get("executable");
                        if (executable instanceof Map) {
                            buildUrl = (String) ((Map<?, ?>) executable).get("url");
                        } else {
                            Object cancelled = body.get("cancelled");
                            if (Boolean.TRUE.equals(cancelled)) {
                                return AutoPollResult.failed(ExternalStatus.ABORTED,
                                        "Jenkins queue item was cancelled", jobUrl, null);
                            }
                            return AutoPollResult.running(ExternalStatus.QUEUED, "Waiting in Jenkins queue", jobUrl, null);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Queue item {} no longer available, polling job URL directly", executionId);
                }
            }

            // Phase 2: Poll build status (use resolved build URL or fall back to job URL)
            String pollUrl = buildUrl != null ? buildUrl : jobUrl;
            String buildApiUrl = trimTrailingSlash(pollUrl) + "/api/json";
            String logUrl = trimTrailingSlash(pollUrl) + "/consoleText";

            ResponseEntity<Map> buildResponse = getJson(buildApiUrl, auth, Map.class);
            if (!buildResponse.getStatusCode().is2xxSuccessful() || buildResponse.getBody() == null) {
                return AutoPollResult.unknown("Could not poll Jenkins build; HTTP "
                        + buildResponse.getStatusCode().value());
            }

            Map<String, Object> build = buildResponse.getBody();
            Boolean building = (Boolean) build.get("building");
            String result = (String) build.get("result");

            if (Boolean.TRUE.equals(building)) {
                // Check for pending input actions (pipeline paused at an `input` step)
                if (hasPendingInputActions(pollUrl, auth)) {
                    String approvalUrl = trimTrailingSlash(pollUrl) + "/input";
                    return new AutoPollResult(
                            ExternalStatus.WAITING_APPROVAL, false, ExecutionStatus.Running,
                            "Build is paused — waiting for approval in Jenkins",
                            null, jobUrl, logUrl, approvalUrl, null, null, java.time.Instant.now());
                }
                return AutoPollResult.running(ExternalStatus.RUNNING, "Build is running", jobUrl, logUrl);
            }

            return mapJenkinsResult(result, jobUrl, logUrl);

        } catch (Exception e) {
            log.warn("Jenkins poll error for execution {}: {}", executionHistory.getId(), e.getMessage());
            return AutoPollResult.unknown("Jenkins poll error: " + e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private boolean hasPendingInputActions(String buildUrl, String auth) {
        try {
            String inputUrl = trimTrailingSlash(buildUrl) + "/wfapi/pendingInputActions";
            ResponseEntity<java.util.List> response = getJson(inputUrl, auth, java.util.List.class);
            return response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && !response.getBody().isEmpty();
        } catch (Exception e) {
            log.debug("Could not check pending input actions for {}: {}", buildUrl, e.getMessage());
            return false;
        }
    }

    private AutoPollResult mapJenkinsResult(String result, String jobUrl, String logUrl) {
        if (result == null) {
            return AutoPollResult.running(ExternalStatus.RUNNING, "Build in progress", jobUrl, logUrl);
        }
        return switch (result.toUpperCase()) {
            case "SUCCESS" -> AutoPollResult.succeeded("Build succeeded", jobUrl, logUrl, null, null);
            case "FAILURE", "UNSTABLE" ->
                    AutoPollResult.failed(ExternalStatus.FAILED, "Build failed: " + result, jobUrl, logUrl);
            case "ABORTED" ->
                    AutoPollResult.failed(ExternalStatus.ABORTED, "Build was aborted", jobUrl, logUrl);
            default ->
                    AutoPollResult.unknown("Unknown Jenkins build result: " + result);
        };
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> getJson(String url, String auth, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, auth);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        return (ResponseEntity<T>) restTemplate.exchange(url, HttpMethod.GET, request, type);
    }

    @SuppressWarnings("unchecked")
    private MultiValueMap<String, String> buildFormParameters(Map<String, Object> inputParameters) {
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();

        Object params = inputParameters.get("parameters");
        if (params instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) params;
            paramMap.forEach((k, v) -> formParams.add(k, v != null ? v.toString() : ""));
        } else if (params != null) {
            formParams.add("PARAMETERS", params.toString());
        }

        for (Map.Entry<String, Object> entry : inputParameters.entrySet()) {
            String key = entry.getKey();
            if (!RESERVED_KEYS.contains(key) && !"parameters".equals(key) && entry.getValue() != null) {
                formParams.add(key, entry.getValue().toString());
            }
        }
        return formParams;
    }

    private static String basicAuth(String user, String token) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractQueueId(String queueUrl) {
        String[] parts = queueUrl.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) return parts[i];
        }
        return queueUrl;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
