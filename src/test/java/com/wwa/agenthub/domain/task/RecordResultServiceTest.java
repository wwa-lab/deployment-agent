package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RecordResultService")
class RecordResultServiceTest {

    @Autowired private RecordResultService recordResultService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskExecutionHistoryRepository executionHistoryRepository;
    @Autowired private TestDataHelper helper;

    private ReleaseFlow releaseFlow;
    private Request request;
    private UserContext ownerUser;
    private UserContext adminUser;
    private UserContext nonOwnerUser;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow);
        ownerUser = TestDataHelper.globallyScopedUser("emp-001", "DEVELOPER");
        adminUser = new UserContext("emp-003", "DEVOPS_ADMIN");
        nonOwnerUser = TestDataHelper.globallyScopedUser("dev-user", "DEVELOPER");
    }

    // ─── Success path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("MANUAL + Ready_For_Execution → task becomes Awaiting_Review")
    void recordResult_success_transitionsToAwaitingReview() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);
        Map<String, Object> summary = Map.of("outcome", "passed");

        Task result = recordResultService.recordResult(task.getId(), summary, "log output", ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
        assertThat(result.getCurrentResultSummary()).containsEntry("outcome", "passed");
        assertThat(result.getLatestExecutionId()).isNotNull();
    }

    @Test
    @DisplayName("execution history record is created with Completed status")
    void recordResult_createsExecutionHistory() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);

        recordResultService.recordResult(task.getId(), Map.of("status", "ok"), null, ownerUser);

        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getExecutionStatus())
                .isEqualTo(com.wwa.agenthub.contracts.enums.ExecutionStatus.Completed);
        assertThat(history.get(0).getAttemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("MANUAL + Executing → task becomes Awaiting_Review")
    void recordResult_executingState_transitionsToAwaitingReview() {
        Task task = seedManualTask(TaskStatus.Executing);

        Task result = recordResultService.recordResult(task.getId(), Map.of("outcome", "passed"), null, ownerUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
    }

    @Test
    @DisplayName("admin can record result for a manual task")
    void recordResult_adminUser_succeeds() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);

        Task result = recordResultService.recordResult(task.getId(), Map.of("status", "ok"), null, adminUser);

        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
    }

    @Test
    @DisplayName("second record result increments attempt number")
    void recordResult_secondCall_incrementsAttemptNumber() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);
        Map<String, Object> summary = Map.of("status", "ok");

        // First record
        recordResultService.recordResult(task.getId(), summary, null, ownerUser);

        // Reset task back to Ready_For_Execution to allow second record
        task = taskRepository.findById(task.getId()).orElseThrow();
        task.setTaskStatus(TaskStatus.Ready_For_Execution);
        taskRepository.save(task);

        // Second record
        recordResultService.recordResult(task.getId(), Map.of("status", "ok2"), null, ownerUser);

        List<TaskExecutionHistory> history = executionHistoryRepository
                .findByTaskIdOrderByAttemptNumberAsc(task.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getAttemptNumber()).isEqualTo(2);
    }

    // ─── Guard violations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("AUTO task → throws ConflictAppException")
    void recordResult_autoTask_throwsConflict() {
        Task task = helper.seedTask(request, TaskStatus.Ready_For_Execution);
        // Default seedTask creates AUTO task
        assertThat(task.getExecutionType()).isEqualTo(ExecutionType.AUTO);

        assertThatThrownBy(() ->
                recordResultService.recordResult(task.getId(), Map.of(), null, ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("not a MANUAL task");
    }

    @Test
    @DisplayName("wrong state (Pending) → throws ConflictAppException")
    void recordResult_wrongState_throwsConflict() {
        Task task = seedManualTask(TaskStatus.Pending);

        assertThatThrownBy(() ->
                recordResultService.recordResult(task.getId(), Map.of(), null, ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("Ready_For_Execution");
    }

    @Test
    @DisplayName("non-owner developer → throws ForbiddenAppException")
    void recordResult_nonOwner_throwsForbidden() {
        Task task = seedManualTask(TaskStatus.Ready_For_Execution);

        assertThatThrownBy(() ->
                recordResultService.recordResult(task.getId(), Map.of(), null, nonOwnerUser))
                .isInstanceOf(ForbiddenAppException.class);
    }

    @Test
    @DisplayName("unknown task ID → throws NotFoundAppException")
    void recordResult_unknownTask_throwsNotFound() {
        assertThatThrownBy(() ->
                recordResultService.recordResult("non-existent", Map.of(), null, ownerUser))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
}
