export type CapabilityType = 'SKILL' | 'SCRIPT' | 'PIPELINE' | 'MANUAL'

export type IntegrationClientType = 'COPILOT' | 'OPENCODE' | 'KIRO' | 'MANUAL' | 'PIPELINE'

export type IntegrationExecutionStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type IntegrationReviewDecision = 'APPROVED' | 'REJECTED' | 'SKIPPED'

export type IntegrationTaskActions = {
  start: boolean
  review: boolean
  rerun: boolean
}

export type IntegrationCapability = {
  capabilityType: CapabilityType
  capabilityId: string
  capabilityVersion?: string
  skillId?: string
}

export type IntegrationProjectContext = {
  projectContextId?: string
  project: {
    projectId: string
    name?: string
  }
  team?: string
  agentModuleId?: string
  repository?: {
    repositoryId?: string
    provider?: string
  }
  branch?: string
  commit?: string
}

export type IntegrationRepository = {
  repositoryId?: string
  provider?: string
}

export type IntegrationActor = {
  userId: string
  displayName: string
}

export type IntegrationClient = {
  applicationId?: string
  clientType: IntegrationClientType
  clientVersion: string
}

export type IntegrationFailure = {
  code: string
  message: string
  retryable: boolean
}

export type IntegrationTask = {
  taskId: string
  workItemId: string
  agentModuleId: string
  title: string
  description?: string
  status: string
  assignee?: IntegrationActor
  capability: IntegrationCapability
  projectContext: IntegrationProjectContext
  approvedInputArtifactIds: string[]
  latestExecutionId?: string
  activeExecutionId?: string
  executionCount: number
  createdAt: string
  updatedAt: string
  actions: IntegrationTaskActions
}

export type IntegrationExecution = {
  executionId: string
  taskId: string
  attemptNumber: number
  status: IntegrationExecutionStatus
  user: IntegrationActor
  client: IntegrationClient
  capability: IntegrationCapability
  projectContext: IntegrationProjectContext
  startedAt: string
  completedAt?: string
  durationMs?: number
  artifactCount: number
  failureReason?: IntegrationFailure
  cancellationReason?: string
  pendingSync: boolean
  correlationId?: string
}

export type IntegrationArtifact = {
  artifactId: string
  workItemId?: string
  taskId: string
  executionId?: string
  role: string
  kind: string
  name: string
  mediaType: string
  sizeBytes: number
  digest: {
    algorithm: 'SHA-256'
    value: string
  }
  content: {
    mode: 'UPLOAD' | 'REFERENCE'
    referenceId?: string
  }
  sourcePath?: string
  createdAt: string
}

export type IntegrationReview = {
  reviewDecisionId: string
  executionId: string
  taskId: string
  decision: IntegrationReviewDecision
  reviewer: IntegrationActor
  comment?: string
  decidedAt: string
  correlationId?: string
}

export type CapabilityUsageVersion = {
  version?: string
  count: number
  percentage: number
}

export type CapabilityUsageRow = {
  capabilityType: CapabilityType
  capabilityId: string
  skillId?: string
  invocationCount: number
  successCount: number
  failureCount: number
  cancelledCount: number
  runningCount: number
  successRate: number
  failureRate: number
  averageDurationMs: number
  userCount: number
  versionDistribution: CapabilityUsageVersion[]
}

export type CapabilityUsageFilters = {
  capabilityId?: string
  skillId?: string
  team?: string
  projectId?: string
  agent?: string
  from?: string
  to?: string
  clientType?: IntegrationClientType
}

export type CapabilityUsage = {
  filters: CapabilityUsageFilters
  totals: {
    invocationCount: number
    distinctCapabilityCount: number
  }
  items: CapabilityUsageRow[]
}

export type TaskListFilters = {
  status?: string
  projectId?: string
  team?: string
  agentModuleId?: string
  limit?: number
  cursor?: string
}

export type IntegrationSuccess<T> = {
  success: true
  data: T
}

export type IntegrationPage<T> = {
  success: true
  data: T[]
  meta: {
    nextCursor?: string
    hasMore: boolean
  }
}
