package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.ConfigurationComponentDto;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.contracts.enums.ExternalStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.configuration.ConfigurationComponentService;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.helper.TestDataHelper;
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
    @Autowired private ConfigurationComponentService configurationComponentService;

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
    @DisplayName("submitting a specific AUTO task uses that task's own input instead of the first task in the request")
    void submitAuto_usesSelectedTaskInput() {
        Task firstTask = seedAutoTask(TaskStatus.Pending, "first-job");
        firstTask.setStepSeq(1);
        firstTask.setTaskName("auto-deploy-1");
        taskRepository.save(firstTask);

        Task secondTask = seedAutoTask(TaskStatus.Ready_For_Execution, "second-job");
        secondTask.setStepSeq(2);
        secondTask.setTaskName("auto-deploy-2");
        taskRepository.save(secondTask);

        seedJenkinsConfig();
        mockJenkinsSuccess();

        Task result = autoExecutionService.submitAutoExecution(secondTask.getId(), ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Executing);
        verify(restTemplate).postForEntity(
                eq("http://jenkins:8080/job/second-job/buildWithParameters"),
                any(),
                eq(String.class));

        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(secondTask.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getInputSnapshot())
                .containsEntry("script", "second-job")
                .containsEntry("parameters", "--env sit");
    }

    @Test
    @DisplayName("scoped component config is used for submission and captured in execution history")
    void submitAuto_scopedConfigUsedAndSnapshotted() {
        request.setApplication("AMH HCC");
        request.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        request.setAgent("Deployment Agent");

        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        configurationComponentService.upsertComponent(
                new ConfigurationComponentDto.UpsertRequest(
                        null,
                        "jenkins",
                        "Jenkins Pipeline",
                        "CI/CD",
                        null,
                        null,
                        null,
                        "http://default-jenkins:8080",
                        "default-user",
                        "default-token",
                        null
                ),
                adminUser
        );
        configurationComponentService.upsertComponent(
                new ConfigurationComponentDto.UpsertRequest(
                        null,
                        "jenkins",
                        "Jenkins Pipeline",
                        "CI/CD",
                        "AMH HCC",
                        "HTSA-CSI-HCC-AMH-PRJ",
                        "Deployment Agent",
                        "http://agent-jenkins:8080",
                        "agent-user",
                        "agent-token",
                        null
                ),
                adminUser
        );
        mockJenkinsSuccess();

        autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        verify(restTemplate).postForEntity(eq("http://agent-jenkins:8080/job/deploy-job/buildWithParameters"), any(), eq(String.class));

        TaskExecutionHistory history = executionHistoryRepository.findByTaskIdOrderByAttemptNumberAsc(task.getId()).get(0);
        assertThat(history.getConfigApplication()).isEqualTo("AMH HCC");
        assertThat(history.getConfigSnowGroup()).isEqualTo("HTSA-CSI-HCC-AMH-PRJ");
        assertThat(history.getConfigAgent()).isEqualTo("Deployment Agent");
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

    @Test
    @DisplayName("adapter failure with long message is truncated before persistence")
    void submitAuto_adapterFailure_longMessageTruncated() {
        Task task = seedAutoTask(TaskStatus.Ready_For_Execution, "deploy-job");
        seedJenkinsConfig();
        String longMessage = "Jenkins error: " + "x".repeat(24_951);

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException(longMessage));

        Task result = autoExecutionService.submitAutoExecution(task.getId(), ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Failed);

        TaskExecutionHistory history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId())
                .get(0);
        assertThat(history.getSubmissionMessage()).hasSize(2000).endsWith("...");
        assertThat(history.getExternalStatusMessage()).hasSize(2000).endsWith("...");
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
    private com.wwa.agenthub.domain.configuration.ConfigurationRepository configRepository;

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
                createConfig(com.wwa.agenthub.contracts.enums.ConfigKey.jenkins_url, "http://jenkins:8080"),
                createConfig(com.wwa.agenthub.contracts.enums.ConfigKey.jenkins_user, "admin"),
                createConfig(com.wwa.agenthub.contracts.enums.ConfigKey.jenkins_api_token, "test-token")
        );
        configRepository.saveAll(items);
    }

    private com.wwa.agenthub.domain.configuration.ConfigurationItem createConfig(
            com.wwa.agenthub.contracts.enums.ConfigKey key, String value) {
        var item = new com.wwa.agenthub.domain.configuration.ConfigurationItem();
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
