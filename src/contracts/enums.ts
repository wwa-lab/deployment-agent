/** Execution type for a task – determines automated vs manual execution path. */
export type ExecutionType = "MANUAL" | "AUTO";

/** Deployment stages in order. */
export const STAGES = ["SIT", "UAT", "PROD"] as const;
export type Stage = (typeof STAGES)[number];

/** Internal Release Flow lifecycle status. */
export type FlowStatus =
  | "Pending"
  | "Running"
  | "Completed"
  | "Failed"
  | "Rejected";

/** Review gate status on a Release Flow. */
export type ReviewStatus = "Pending_Review" | "Approved" | "Rejected";

/** Per-stage Request lifecycle status. */
export type RequestStatus =
  | "Pending"
  | "Running"
  | "Completed"
  | "Failed"
  | "Skipped"
  | "Rejected";

/** Task lifecycle status – matches frozen design states. */
export type TaskStatus =
  | "Pending"
  | "Ready_For_Execution"
  | "Executing"
  | "Awaiting_Review"
  | "Approved"
  | "Rejected"
  | "Skipped"
  | "Failed";

/** TaskExecutionHistory execution outcome status. */
export type ExecutionStatus =
  | "Running"
  | "Completed"
  | "Failed"
  | "Timed_Out";

/** Summary status used for display purposes only (Done/Running/Pending). */
export type SummaryStatus = "Done" | "Running" | "Pending";

/** Audit action types – append-only registry. */
export type AuditActionType =
  | "upload"
  | "edit"
  | "view_result"
  | "approve"
  | "reject"
  | "rerun"
  | "skip"
  | "config_update";

/** RBAC roles aligned with frozen design decisions. */
export const ROLES = [
  "DEVELOPER",
  "TL",
  "DEVOPS_ADMIN",
  "AUDIT",
  "MANAGEMENT",
] as const;
export type Role = (typeof ROLES)[number];

/** Known configuration keys. */
export const CONFIG_KEYS = [
  "jenkins_url",
  "ansible_url",
  "execution_callback_endpoint",
] as const;
export type ConfigKey = (typeof CONFIG_KEYS)[number];
