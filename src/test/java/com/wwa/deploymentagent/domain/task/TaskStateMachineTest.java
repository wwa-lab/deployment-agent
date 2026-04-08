package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskStateMachine")
class TaskStateMachineTest {

    @Test void pendingToReadyForExecution_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Pending, TaskStatus.Ready_For_Execution)).isTrue();
    }

    @Test void pendingToSkipped_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Pending, TaskStatus.Skipped)).isTrue();
    }

    @Test void pendingToExecuting_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Pending, TaskStatus.Executing)).isFalse();
    }

    @Test void readyToExecuting_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Ready_For_Execution, TaskStatus.Executing)).isTrue();
    }

    @Test void readyToSkipped_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Ready_For_Execution, TaskStatus.Skipped)).isTrue();
    }

    @Test void readyToApproved_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Ready_For_Execution, TaskStatus.Approved)).isFalse();
    }

    @Test void executingToAwaitingReview_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Executing, TaskStatus.Awaiting_Review)).isTrue();
    }

    @Test void executingToFailed_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Executing, TaskStatus.Failed)).isTrue();
    }

    @Test void executingToApproved_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Executing, TaskStatus.Approved)).isFalse();
    }

    @Test void awaitingReviewToApproved_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Awaiting_Review, TaskStatus.Approved)).isTrue();
    }

    @Test void awaitingReviewToRejected_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Awaiting_Review, TaskStatus.Rejected)).isTrue();
    }

    @Test void awaitingReviewToSkipped_allowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Awaiting_Review, TaskStatus.Skipped)).isTrue();
    }

    @Test void awaitingReviewToExecuting_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Awaiting_Review, TaskStatus.Executing)).isFalse();
    }

    @Test void approvedIsTerminal() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Approved, TaskStatus.Pending)).isFalse();
        assertThat(TaskStateMachine.isValid(TaskStatus.Approved, TaskStatus.Ready_For_Execution)).isFalse();
    }

    @Test void rejectedToReadyForExecution_allowed_rerun() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Rejected, TaskStatus.Ready_For_Execution)).isTrue();
    }

    @Test void rejectedToApproved_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Rejected, TaskStatus.Approved)).isFalse();
    }

    @Test void skippedIsTerminal() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Skipped, TaskStatus.Pending)).isFalse();
        assertThat(TaskStateMachine.isValid(TaskStatus.Skipped, TaskStatus.Ready_For_Execution)).isFalse();
    }

    @Test void failedToReadyForExecution_allowed_rerun() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Failed, TaskStatus.Ready_For_Execution)).isTrue();
    }

    @Test void failedToApproved_disallowed() {
        assertThat(TaskStateMachine.isValid(TaskStatus.Failed, TaskStatus.Approved)).isFalse();
    }
}
