package com.wwa.deploymentagent.domain.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.configuration.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Submits AUTO tasks to Ansible Tower/AWX via its REST API.
 *
 * <p>Reads Ansible URL and API token from the configuration store.
 * Launches a job template and extracts the job ID from the response.
 *
 * <h3>Input parameter mapping</h3>
 * <ul>
 *   <li>{@code script} → AWX job template ID (e.g. "42")</li>
 *   <li>{@code parameters} → passed as {@code extra_vars} string to the launch endpoint</li>
 *   <li>{@code system} → must be "ANSIBLE" (routing handled by AutoExecutionService)</li>
 * </ul>
 *
 * <h3>External job URL</h3>
 * <p>The stored URL points to the AWX/Tower <b>UI</b> path ({@code /#/jobs/playbook/{id}})
 * so that users can click through directly to view the job output in a browser.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnsibleExecutionAdapter implements AutoExecutionAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigurationService configurationService;
    private final RestTemplate restTemplate;

    @Override
    public String systemType() {
        return "ANSIBLE";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AutoSubmissionResult submit(Map<String, Object> inputParameters) {
        try {
            String baseUrl = getConfig(ConfigKey.ansible_url);
            String token = getConfig(ConfigKey.ansible_api_token);

            String templateId = (String) inputParameters.getOrDefault("script", "1");
            String launchUrl = baseUrl + "/api/v2/job_templates/" + templateId + "/launch/";

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
                // UI URL so users can click through to the job output in a browser
                String jobUrl = baseUrl + "/#/jobs/playbook/" + executionId;
                return AutoSubmissionResult.ok(executionId, jobUrl);
            } else {
                return AutoSubmissionResult.failure(
                        "Ansible returned HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Ansible submission failed", e);
            return AutoSubmissionResult.failure("Ansible submission failed: " + e.getMessage());
        }
    }

    /**
     * Builds the JSON request body for the AWX launch endpoint.
     * Uses Jackson ObjectMapper to safely serialize extra_vars,
     * avoiding JSON injection from user-supplied parameter values.
     */
    private String buildRequestBody(Map<String, Object> inputParameters) throws JsonProcessingException {
        Object params = inputParameters.get("parameters");
        if (params == null) {
            return "{}";
        }

        Map<String, Object> requestBody = new HashMap<>();
        // AWX expects extra_vars as a JSON string or an object.
        // We pass it as a string to match the most common AWX configuration.
        requestBody.put("extra_vars", params.toString());
        return OBJECT_MAPPER.writeValueAsString(requestBody);
    }

    private String getConfig(ConfigKey key) {
        return configurationService.getByKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Configuration missing: " + key.name()))
                .getConfigValue();
    }
}
