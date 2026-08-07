package com.wwa.agenthub.domain.decision;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.InvalidStateTransitionException;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DecisionEngine")
class DecisionEngineTest {

    @Autowired private DecisionEngine decisionEngine;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskExecutionHistoryRepository executionHistoryRepository;
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
        ownerUser = scopedUser("emp-001");
        adminUser = new UserContext("emp-003", "DEVOPS_ADMIN");
        devUser = scopedUser("dev-user");
    }

    private static UserContext scopedUser(String userId) {
        return new UserContext(
                userId,
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                userId,
                List.of(new AccessScope("*", "*")));
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
    @DisplayName("rerun: Rejected → Ready_For_Execution without creating a ghost execution")
    void rerun_rejected_doesNotCreateExecutionHistory() {
        Task task = helper.seedTask(request, TaskStatus.Rejected);
        long historyBefore = executionHistoryRepository.count();

        decisionEngine.applyDecision(task.getId(), DecisionType.rerun, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
        assertThat(updated.getLatestExecutionId()).isNull();
        assertThat(executionHistoryRepository.count()).isEqualTo(historyBefore);
    }

    @Test
    @DisplayName("rerun: Failed → Ready_For_Execution without creating a ghost execution")
    void rerun_failed_doesNotCreateExecutionHistory() {
        Task task = helper.seedTask(request, TaskStatus.Failed);
        long historyBefore = executionHistoryRepository.count();

        decisionEngine.applyDecision(task.getId(), DecisionType.rerun, ownerUser, null);

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
        assertThat(executionHistoryRepository.count()).isEqualTo(historyBefore);
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

    // ─── idempotency (P1-1) ─────────────────────────────────────────────────
    //
    // When a decision would move the task to a state it is already in, the
    // decision is a successful no-op: the status does not change, no state
    // transition exception is thrown, and no duplicate audit entry is
    // written. This prevents double-click / retry scenarios from producing
    // confusing 422 responses. Rerun is intentionally excluded because it
    // must always create a new execution history attempt.

    @Test
    @DisplayName("approve: already Approved is an idempotent no-op")
    void approve_alreadyApproved_isNoOp() {
        Task task = helper.seedTask(request, TaskStatus.Approved);

        decisionEngine.applyDecision(task.getId(), DecisionType.approve, ownerUser, "retry");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Approved);
    }

    @Test
    @DisplayName("reject: already Rejected is an idempotent no-op")
    void reject_alreadyRejected_isNoOp() {
        Task task = helper.seedTask(request, TaskStatus.Rejected);

        decisionEngine.applyDecision(task.getId(), DecisionType.reject, ownerUser, "retry");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Rejected);
    }

    @Test
    @DisplayName("skip: already Skipped is an idempotent no-op")
    void skip_alreadySkipped_isNoOp() {
        Task task = helper.seedTask(request, TaskStatus.Skipped);

        decisionEngine.applyDecision(task.getId(), DecisionType.skip, ownerUser, "retry");

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Skipped);
    }
}
