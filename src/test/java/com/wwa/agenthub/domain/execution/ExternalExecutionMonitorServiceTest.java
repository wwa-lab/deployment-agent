package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.contracts.enums.*;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ExternalExecutionMonitorService.
 *
 * <p>Uses real Spring context with H2. Adapters are replaced with @MockitoBean
 * to control poll results precisely.
 *
 * <p>Note: {@code processSingleExecution} uses {@code @Transactional} (REQUIRED).
 * In production this always creates a new tx (called from non-transactional scheduler).
 * In tests it participates in the test's transaction → seeds are visible and cleanup is free.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ExternalExecutionMonitorService")
class ExternalExecutionMonitorServiceTest {

    // Replace real adapters with mocks so no real HTTP calls are made
    @MockitoBean private JenkinsExecutionAdapter jenkinsAdapter;
    @MockitoBean private AnsibleExecutionAdapter ansibleAdapter;
    @MockitoBean private RestTemplate restTemplate;

    @Autowired private ExternalExecutionMonitorService monitorService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskExecutionHistoryRepository executionHistoryRepository;
    @Autowired private TestDataHelper helper;

    private Request request;

    @BeforeEach
    void setUp() {
        ReleaseFlow releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow);

        // Both adapters identify their system types
        when(jenkinsAdapter.systemType()).thenReturn("JENKINS");
        when(ansibleAdapter.systemType()).thenReturn("ANSIBLE");
    }

    // ─── SUCCEEDED path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUCCEEDED poll result → execution Completed, task Awaiting_Review")
    void process_succeeded_transitionsToAwaitingReview() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.RUNNING, "JENKINS");

        when(jenkinsAdapter.pollStatus(any())).thenReturn(
                AutoPollResult.succeeded("Build done", "http://jenkins/job/42/1",
                        "http://jenkins/job/42/1/console", null, null));

        monitorService.processSingleExecution(history.getId());

        TaskExecutionHistory refreshed = executionHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(refreshed.getExecutionStatus()).isEqualTo(ExecutionStatus.Completed);
        assertThat(refreshed.getExternalStatus()).isEqualTo(ExternalStatus.SUCCEEDED);
        assertThat(refreshed.getLastSyncedAt()).isNotNull();
        assertThat(refreshed.getEndTime()).isNotNull();
        assertThat(refreshed.getExternalJobUrl()).isEqualTo("http://jenkins/job/42/1");
        assertThat(refreshed.getExternalLogUrl()).isEqualTo("http://jenkins/job/42/1/console");

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
    }

    // ─── FAILED path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("FAILED poll result → execution Failed, task Failed")
    void process_failed_transitionsToFailed() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.RUNNING, "JENKINS");

        when(jenkinsAdapter.pollStatus(any())).thenReturn(
                AutoPollResult.failed(ExternalStatus.FAILED, "Build failed",
                        "http://jenkins/job/42/1", "http://jenkins/job/42/1/console"));

        monitorService.processSingleExecution(history.getId());

        TaskExecutionHistory refreshed = executionHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(refreshed.getExecutionStatus()).isEqualTo(ExecutionStatus.Failed);
        assertThat(refreshed.getExternalStatus()).isEqualTo(ExternalStatus.FAILED);
        assertThat(refreshed.getEndTime()).isNotNull();

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Failed);
    }

    // ─── ABORTED path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ABORTED poll result → execution Failed, task Failed")
    void process_aborted_transitionsToFailed() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.RUNNING, "JENKINS");

        when(jenkinsAdapter.pollStatus(any())).thenReturn(
                AutoPollResult.failed(ExternalStatus.ABORTED, "Build aborted",
                        "http://jenkins/job/42/1", null));

        monitorService.processSingleExecution(history.getId());

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Failed);
    }

    // ─── RUNNING (non-terminal) ────────────────────────────────────────────────

    @Test
    @DisplayName("RUNNING poll result → only updates external status, task remains Executing")
    void process_running_updatesStatusOnly() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.QUEUED, "JENKINS");

        when(jenkinsAdapter.pollStatus(any())).thenReturn(
                AutoPollResult.running(ExternalStatus.RUNNING, "Build running",
                        "http://jenkins/job/42/1", "http://jenkins/log"));

        monitorService.processSingleExecution(history.getId());

        TaskExecutionHistory refreshed = executionHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(refreshed.getExecutionStatus()).isEqualTo(ExecutionStatus.Running);
        assertThat(refreshed.getExternalStatus()).isEqualTo(ExternalStatus.RUNNING);
        assertThat(refreshed.getExternalStatusMessage()).isEqualTo("Build running");
        assertThat(refreshed.getExternalLogUrl()).isEqualTo("http://jenkins/log");
        assertThat(refreshed.getLastSyncedAt()).isNotNull();

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Executing);
    }

    // ─── WAITING_APPROVAL ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WAITING_APPROVAL → task stays Executing, approval URL recorded")
    void process_waitingApproval_taskStaysExecuting() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.RUNNING, "ANSIBLE");

        AutoPollResult waitingApproval = new AutoPollResult(
                ExternalStatus.WAITING_APPROVAL, false, ExecutionStatus.Running,
                "Waiting for approval", null, "http://awx/jobs/1",
                "http://awx/jobs/1/log", "http://awx/approvals/7",
                null, null, Instant.now());
        when(ansibleAdapter.pollStatus(any())).thenReturn(waitingApproval);

        monitorService.processSingleExecution(history.getId());

        TaskExecutionHistory refreshed = executionHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(refreshed.getExternalStatus()).isEqualTo(ExternalStatus.WAITING_APPROVAL);
        assertThat(refreshed.getExternalApprovalUrl()).isEqualTo("http://awx/approvals/7");
        assertThat(refreshed.getExecutionStatus()).isEqualTo(ExecutionStatus.Running);

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Executing);
    }

    // ─── Idempotency ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("already-Completed execution → skipped (task stays Awaiting_Review)")
    void process_alreadyCompleted_isSkipped() {
        Task task = seedAutoTask(TaskStatus.Awaiting_Review);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Completed, ExternalStatus.SUCCEEDED, "JENKINS");

        monitorService.processSingleExecution(history.getId());

        // adapter.pollStatus should never be called for non-Running executions
        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
    }

    // ─── UNKNOWN (non-terminal) ────────────────────────────────────────────────

    @Test
    @DisplayName("UNKNOWN poll result → execution stays Running, task stays Executing")
    void process_unknown_keepsPriorState() {
        Task task = seedAutoTask(TaskStatus.Executing);
        TaskExecutionHistory history = seedHistory(task, ExecutionStatus.Running, ExternalStatus.RUNNING, "JENKINS");

        when(jenkinsAdapter.pollStatus(any())).thenReturn(
                AutoPollResult.unknown("Could not reach Jenkins API"));

        monitorService.processSingleExecution(history.getId());

        TaskExecutionHistory refreshed = executionHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(refreshed.getExecutionStatus()).isEqualTo(ExecutionStatus.Running);
        // UNKNOWN should not overwrite the previous known external status
        assertThat(refreshed.getExternalStatus()).isEqualTo(ExternalStatus.RUNNING);

        Task refreshedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshedTask.getTaskStatus()).isEqualTo(TaskStatus.Executing);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Task seedAutoTask(TaskStatus status) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskGroupId("TG-MON");
        task.setTaskGroupName("Monitor Group");
        task.setStepSeq(1);
        task.setTaskName("auto-monitor-task");
        task.setExecutionType(ExecutionType.AUTO);
        task.setTaskStatus(status);
        task.setInputParameters(Map.of("script", "deploy-job"));
        task.setOwner("alice");
        task.setStartTime(Instant.now());
        return taskRepository.save(task);
    }

    private TaskExecutionHistory seedHistory(Task task, ExecutionStatus executionStatus,
                                             ExternalStatus externalStatus, String systemType) {
        TaskExecutionHistory h = new TaskExecutionHistory();
        h.setTask(task);
        h.setAttemptNumber(1);
        h.setExecutionStatus(executionStatus);
        h.setExternalSystemType(systemType);
        h.setExternalExecutionId("42");
        h.setExternalJobUrl("http://jenkins:8080/queue/item/42/");
        h.setExternalStatus(externalStatus);
        h.setStartTime(Instant.now());
        h.setSubmittedAt(Instant.now());
        h.setSubmissionStatus("SUBMITTED");
        return executionHistoryRepository.save(h);
    }
}
