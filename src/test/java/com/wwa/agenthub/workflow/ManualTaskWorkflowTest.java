package com.wwa.agenthub.workflow;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.decision.DecisionEngine;
import com.wwa.agenthub.domain.decision.DecisionType;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.RecordResultService;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T13.2 + T13.7 - Integration workflow tests for MANUAL task full lifecycle.
 *
 * Covers the complete decision chain from Pending through Approved/Rejected/Skipped,
 * and validates that AUTO tasks are guarded from manual result recording.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ManualTaskWorkflow")
class ManualTaskWorkflowTest {

    @Autowired private TaskService taskService;
    @Autowired private RecordResultService recordResultService;
    @Autowired private DecisionEngine decisionEngine;
    @Autowired private ReleaseFlowProgressionService progressionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TestDataHelper helper;

    private ReleaseFlow releaseFlow;
    private Request request;
    private UserContext ownerUser;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequestWithAgent(releaseFlow, AgentId.DEPLOYMENT_AGENT);
        ownerUser = new UserContext("emp-001", "DEVELOPER");
    }

    // ─── Full lifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("MANUAL task full lifecycle: Pending → Ready_For_Execution → Awaiting_Review → Approved")
    void manualTask_fullLifecycle_pendingToApproved() {
        // Seed a MANUAL task in Pending state
        Task task = seedManualTask(TaskStatus.Pending);

        // Step 1: Advance to Ready_For_Execution
        taskService.updateStatus(task.getId(), TaskStatus.Ready_For_Execution, ownerUser, "starting execution");
        Task afterReady = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(afterReady.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);

        // Step 2: Record result → transitions to Awaiting_Review
        Map<String, Object> resultSummary = Map.of("outcome", "passed", "duration", "120s");
        Task afterRecord = recordResultService.recordResult(
                task.getId(), resultSummary, "Execution log output", ownerUser);
        assertThat(afterRecord.getTaskStatus()).isEqualTo(TaskStatus.Awaiting_Review);
        assertThat(afterRecord.getCurrentResultSummary()).containsEntry("outcome", "passed");

        // Step 3: Apply approve decision
        decisionEngine.applyDecision(task.getId(), DecisionType.approve, ownerUser, "Looks good");
        progressionService.progressAfterDecision(task.getId());

        // Step 4: Assert final state is Approved
        Task finalState = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(finalState.getTaskStatus()).isEqualTo(TaskStatus.Approved);
    }

    // ─── Reject decision ──────────────────────────────────────────────────────

    @Test
    @DisplayName("reject decision on Awaiting_Review task sets status to Rejected")
    void manualTask_rejectDecision_setsRejectedStatus() {
        Task task = seedManualTask(TaskStatus.Awaiting_Review);

        decisionEngine.applyDecision(task.getId(), DecisionType.reject, ownerUser, "Issues found");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Rejected);
    }

    // ─── Skip decision ────────────────────────────────────────────────────────

    @Test
    @DisplayName("skip decision on Pending task sets status to Skipped")
    void manualTask_skipDecision_setsSkippedStatus() {
        Task task = seedManualTask(TaskStatus.Pending);

        decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, "skipping step");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Skipped);
    }

    // ─── AUTO task guard ──────────────────────────────────────────────────────

    @Test
    @DisplayName("recordResult on AUTO task in Ready_For_Execution throws ConflictAppException")
    void autoTask_recordResult_guardedByExecutionType() {
        // seedTask creates an AUTO task by default
        Task autoTask = helper.seedTask(request, TaskStatus.Ready_For_Execution);
        assertThat(autoTask.getExecutionType()).isEqualTo(ExecutionType.AUTO);

        assertThatThrownBy(() ->
                recordResultService.recordResult(autoTask.getId(), Map.of("status", "ok"), null, ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("not a MANUAL task");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Task seedManualTask(TaskStatus status) {
        Task task = new Task();
        task.setRequest(request);
        task.setTaskGroupId("TG-MANUAL");
        task.setTaskGroupName("Manual Deployment Group");
        task.setStepSeq(1);
        task.setTaskName("manual-deploy-step");
        task.setExecutionType(ExecutionType.MANUAL);
        task.setTaskStatus(status);
        task.setOwner("alice");
        return taskRepository.save(task);
    }
}
