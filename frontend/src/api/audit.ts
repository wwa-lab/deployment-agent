import platformClient from './platformClient'
import type { AuditLogEntry, PaginatedResponse } from '../types'

export async function listAuditLogs(params: {
  page?: number
  size?: number
  operatorId?: string
  taskId?: string
  application?: string
  snowGroup?: string
  agent?: string
} = {}): Promise<PaginatedResponse<AuditLogEntry>> {
  const response = await platformClient.get('/audit-logs', { params })
  return response.data
}
