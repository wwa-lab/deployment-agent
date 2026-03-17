import {
  FlowStatus,
  RequestStatus,
  SummaryStatus,
  TaskStatus,
} from "../../contracts/enums";

/**
 * Aggregates task statuses within a Request to a Request summary status.
 * Pure function – no side effects, no I/O.
 */
export function aggregateTasksToRequestStatus(
  taskStatuses: TaskStatus[]
): RequestStatus {
  if (taskStatuses.length === 0) return "Pending";

  if (taskStatuses.some((s) => s === "Executing")) return "Running";
  if (
    taskStatuses.every(
      (s) => s === "Approved" || s === "Skipped"
    )
  )
    return "Completed";
  if (taskStatuses.some((s) => s === "Rejected")) return "Rejected";
  if (taskStatuses.some((s) => s === "Failed")) return "Failed";

  return "Pending";
}

/**
 * Aggregates Request statuses within a stage to a stage-level summary.
 * Pure function.
 */
export function aggregateRequestsToStageStatus(
  requestStatuses: RequestStatus[]
): RequestStatus {
  if (requestStatuses.length === 0) return "Pending";

  if (requestStatuses.some((s) => s === "Running")) return "Running";
  if (requestStatuses.every((s) => s === "Completed")) return "Completed";
  if (requestStatuses.some((s) => s === "Rejected")) return "Rejected";
  if (requestStatuses.some((s) => s === "Failed")) return "Failed";

  return "Pending";
}

/**
 * Maps internal FlowStatus / RequestStatus to the three-value summary display status.
 * Locked design decision: summary display uses only Done | Running | Pending.
 */
export function toSummaryStatus(
  status: FlowStatus | RequestStatus | TaskStatus
): SummaryStatus {
  if (status === "Completed" || status === "Approved" || status === "Skipped") {
    return "Done";
  }
  if (
    status === "Running" ||
    status === "Executing" ||
    status === "Awaiting_Review" ||
    status === "Ready_For_Execution"
  ) {
    return "Running";
  }
  return "Pending";
}

/**
 * Derives the overall Release Flow status from aggregated stage statuses.
 * Pure function.
 */
export function aggregateStagesToFlowStatus(
  stageStatuses: RequestStatus[]
): FlowStatus {
  if (stageStatuses.length === 0) return "Pending";
  if (stageStatuses.some((s) => s === "Rejected")) return "Rejected";
  if (stageStatuses.some((s) => s === "Failed")) return "Failed";
  if (stageStatuses.every((s) => s === "Completed")) return "Completed";
  if (stageStatuses.some((s) => s === "Running")) return "Running";
  return "Pending";
}
