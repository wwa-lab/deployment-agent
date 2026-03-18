package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.SummaryStatus;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;

import java.util.List;

/**
 * Pure static aggregation functions for deriving parent statuses from child statuses.
 * No I/O, no dependencies – all methods are deterministic pure functions.
 */
public final class ReleaseFlowAggregation {

    private ReleaseFlowAggregation() {}

    /**
     * Aggregates task statuses within a Request to a Request summary status.
     *
     * <p>Rules (evaluated in priority order):
     * <ol>
     *   <li>Empty → Pending</li>
     *   <li>All Approved or Skipped → Completed</li>
     *   <li>Any Rejected → Rejected</li>
     *   <li>Any Failed → Failed</li>
     *   <li>Any Executing, Awaiting_Review, or Ready_For_Execution → Running</li>
     *   <li>Otherwise → Pending</li>
     * </ol>
     */
    public static RequestStatus aggregateTasksToRequestStatus(List<TaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return RequestStatus.Pending;

        if (statuses.stream().allMatch(s -> s == TaskStatus.Approved || s == TaskStatus.Skipped)) {
            return RequestStatus.Completed;
        }
        if (statuses.stream().anyMatch(s -> s == TaskStatus.Rejected)) return RequestStatus.Rejected;
        if (statuses.stream().anyMatch(s -> s == TaskStatus.Failed)) return RequestStatus.Failed;
        if (statuses.stream().anyMatch(s ->
                s == TaskStatus.Executing
                || s == TaskStatus.Awaiting_Review
                || s == TaskStatus.Ready_For_Execution)) {
            return RequestStatus.Running;
        }
        return RequestStatus.Pending;
    }

    /**
     * Aggregates Request statuses within a stage to a stage-level summary.
     */
    public static RequestStatus aggregateRequestsToStageStatus(List<RequestStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return RequestStatus.Pending;

        if (statuses.stream().anyMatch(s -> s == RequestStatus.Running)) return RequestStatus.Running;
        if (statuses.stream().allMatch(s -> s == RequestStatus.Completed)) return RequestStatus.Completed;
        if (statuses.stream().anyMatch(s -> s == RequestStatus.Rejected)) return RequestStatus.Rejected;
        if (statuses.stream().anyMatch(s -> s == RequestStatus.Failed)) return RequestStatus.Failed;
        return RequestStatus.Pending;
    }

    /**
     * Derives the overall Release Flow status from aggregated stage statuses.
     *
     * <p>Only stages that have at least one Request should be included in the input;
     * stages with no Requests must not be included as Pending placeholders.
     */
    public static FlowStatus aggregateStagesToFlowStatus(List<RequestStatus> stageStatuses) {
        if (stageStatuses == null || stageStatuses.isEmpty()) return FlowStatus.Pending;
        if (stageStatuses.stream().anyMatch(s -> s == RequestStatus.Rejected)) return FlowStatus.Rejected;
        if (stageStatuses.stream().anyMatch(s -> s == RequestStatus.Failed)) return FlowStatus.Failed;
        if (stageStatuses.stream().allMatch(s -> s == RequestStatus.Completed)) return FlowStatus.Completed;
        if (stageStatuses.stream().anyMatch(s -> s == RequestStatus.Running)) return FlowStatus.Running;
        return FlowStatus.Pending;
    }

    /**
     * Maps internal FlowStatus / RequestStatus / TaskStatus to the three-value summary display status.
     * Summary display uses only Done | Running | Pending.
     */
    public static SummaryStatus toSummaryStatus(Object status) {
        if (status == FlowStatus.Completed || status == RequestStatus.Completed
                || status == TaskStatus.Approved || status == TaskStatus.Skipped
                || status == FlowStatus.Rejected || status == FlowStatus.Failed
                || status == RequestStatus.Rejected || status == RequestStatus.Failed
                || status == TaskStatus.Rejected || status == TaskStatus.Failed) {
            return SummaryStatus.Done;
        }
        if (status == FlowStatus.Running || status == RequestStatus.Running
                || status == TaskStatus.Executing || status == TaskStatus.Awaiting_Review
                || status == TaskStatus.Ready_For_Execution) {
            return SummaryStatus.Running;
        }
        return SummaryStatus.Pending;
    }
}
