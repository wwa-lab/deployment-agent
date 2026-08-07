import axios from 'axios'
import router from '../router'
import { installCorrelationIdInterceptor } from './correlationId'
import type {
  CapabilityUsage,
  CapabilityUsageFilters,
  IntegrationArtifact,
  IntegrationExecution,
  IntegrationPage,
  IntegrationReview,
  IntegrationReviewDecision,
  IntegrationSuccess,
  IntegrationTask,
  TaskListFilters,
} from '../platform/integration/types'

type IntegrationErrorEnvelope = {
  success: false
  error?: {
    code?: string
    message?: string
    retryable?: boolean
    requestId?: string
  }
}

export class AtlasApiError extends Error {
  readonly code: string
  readonly retryable: boolean
  readonly requestId?: string
  readonly status?: number

  constructor(
    message: string,
    code = 'INTEGRATION_REQUEST_FAILED',
    retryable = false,
    requestId?: string,
    status?: number,
  ) {
    super(message)
    this.name = 'AtlasApiError'
    this.code = code
    this.retryable = retryable
    this.requestId = requestId
    this.status = status
  }
}

const atlasIntegrationClient = axios.create({
  baseURL: '/api/v1/integration',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

installCorrelationIdInterceptor(atlasIntegrationClient)

atlasIntegrationClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      void router.push('/login')
    }

    const envelope = error.response?.data as IntegrationErrorEnvelope | undefined
    const serverError = envelope?.error
    return Promise.reject(new AtlasApiError(
      serverError?.message || error.message || 'Atlas integration request failed',
      serverError?.code,
      serverError?.retryable,
      serverError?.requestId,
      error.response?.status,
    ))
  },
)

export async function listIntegrationTasks(filters: TaskListFilters = {}): Promise<IntegrationPage<IntegrationTask>> {
  const response = await atlasIntegrationClient.get<IntegrationPage<IntegrationTask>>('/tasks', {
    params: compactParams(filters),
  })
  return response.data
}

export async function listAllIntegrationTasks(filters: TaskListFilters = {}): Promise<IntegrationTask[]> {
  return collectPages((cursor) => listIntegrationTasks({ ...filters, limit: 100, cursor }))
}

export async function getIntegrationTask(taskId: string): Promise<IntegrationTask> {
  const response = await atlasIntegrationClient.get<IntegrationSuccess<IntegrationTask>>(
    `/tasks/${encodeURIComponent(taskId)}`,
  )
  return response.data.data
}

export async function listExecutions(taskId: string): Promise<IntegrationExecution[]> {
  return collectPages(async (cursor) => {
    const response = await atlasIntegrationClient.get<IntegrationPage<IntegrationExecution>>(
      `/tasks/${encodeURIComponent(taskId)}/executions`,
      { params: compactParams({ limit: 200, cursor }) },
    )
    return response.data
  })
}

export async function listArtifacts(executionId: string): Promise<IntegrationArtifact[]> {
  const response = await atlasIntegrationClient.get<IntegrationSuccess<IntegrationArtifact[]>>(
    `/executions/${encodeURIComponent(executionId)}/artifacts`,
  )
  return response.data.data
}

export async function listApprovedInputArtifacts(taskId: string): Promise<IntegrationArtifact[]> {
  return collectPages(async (cursor) => {
    const response = await atlasIntegrationClient.get<IntegrationPage<IntegrationArtifact>>(
      `/tasks/${encodeURIComponent(taskId)}/approved-input-artifacts`,
      { params: compactParams({ limit: 100, cursor }) },
    )
    return response.data
  })
}

export async function rerunIntegrationTask(
  taskId: string,
  executionId: string,
): Promise<IntegrationTask> {
  const response = await atlasIntegrationClient.post<IntegrationSuccess<IntegrationTask>>(
    `/tasks/${encodeURIComponent(taskId)}/rerun`,
    { executionId },
    { headers: { 'Idempotency-Key': createIdempotencyKey() } },
  )
  return response.data.data
}

export async function getReviewDecision(executionId: string): Promise<IntegrationReview> {
  const response = await atlasIntegrationClient.get<IntegrationSuccess<IntegrationReview>>(
    `/executions/${encodeURIComponent(executionId)}/review-decision`,
  )
  return response.data.data
}

export async function submitReviewDecision(
  executionId: string,
  decision: IntegrationReviewDecision,
  comment: string,
): Promise<IntegrationReview> {
  const response = await atlasIntegrationClient.post<IntegrationSuccess<IntegrationReview>>(
    `/executions/${encodeURIComponent(executionId)}/review-decision`,
    { decision, comment: comment.trim() || undefined },
    { headers: { 'Idempotency-Key': createIdempotencyKey() } },
  )
  return response.data.data
}

export async function getCapabilityUsage(filters: CapabilityUsageFilters = {}): Promise<CapabilityUsage> {
  const response = await atlasIntegrationClient.get<IntegrationSuccess<CapabilityUsage>>(
    '/telemetry/capability-usage',
    { params: compactParams(filters) },
  )
  return response.data.data
}

export async function downloadArtifactContent(executionId: string, artifactId: string): Promise<Blob> {
  const response = await atlasIntegrationClient.get<Blob>(
    `/executions/${encodeURIComponent(executionId)}/artifacts/${encodeURIComponent(artifactId)}/content`,
    { responseType: 'blob' },
  )
  return response.data
}

export function createIdempotencyKey(): string {
  const randomPart = globalThis.crypto?.randomUUID?.()
    ?? Math.random().toString(36).slice(2).padEnd(16, '0')
  return `atlas-web-${Date.now().toString(36)}-${randomPart}`
}

function compactParams<T extends object>(values: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(values).filter(([, value]) => value !== undefined && value !== ''),
  ) as Partial<T>
}

async function collectPages<T>(
  load: (cursor?: string) => Promise<IntegrationPage<T>>,
): Promise<T[]> {
  const values: T[] = []
  const seen = new Set<string>()
  let cursor: string | undefined
  do {
    const page = await load(cursor)
    values.push(...page.data)
    if (!page.meta.hasMore || !page.meta.nextCursor) return values
    if (seen.has(page.meta.nextCursor)) {
      throw new AtlasApiError('Atlas returned a repeated pagination cursor.', 'INVALID_PAGE_CURSOR')
    }
    seen.add(page.meta.nextCursor)
    cursor = page.meta.nextCursor
  } while (seen.size <= 100)
  throw new AtlasApiError('Atlas pagination exceeded the supported result window.', 'PAGE_WINDOW_EXCEEDED')
}

export default atlasIntegrationClient
