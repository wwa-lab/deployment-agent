import apiClient from './client'
import type { Task, TaskExecutionHistory, TaskResult } from '../types'

export async function listTaskExecutions(taskId: string): Promise<TaskExecutionHistory[]> {
  const response = await apiClient.get(`/tasks/${taskId}/executions`)
  return response.data as TaskExecutionHistory[]
}

export async function recordResult(
  taskId: string,
  body: { resultSummary: Record<string, unknown>; resultLogs?: string }
): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/record-result`, body)
  return response.data
}

export async function submitDecision(taskId: string, decision: string): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/decision`, { decision: decision.toLowerCase() })
  return response.data
}

export async function editTask(
  taskId: string,
  inputParameters: Record<string, unknown>
): Promise<Task> {
  const response = await apiClient.put(`/tasks/${taskId}/input`, inputParameters)
  return response.data
}

export async function getTaskResult(
  taskId: string,
  executionId?: string
): Promise<TaskResult> {
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
  const response = await apiClient.post(`/tasks/${taskId}/submit-auto`)
  return response.data
}

export async function startManualExecution(taskId: string): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/start-manual`)
  return response.data
}
