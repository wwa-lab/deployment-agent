import apiClient from './client'
import type { AuditLogEntry, PaginatedResponse } from '../types'

export async function listAuditLogs(params: {
  page?: number
  size?: number
} = {}): Promise<PaginatedResponse<AuditLogEntry>> {
  const response = await apiClient.get('/audit-logs', { params })
  return response.data
}
