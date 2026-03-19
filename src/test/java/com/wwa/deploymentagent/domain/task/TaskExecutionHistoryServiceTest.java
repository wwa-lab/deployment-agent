package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TaskExecutionHistoryService")
class TaskExecutionHistoryServiceTest {

    @Autowired private TaskExecutionHistoryService executionHistoryService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TestDataHelper helper;

    private Task task;

    @BeforeEach
    void setUp() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        task = helper.seedTask(req, TaskStatus.Ready_For_Execution);
    }

    @Test
    @DisplayName("creates first execution with attempt_number=1 and Running status")
    void createExecution_firstAttempt() {
        TaskExecutionHistory exec = executionHistoryService.createExecution(task.getId());

        assertThat(exec.getId()).isNotNull();
        assertThat(exec.getAttemptNumber()).isEqualTo(1);
        assertThat(exec.getExecutionStatus()).isEqualTo(ExecutionStatus.Running);
        assertThat(exec.getStartTime()).isNotNull();
        assertThat(exec.getEndTime()).isNull();
        assertThat(exec.getInputSnapshot()).isNotNull();
    }

    @Test
    @DisplayName("creates second execution with attempt_number=2 on rerun")
    void createExecution_secondAttempt() {
        executionHistoryService.createExecution(task.getId());
        TaskExecutionHistory second = executionHistoryService.createExecution(task.getId());

        assertThat(second.getAttemptNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("updates Task.latestExecutionId after creating execution")
    void createExecution_updatesLatestExecutionId() {
        TaskExecutionHistory exec = executionHistoryService.createExecution(task.getId());

        Task refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.getLatestExecutionId()).isEqualTo(exec.getId());
    }

    @Test
    @DisplayName("findByTaskId returns all executions ordered by attempt number")
    void findByTaskId_returnsOrdered() {
        executionHistoryService.createExecution(task.getId());
        executionHistoryService.createExecution(task.getId());

        List<TaskExecutionHistory> history = executionHistoryService.findByTaskId(task.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAttemptNumber()).isLessThan(history.get(1).getAttemptNumber());
    }

    @Test
    @DisplayName("findLatest returns the highest attempt number")
    void findLatest_returnsHighestAttempt() {
        executionHistoryService.createExecution(task.getId());
        TaskExecutionHistory second = executionHistoryService.createExecution(task.getId());

        Optional<TaskExecutionHistory> latest = executionHistoryService.findLatest(task.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("completeExecution sets status, result, and endTime")
    void completeExecution_setsAllFields() {
        TaskExecutionHistory exec = executionHistoryService.createExecution(task.getId());
        Map<String, Object> summary = Map.of("status", "ok");

        TaskExecutionHistory completed = executionHistoryService.completeExecution(
                exec.getId(), ExecutionStatus.Completed, summary, "log output here");

        assertThat(completed.getExecutionStatus()).isEqualTo(ExecutionStatus.Completed);
        assertThat(completed.getResultSummary()).containsEntry("status", "ok");
        assertThat(completed.getResultLogs()).isEqualTo("log output here");
        assertThat(completed.getEndTime()).isNotNull();
    }
}
