// Enums
export type FlowStatus = 'Pending' | 'Running' | 'Completed' | 'Failed' | 'Rejected'
export type ReviewStatus = 'Pending_Review' | 'Approved' | 'Rejected'
export type Stage = 'SIT' | 'UAT' | 'PROD'
export type TaskStatus =
  | 'Pending'
  | 'Ready_For_Execution'
  | 'Executing'
  | 'Awaiting_Review'
  | 'Approved'
  | 'Rejected'
  | 'Skipped'
  | 'Failed'
export type ExecutionType = 'MANUAL' | 'AUTO'
export type RequestStatus = 'Pending' | 'Running' | 'Completed' | 'Failed' | 'Rejected'
export type UserRole = 'DEVELOPER' | 'TL' | 'DEVOPS_ADMIN' | 'AUDIT_MGMT'

// Task
export interface Task {
  id: string
  taskGroupId: string
  taskGroupName: string
  stepSeq: number
  taskName: string
  executionType: ExecutionType
  taskStatus: TaskStatus
  inputParameters: { script?: string; parameters?: string }
  expectedOutput?: string
  owner?: string
  plannedStartTime?: string
  plannedEndTime?: string
  currentResultSummary?: Record<string, unknown>
  latestExecutionId?: string
  startTime?: string
  endTime?: string
  lastUpdatedAt?: string
}

// Request
export interface Request {
  id: string
  stage: Stage
  requestStatus: RequestStatus
  tasks: Task[]
}

// ReleaseFlow (list item)
export interface ReleaseFlowListItem {
  id: string
  projectId: string
  projectName: string
  releaseId: string
  currentStage: Stage
  flowStatus: FlowStatus
  reviewStatus: ReviewStatus
}

// ReleaseFlow (detail)
export interface ReleaseFlowDetail {
  id: string
  projectId: string
  projectName: string
  releaseId: string
  normalizedReleaseId: string
  currentStage: Stage
  flowStatus: FlowStatus
  reviewStatus: ReviewStatus
  requests: Request[]
}

// ConfigItem
export interface ConfigItem {
  key: string
  value: string
  description?: string
  updatedBy?: string
  updatedAt?: string
}

// AuditLogEntry
export interface AuditLogEntry {
  id: string
  operatorId: string
  operatorRole: string
  actionType: string
  timestamp: string
  releaseFlowId?: string
  requestId?: string
  taskId?: string
  contextPayload?: Record<string, unknown>
}

// TaskResult
export interface TaskResult {
  taskId: string
  executionId: string
  attemptNumber: number
  status: string
  resultSummary?: Record<string, unknown>
  resultLogs?: string
}

// Paginated
export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  size: number
}

// Upload response
export interface UploadResponse {
  releaseFlowId: string
  releaseId: string
  stage: Stage
  taskCount: number
}
