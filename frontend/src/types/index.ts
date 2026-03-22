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
export type UserRole = 'DEVELOPER' | 'TL' | 'DEVOPS_ADMIN' | 'AUDIT' | 'MANAGEMENT'

// Task
export interface Task {
  id: string
  category?: string
  taskGroupId: string
  taskGroupName: string
  stepSeq: number
  taskName: string
  executionType: ExecutionType
  taskStatus: TaskStatus
  inputParameters: { script?: string; parameters?: string; system?: string }
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

export interface TaskExecutionHistory {
  id: string
  taskId: string
  attemptNumber: number
  executionStatus: string
  resultSummary?: Record<string, unknown>
  resultLogs?: string
  externalSystemType?: string
  externalExecutionId?: string
  externalJobUrl?: string
  submittedAt?: string
  submissionStatus?: string
  submissionMessage?: string
}

// Request
export interface Request {
  id: string
  releaseFlowId: string
  stage: Stage
  requestStatus: RequestStatus
  snowGroup?: string
  application?: string
  site?: string
  createdBy?: string
  estimatedRemainingMinutes?: number
  createdAt?: string
  updatedAt?: string
  version: number
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
  externalSystemType?: string
  externalExecutionId?: string
  externalJobUrl?: string
  submittedAt?: string
  submissionStatus?: string
  submissionMessage?: string
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

// Template management
export interface TemplateTask {
  id: string
  category: string
  taskName: string
  step: number
  stepName: string
  type: ExecutionType
  owner: string
  estDuration: string
  dependencies?: string
}

export interface TemplateTaskDraft {
  category: string
  taskName: string
  step: number
  stepName: string
  type: ExecutionType
  owner: string
  estDurationMinutes: number
  dependencies?: string
}

export interface TemplateRecord {
  id: string
  name: string
  version: string
  agent: string
  category: string
  snowGroup: string
  application: string
  site: string
  estDuration: string
  description: string
  createdBy: string
  createdAt: string
  updatedAt: string
  tasks: TemplateTask[]
}

export interface CreateTemplateDraft {
  name: string
  version: string
  agent: string
  category: string
  snowGroup: string
  application: string
  site: string
  estDurationMinutes: number
  description: string
  source: 'manual' | 'upload'
  sourceFileName?: string
}

// Auth
export interface AuthResponse {
  userId: string
  role: UserRole
  displayName: string
}
