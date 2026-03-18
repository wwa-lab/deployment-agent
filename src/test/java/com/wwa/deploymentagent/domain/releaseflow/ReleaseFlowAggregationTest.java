package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReleaseFlowAggregation")
class ReleaseFlowAggregationTest {

    // ─── aggregateTasksToRequestStatus ───────────────────────────────────────

    @Test void emptyTasks_returnsPending() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(List.of()))
                .isEqualTo(RequestStatus.Pending);
    }

    @Test void allApprovedOrSkipped_returnsCompleted() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Approved, TaskStatus.Skipped)))
                .isEqualTo(RequestStatus.Completed);
    }

    @Test void anyRejected_returnsRejected() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Approved, TaskStatus.Rejected)))
                .isEqualTo(RequestStatus.Rejected);
    }

    @Test void anyFailed_returnsFailed() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Approved, TaskStatus.Failed)))
                .isEqualTo(RequestStatus.Failed);
    }

    @Test void anyExecuting_returnsRunning() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Pending, TaskStatus.Executing)))
                .isEqualTo(RequestStatus.Running);
    }

    @Test void anyAwaitingReview_returnsRunning() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Approved, TaskStatus.Awaiting_Review)))
                .isEqualTo(RequestStatus.Running);
    }

    @Test void anyReadyForExecution_returnsRunning() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Pending, TaskStatus.Ready_For_Execution)))
                .isEqualTo(RequestStatus.Running);
    }

    @Test void allPending_returnsPending() {
        assertThat(ReleaseFlowAggregation.aggregateTasksToRequestStatus(
                List.of(TaskStatus.Pending, TaskStatus.Pending)))
                .isEqualTo(RequestStatus.Pending);
    }

    // ─── aggregateStagesToFlowStatus ─────────────────────────────────────────

    @Test void emptyStages_returnsFlowPending() {
        assertThat(ReleaseFlowAggregation.aggregateStagesToFlowStatus(List.of()))
                .isEqualTo(FlowStatus.Pending);
    }

    @Test void allStagesCompleted_returnsFlowCompleted() {
        assertThat(ReleaseFlowAggregation.aggregateStagesToFlowStatus(
                List.of(RequestStatus.Completed)))
                .isEqualTo(FlowStatus.Completed);
    }

    @Test void anyStageRejected_returnsFlowRejected() {
        assertThat(ReleaseFlowAggregation.aggregateStagesToFlowStatus(
                List.of(RequestStatus.Completed, RequestStatus.Rejected)))
                .isEqualTo(FlowStatus.Rejected);
    }

    @Test void anyStageRunning_returnsFlowRunning() {
        assertThat(ReleaseFlowAggregation.aggregateStagesToFlowStatus(
                List.of(RequestStatus.Running)))
                .isEqualTo(FlowStatus.Running);
    }

    // ─── toSummaryStatus ─────────────────────────────────────────────────────

    @Test void completedMapsToSummaryDone() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(FlowStatus.Completed)).isEqualTo(SummaryStatus.Done);
    }

    @Test void rejectedMapsToSummaryDone() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(FlowStatus.Rejected)).isEqualTo(SummaryStatus.Done);
    }

    @Test void runningMapsToSummaryRunning() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(FlowStatus.Running)).isEqualTo(SummaryStatus.Running);
    }

    @Test void pendingMapsToSummaryPending() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(FlowStatus.Pending)).isEqualTo(SummaryStatus.Pending);
    }

    @Test void approvedTaskMapsToSummaryDone() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(TaskStatus.Approved)).isEqualTo(SummaryStatus.Done);
    }

    @Test void awaitingReviewMapsToSummaryRunning() {
        assertThat(ReleaseFlowAggregation.toSummaryStatus(TaskStatus.Awaiting_Review)).isEqualTo(SummaryStatus.Running);
    }
}
