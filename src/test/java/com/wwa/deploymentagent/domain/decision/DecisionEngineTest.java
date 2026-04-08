package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.InvalidStateTransitionException;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DecisionEngine")
class DecisionEngineTest {

    @Autowired private DecisionEngine decisionEngine;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TestDataHelper helper;

    private ReleaseFlow releaseFlow;
    private Request request;
    private UserContext ownerUser;
    private UserContext adminUser;
    private UserContext devUser;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow);
        ownerUser = new UserContext("emp-001", "DEVELOPER");
        adminUser = new UserContext("emp-003", "DEVOPS_ADMIN");
        devUser = new UserContext("dev-user", "DEVELOPER");
    }

    // ─── approve ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("approve: Awaiting_Review → Approved for owner")
    void approve_awaitingReview_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        decisionEngine.applyDecision(task.getId(), DecisionType.approve, ownerUser, "Looks good");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Approved);
    }

    @Test
    @DisplayName("approve: throws ForbiddenAppException for non-owner user")
    void approve_nonOwner_throwsForbidden() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        assertThatThrownBy(() ->
                decisionEngine.applyDecision(task.getId(), DecisionType.approve, devUser, null))
                .isInstanceOf(ForbiddenAppException.class);
    }

    @Test
    @DisplayName("approve: throws InvalidStateTransitionException when not Awaiting_Review")
    void approve_wrongState_throwsInvalidTransition() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        assertThatThrownBy(() ->
                decisionEngine.applyDecision(task.getId(), DecisionType.approve, ownerUser, null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ─── reject ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reject: Awaiting_Review → Rejected for owner")
    void reject_awaitingReview_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        decisionEngine.applyDecision(task.getId(), DecisionType.reject, ownerUser, "Issues found");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Rejected);
    }

    @Test
    @DisplayName("reject: Awaiting_Review → Rejected for admin")
    void reject_awaitingReview_adminSucceeds() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        decisionEngine.applyDecision(task.getId(), DecisionType.reject, adminUser, "Admin override");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Rejected);
    }

    @Test
    @DisplayName("reject: throws ForbiddenAppException for non-owner user")
    void reject_nonOwner_throwsForbidden() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        assertThatThrownBy(() ->
                decisionEngine.applyDecision(task.getId(), DecisionType.reject, devUser, null))
                .isInstanceOf(ForbiddenAppException.class);
    }

    // ─── rerun ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rerun: Rejected → Ready_For_Execution and creates execution history")
    void rerun_rejected_createsExecutionHistory() {
        Task task = helper.seedTask(request, TaskStatus.Rejected);

        decisionEngine.applyDecision(task.getId(), DecisionType.rerun, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
        assertThat(updated.getLatestExecutionId()).isNotNull();
    }

    @Test
    @DisplayName("rerun: Failed → Ready_For_Execution and creates execution history")
    void rerun_failed_createsExecutionHistory() {
        Task task = helper.seedTask(request, TaskStatus.Failed);

        decisionEngine.applyDecision(task.getId(), DecisionType.rerun, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
    }

    @Test
    @DisplayName("rerun: throws InvalidStateTransitionException when task is Pending")
    void rerun_pendingTask_throwsInvalidTransition() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        assertThatThrownBy(() ->
                decisionEngine.applyDecision(task.getId(), DecisionType.rerun, ownerUser, null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ─── skip ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("skip: Pending → Skipped for owner")
    void skip_pending_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Skipped);
    }

    @Test
    @DisplayName("skip: Ready_For_Execution → Skipped for owner")
    void skip_readyForExecution_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Ready_For_Execution);

        decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Skipped);
    }

    @Test
    @DisplayName("skip: Awaiting_Review → Skipped for owner")
    void skip_awaitingReview_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Awaiting_Review);

        decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Skipped);
    }

    @Test
    @DisplayName("skip: throws InvalidStateTransitionException when Executing")
    void skip_executingState_throwsInvalidTransition() {
        Task task = helper.seedTask(request, TaskStatus.Executing);

        assertThatThrownBy(() ->
                decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
