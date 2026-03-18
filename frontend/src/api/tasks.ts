import apiClient from './client'
import type { Task, TaskResult } from '../types'

export async function recordResult(
  taskId: string,
  body: { resultSummary: string; resultLogs?: string }
): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/record-result`, body)
  return response.data
}

export async function submitDecision(taskId: string, decision: string): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/decision`, { decision })
  return response.data
}

export async function editTask(
  taskId: string,
  inputParameters: Record<string, unknown>
): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/edit`, { inputParameters })
  return response.data
}

export async function getTaskResult(
  taskId: string,
  executionId?: string
): Promise<TaskResult> {
  const params = executionId ? { executionId } : {}
  const response = await apiClient.get(`/tasks/${taskId}/result`, { params })
  return response.data
}

export async function submitAutoExecution(taskId: string): Promise<Task> {
  const response = await apiClient.post(`/tasks/${taskId}/submit-auto`)
  return response.data
}
