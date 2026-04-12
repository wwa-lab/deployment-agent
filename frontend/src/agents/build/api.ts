import { buildClient } from './index'
import type {
  PaginatedResponse,
  ReleaseFlowDetail,
  ReleaseFlowListItem,
  Stage,
  Task,
  TaskExecutionHistory,
  TaskResult,
  UploadResponse,
} from '../../types'

export interface ListReleaseFlowsParams {
  project?: string
  status?: string
  stage?: string
  application?: string
  snowGroup?: string
  attemptView?: 'latest' | 'history'
  includeArchived?: boolean
  page?: number
  size?: number
}

export async function listReleaseFlows(
  params: ListReleaseFlowsParams = {},
): Promise<PaginatedResponse<ReleaseFlowListItem>> {
  const response = await buildClient.get('/release-flows', { params })
  return response.data
}

export async function getReleaseFlow(
  id: string,
  params?: { includeArchived?: boolean },
): Promise<ReleaseFlowDetail> {
  const response = await buildClient.get(`/release-flows/${id}`, { params })
  return response.data
}

export async function listTaskExecutions(taskId: string): Promise<TaskExecutionHistory[]> {
  const response = await buildClient.get(`/tasks/${taskId}/executions`)
  return response.data as TaskExecutionHistory[]
}

export async function recordResult(
  taskId: string,
  body: { resultSummary: Record<string, unknown>; resultLogs?: string },
): Promise<Task> {
  const response = await buildClient.post(`/tasks/${taskId}/record-result`, body)
  return response.data
}

export async function submitDecision(taskId: string, decision: string): Promise<Task> {
  const response = await buildClient.post(`/tasks/${taskId}/decision`, {
    decision: decision.toLowerCase(),
  })
  return response.data
}

export async function editTask(
  taskId: string,
  inputParameters: Record<string, unknown>,
): Promise<Task> {
  const response = await buildClient.put(`/tasks/${taskId}/input`, inputParameters)
  return response.data
}

export async function editNames(
  taskId: string,
  names: { taskName?: string; taskGroupName?: string },
): Promise<Task> {
  const response = await buildClient.put(`/tasks/${taskId}/names`, names)
  return response.data
}

export async function editExecutionType(
  taskId: string,
  executionType: 'MANUAL' | 'AUTO',
): Promise<Task> {
  const response = await buildClient.put(`/tasks/${taskId}/execution-type`, { executionType })
  return response.data
}

export async function cloneTask(taskId: string): Promise<Task> {
  const response = await buildClient.post(`/tasks/${taskId}/clone`)
  return response.data
}

export async function reorderTasks(requestId: string, taskIds: string[]): Promise<Task[]> {
  const response = await buildClient.put('/tasks/reorder', { requestId, taskIds })
  return response.data
}

export async function getTaskResult(taskId: string, executionId?: string): Promise<TaskResult> {
  const executions = await listTaskExecutions(taskId)
  const selectedExecution = executionId
    ? executions.find((execution) => execution.id === executionId)
    : executions[executions.length - 1]

  if (!selectedExecution) {
    throw new Error('No result available for this task yet.')
  }

  return {
    taskId: selectedExecution.taskId,
    executionId: selectedExecution.id,
    attemptNumber: selectedExecution.attemptNumber,
    status: selectedExecution.executionStatus,
    resultSummary: selectedExecution.resultSummary,
    resultLogs: selectedExecution.resultLogs,
    externalSystemType: selectedExecution.externalSystemType,
    externalExecutionId: selectedExecution.externalExecutionId,
    externalJobUrl: selectedExecution.externalJobUrl,
    submittedAt: selectedExecution.submittedAt,
    submissionStatus: selectedExecution.submissionStatus,
    submissionMessage: selectedExecution.submissionMessage,
  }
}

export async function submitAutoExecution(taskId: string): Promise<Task> {
  const response = await buildClient.post(`/tasks/${taskId}/submit-auto`)
  return response.data
}

export async function startManualExecution(taskId: string): Promise<Task> {
  const response = await buildClient.post(`/tasks/${taskId}/start-manual`)
  return response.data
}

export interface UploadOptions {
  releaseId?: string
  snowGroup?: string
  application?: string
  agent?: string
}

export async function uploadFile(
  file: File,
  stage: Stage,
  options: UploadOptions = {},
): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('stage', stage)
  if (options.releaseId) formData.append('releaseId', options.releaseId)
  if (options.snowGroup) formData.append('snowGroup', options.snowGroup)
  if (options.application) formData.append('application', options.application)
  if (options.agent) formData.append('agent', options.agent)

  const response = await buildClient.post('/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data
}

export async function downloadTemplate(): Promise<Blob> {
  const response = await buildClient.get('/upload/template', {
    responseType: 'blob',
  })
  return response.data
}
