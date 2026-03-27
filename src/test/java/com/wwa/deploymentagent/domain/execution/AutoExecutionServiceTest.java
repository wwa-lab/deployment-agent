package com.wwa.deploymentagent.domain.execution;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.ExternalStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryRepository;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ConflictAppException;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AutoExecutionService")
class AutoExecutionServiceTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public RestTemplate mockRestTemplate() {
            return mock(RestTemplate.class);
        }
    }

    @Autowired private AutoExecutionService autoExecutionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskExecutionHistoryRepository executionHistoryRepository;
    @Autowired private TestDataHelper helper;
    @Autowired private RestTemplate restTemplate;

    private ReleaseFlow releaseFlow;
    private Request request;
    private UserContext ownerUser;
    private UserContext adminUser;
    private UserContext nonOwnerUser;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow);
        ownerUser  = new UserContext("emp-001", "DEVELOPER");
        adminUser  = new UserContext("emp-003", "DEVOPS_ADMIN");
        nonOwnerUser = new UserContext("dev-user", "DEVELOPER");

        reset(restTemplate);
    }

    // ─── Success path (Jenkins) ───────────────────────────────────────────────

    @Test
    @DisplayName("AUTO + Ready_For_Execution → task becomes Executing on successful Jenkins submission")
    void submitAuto_jenkins_success_transitionsToExecuting() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        seedJenkinsConfig();

        mockJenkinsSuccess();

        Task result = autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Executing);
        assertThat(result.getLatestExecutionId()).isNotNull();
    }

    @Test
    @DisplayName("execution history is created with external metadata and QUEUED status on success")
    void submitAuto_success_createsExecutionHistoryWithExternalMetadata() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        seedJenkinsConfig();
        mockJenkinsSuccess();

        autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId());
        assertThat(history).hasSize(1);

        TaskExecutionHistory h = history.get(0);
        assertThat(h.getExternalSystemType()).isEqualTo("JENKINS");
        assertThat(h.getSubmissionStatus()).isEqualTo("SUBMITTED");
        assertThat(h.getExternalJobUrl()).contains("jenkins");
        assertThat(h.getSubmittedAt()).isNotNull();
        assertThat(h.getExternalStatus()).isEqualTo(ExternalStatus.QUEUED);
        assertThat(h.getExternalStatusMessage()).isNotBlank();
    }

    @Test
    @DisplayName("AUTO task with Jenkins URL in script → resolves via URL inference")
    void submitAuto_jenkinsUrl_resolvedByUrlInference() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "http://jenkins:8080/job/my-pipeline/");
        seedJenkinsConfig();
        mockJenkinsSuccess();

        Task result = autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Executing);
        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId());
        assertThat(history.get(0).getExternalSystemType()).isEqualTo("JENKINS");
    }

    @Test
    @DisplayName("DEVOPS_ADMIN can submit an AUTO task")
    void submitAuto_adminUser_succeeds() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        seedJenkinsConfig();
        mockJenkinsSuccess();

        Task result = autoExecutionService.submitAutoExecution(task.getId(), adminUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Executing);
    }

    @Test
    @DisplayName("adapter failure → task becomes Failed, execution history shows FAILED submission")
    void submitAuto_adapterFailure_transitionsToFailed() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        seedJenkinsConfig();

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        Task result = autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Failed);

        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSubmissionStatus()).isEqualTo("FAILED");
        assertThat(history.get(0).getSubmissionMessage()).contains("Connection refused");
        assertThat(history.get(0).getExternalStatus()).isEqualTo(ExternalStatus.FAILED);
    }

    // ─── Guard violations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("MANUAL task → throws ConflictAppException")
    void submitAuto_manualTask_throwsConflict() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);

        assertThatThrownBy(() ->
                autoExecutionService.submitAutoExecution(task.getId(), ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("not an AUTO task");
    }

    @Test
    @DisplayName("wrong state (Pending) → throws ConflictAppException")
    void submitAuto_wrongState_throwsConflict() {
        Task task = seedAutoTask(TaskStatus.Pending, "deploy-job");

        assertThatThrownBy(() ->
                autoExecutionService.submitAutoExecution(task.getId(), ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("Ready_For_Execution");
    }

    @Test
    @DisplayName("non-owner developer → throws ForbiddenAppException")
    void submitAuto_nonOwner_throwsForbidden() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");

        assertThatThrownBy(() ->
                autoExecutionService.submitAutoExecution(task.getId(), nonOwnerUser))
                .isInstanceOf(ForbiddenAppException.class);
    }

    @Test
    @DisplayName("unknown task ID → throws NotFoundAppException")
    void submitAuto_unknownTask_throwsNotFound() {
        assertThatThrownBy(() ->
                autoExecutionService.submitAutoExecution("non-existent", ownerUser))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @Autowired
    private com.wwa.deploymentagent.domain.configuration.ConfigurationRepository configRepository;

    private Task seedAutoTask(TaskStatus status, String script) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskGroupId("TG-AUTO");
        task.setTaskGroupName("Auto Group");
        task.setStepSeq(1);
        task.setTaskName("auto-deploy");
        task.setExecutionType(ExecutionType.AUTO);
        task.setTaskStatus(status);
        task.setInputParameters(Map.of("script", script, "parameters", "--env sit"));
        task.setOwner("alice");
        return taskRepository.save(task);
    }

    private Task seedManualTask(TaskStatus status) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskGroupId("TG-MANUAL");
        task.setTaskGroupName("Manual Group");
        task.setStepSeq(1);
        task.setTaskName("manual-step");
        task.setExecutionType(ExecutionType.MANUAL);
        task.setTaskStatus(status);
        task.setOwner("alice");
        return taskRepository.save(task);
    }

    private void seedJenkinsConfig() {
        var items = List.of(
                createConfig(com.wwa.deploymentagent.contracts.enums.ConfigKey.jenkins_url, "http://jenkins:8080"),
                createConfig(com.wwa.deploymentagent.contracts.enums.ConfigKey.jenkins_user, "admin"),
                createConfig(com.wwa.deploymentagent.contracts.enums.ConfigKey.jenkins_api_token, "test-token")
        );
        configRepository.saveAll(items);
    }

    private com.wwa.deploymentagent.domain.configuration.ConfigurationItem createConfig(
            com.wwa.deploymentagent.contracts.enums.ConfigKey key, String value) {
        var item = new com.wwa.deploymentagent.domain.configuration.ConfigurationItem();
        item.setConfigKey(key);
        item.setConfigValue(value);
        item.setUpdatedBy("test");
        return item;
    }

    private void mockJenkinsSuccess() {
        ResponseEntity<String> mockResponse = ResponseEntity.status(201)
                .header("Location", "http://jenkins:8080/queue/item/42/")
                .body("");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);
    }
}
