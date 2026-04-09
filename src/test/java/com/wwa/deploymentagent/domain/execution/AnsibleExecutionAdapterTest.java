package com.wwa.deploymentagent.domain.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.deploymentagent.domain.configuration.ConfigurationComponentService;
import com.wwa.deploymentagent.domain.configuration.ConfigurationScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AnsibleExecutionAdapter")
class AnsibleExecutionAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigurationComponentService configurationComponentService = mock(ConfigurationComponentService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AnsibleExecutionAdapter adapter = new AnsibleExecutionAdapter(configurationComponentService, restTemplate);

    @BeforeEach
    void setUp() {
        when(configurationComponentService.resolveForSystem(eq("ANSIBLE"), any(ConfigurationScope.class)))
                .thenReturn(new ConfigurationComponentService.ResolvedSystemConfiguration("http://awx", null, "token"));
    }

    @Test
    @DisplayName("structured JSON parameters are sent to AWX as structured extra_vars")
    void submit_structuredJsonParameters_preservesExtraVarsShape() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("id", 42), HttpStatus.OK));

        AutoSubmissionResult result = adapter.submit(
                new ExecutionTarget("ANSIBLE", ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE, "42", "42", null, true),
                Map.of(
                        "script", "42",
                        "parameters", "{\"inventory\":\"prod\",\"limit\":\"web\"}"
                ),
                ConfigurationScope.empty());

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://awx/api/v2/job_templates/42/launch/"),
                entityCaptor.capture(),
                eq(Map.class));

        Map<String, Object> requestBody = OBJECT_MAPPER.readValue(
                entityCaptor.getValue().getBody(),
                new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> extraVars = (Map<String, Object>) requestBody.get("extra_vars");

        assertThat(result.success()).isTrue();
        assertThat(requestBody).containsKey("extra_vars");
        assertThat(extraVars)
                .containsEntry("inventory", "prod")
                .containsEntry("limit", "web");
    }

    @Test
    @DisplayName("plain text parameters remain compatible while preserving additional task input fields")
    void submit_plainTextParameters_keepsRawStringAndExtraFields() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("id", 99), HttpStatus.OK));

        adapter.submit(
                new ExecutionTarget("ANSIBLE", ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE, "99", "99", null, true),
                Map.of(
                        "script", "99",
                        "parameters", "--env sit",
                        "inventory", "blue",
                        "system", "ANSIBLE"
                ),
                ConfigurationScope.empty());

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://awx/api/v2/job_templates/99/launch/"),
                entityCaptor.capture(),
                eq(Map.class));

        Map<String, Object> requestBody = OBJECT_MAPPER.readValue(
                entityCaptor.getValue().getBody(),
                new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> extraVars = (Map<String, Object>) requestBody.get("extra_vars");

        assertThat(extraVars)
                .containsEntry("inventory", "blue")
                .containsEntry("PARAMETERS", "--env sit")
                .doesNotContainKey("system");
    }
}
