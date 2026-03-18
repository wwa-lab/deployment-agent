package com.wwa.deploymentagent.domain.execution;

import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.configuration.ConfigurationService;
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
 * Submits AUTO tasks to Jenkins via its Remote Access API.
 *
 * <p>Reads Jenkins URL, user, and API token from the configuration store.
 * Triggers a parameterized build and extracts the queue location from the response.
 *
 * <h3>Input parameter mapping</h3>
 * <p>The task's {@code inputParameters} map is mapped to Jenkins as follows:
 * <ul>
 *   <li>{@code script} → Jenkins job name (the path segment in {@code /job/{name}/buildWithParameters})</li>
 *   <li>{@code parameters} → if it is a Map, each entry becomes a named Jenkins build parameter.
 *       If it is a plain String, it is sent as a single parameter named {@code PARAMETERS}.
 *       This supports both structured parameters (from the config/edit UI) and freeform strings
 *       (from Excel import).</li>
 *   <li>All other keys in {@code inputParameters} (except {@code script}, {@code system},
 *       {@code parameters}) are also forwarded as named Jenkins parameters, enabling direct
 *       pass-through from the Excel "Parameters" column when it is parsed as key=value pairs.</li>
 * </ul>
 *
 * <h3>External job URL</h3>
 * <p>Jenkins returns a {@code Location} header pointing to the queue item
 * (e.g. {@code http://jenkins:8080/queue/item/42/}). This is stored as the external job URL.
 * The queue item page in the Jenkins UI shows the build link once the job starts executing,
 * so the user can navigate from queue → build from one click.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JenkinsExecutionAdapter implements AutoExecutionAdapter {

    private static final java.util.Set<String> RESERVED_KEYS =
            java.util.Set.of("script", "system");

    private final ConfigurationService configurationService;
    private final RestTemplate restTemplate;

    @Override
    public String systemType() {
        return "JENKINS";
    }

    @Override
    public AutoSubmissionResult submit(Map<String, Object> inputParameters) {
        try {
            String baseUrl = getConfig(ConfigKey.jenkins_url);
            String user = getConfig(ConfigKey.jenkins_user);
            String token = getConfig(ConfigKey.jenkins_api_token);

            String jobName = (String) inputParameters.getOrDefault("script", "default-job");
            String buildUrl = baseUrl + "/job/" + jobName + "/buildWithParameters";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String auth = Base64.getEncoder()
                    .encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + auth);

            MultiValueMap<String, String> formParams = buildFormParameters(inputParameters);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String queueUrl = response.getHeaders().getFirst("Location");
                String executionId = queueUrl != null ? extractQueueId(queueUrl) : "unknown";
                String jobUrl = queueUrl != null ? queueUrl : (baseUrl + "/job/" + jobName);
                return AutoSubmissionResult.ok(executionId, jobUrl);
            } else {
                return AutoSubmissionResult.failure(
                        "Jenkins returned HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Jenkins submission failed", e);
            return AutoSubmissionResult.failure("Jenkins submission failed: " + e.getMessage());
        }
    }

    /**
     * Builds form parameters for the Jenkins buildWithParameters endpoint.
     *
     * <p>Strategy:
     * <ol>
     *   <li>If {@code parameters} is a Map → each entry becomes a named form parameter</li>
     *   <li>If {@code parameters} is a String → sent as a single form parameter named PARAMETERS</li>
     *   <li>All other non-reserved keys from inputParameters are also added as named parameters</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private MultiValueMap<String, String> buildFormParameters(Map<String, Object> inputParameters) {
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();

        Object params = inputParameters.get("parameters");
        if (params instanceof Map) {
            // Structured parameters: each key-value becomes a named Jenkins parameter
            Map<String, Object> paramMap = (Map<String, Object>) params;
            paramMap.forEach((k, v) -> formParams.add(k, v != null ? v.toString() : ""));
        } else if (params != null) {
            // Freeform string: send as a single PARAMETERS field
            formParams.add("PARAMETERS", params.toString());
        }

        // Forward any additional top-level keys as named parameters
        // (supports Excel columns that map directly to Jenkins parameter names)
        for (Map.Entry<String, Object> entry : inputParameters.entrySet()) {
            String key = entry.getKey();
            if (!RESERVED_KEYS.contains(key) && !"parameters".equals(key) && entry.getValue() != null) {
                formParams.add(key, entry.getValue().toString());
            }
        }

        return formParams;
    }

    private String getConfig(ConfigKey key) {
        return configurationService.getByKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Configuration missing: " + key.name()))
                .getConfigValue();
    }

    private String extractQueueId(String queueUrl) {
        // Jenkins queue URLs: .../queue/item/123/
        String[] parts = queueUrl.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) return parts[i];
        }
        return queueUrl;
    }
}
