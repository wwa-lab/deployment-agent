import apiClient from './client'
import type { AuditLogEntry, PaginatedResponse } from '../types'

export async function listAuditLogs(params: {
  page?: number
  size?: number
  operatorId?: string
  taskId?: string
} = {}): Promise<PaginatedResponse<AuditLogEntry>> {
  const response = await apiClient.get('/audit-logs', { params })
  return response.data
}
