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
export type AccessGrantStatus = 'ACTIVE' | 'SUSPENDED'
export type UserPermission =
  | 'release.view'
  | 'release.upload'
  | 'release.view_archived'
  | 'release.rundown.edit'
  | 'release.rundown.archive'
  | 'release.rundown.restore'
  | 'release.rundown.purge'
  | 'release.rundown.start'
  | 'release.rundown.fail'
  | 'task.edit'
  | 'task.run'
  | 'task.review'
  | 'config.manage'
  | 'audit.view'
  | 'access.manage'
export type ConfigKey =
  | 'jenkins_url'
  | 'jenkins_user'
  | 'jenkins_api_token'
  | 'ansible_url'
  | 'ansible_user'
  | 'ansible_api_token'
  | 'execution_callback_endpoint'
export type ConfigIntegrationId = 'jenkins' | 'ansible' | 'callback'

// Task
export interface Task {
  id: string
  category?: string
  dependencies?: string
  taskGroupId: string
  taskGroupName: string
  stepSeq: number
  taskName: string
  executionType: ExecutionType
  critical: boolean
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
  inputSnapshot?: Record<string, unknown>
  resultSummary?: Record<string, unknown>
  resultLogs?: string
  startTime?: string
  endTime?: string
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
  archivedAt?: string
  archivedBy?: string
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
  normalizedReleaseId?: string
  currentStage: Stage
  flowStatus: FlowStatus
  reviewStatus: ReviewStatus
  archivedAt?: string
  archivedBy?: string
  sitStatus: RequestStatus
  uatStatus: RequestStatus
  prodStatus: RequestStatus
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
  archivedAt?: string
  archivedBy?: string
  requests: Request[]
}

// ConfigItem
export interface ConfigItem {
  key: ConfigKey
  value: string
  description?: string
  updatedBy?: string
  updatedAt?: string
}

export interface ConfigComponentRow {
  id: ConfigIntegrationId
  label: string
  category: string
  endpointKey?: ConfigKey
  userKey?: ConfigKey
  secretKey?: ConfigKey
  endpoint: string
  serviceUser?: string
  secretValue?: string
  secretState: 'Configured' | 'Missing' | 'Not required'
  description?: string
  updatedBy?: string
  updatedAt?: string
  status: 'Ready' | 'Partial' | 'Needs Setup'
}

export interface ConfigComponentDraft {
  endpoint: string
  serviceUser?: string
  secretValue?: string
  description?: string
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

export interface AccessGrant {
  employeeId: string
  displayName: string
  grantStatus: AccessGrantStatus
  assignedRoles: UserRole[]
  note?: string
  lastLoginAt?: string
  createdBy?: string
  createdAt?: string
  updatedBy?: string
  updatedAt?: string
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

export interface RequestArchiveResult {
  releaseFlowId: string
  requestId: string
  stage: Stage
  requestArchived: boolean
  releaseFlowArchived: boolean
  activeRequestCount: number
}

export interface RequestPurgeResult {
  releaseFlowId: string
  requestId: string
  stage: Stage
  releaseFlowDeleted: boolean
  remainingRequestCount: number
  activeRequestCount: number
}

// Template management
export interface TemplateTask {
  id: string
  category: string
  taskName: string
  step: number
  stepName: string
  type: ExecutionType
  critical: boolean
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
  critical: boolean
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
  role?: UserRole
  roles: UserRole[]
  permissions: UserPermission[]
  displayName: string
}
