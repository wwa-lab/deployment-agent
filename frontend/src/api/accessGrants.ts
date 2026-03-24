import apiClient from './client'
import type {
  AccessGrant,
  AccessGrantStatus,
  PaginatedResponse,
  UserRole,
} from '../types'

type ListParams = {
  query?: string
  status?: AccessGrantStatus
  page?: number
  size?: number
}

export async function listAccessGrants(params: ListParams): Promise<PaginatedResponse<AccessGrant>> {
  const response = await apiClient.get('/access-grants', { params })
  return response.data
}

export async function createAccessGrant(input: {
  employeeId: string
  grantStatus: AccessGrantStatus
  assignedRoles: UserRole[]
  note?: string
}): Promise<AccessGrant> {
  const response = await apiClient.post('/access-grants', input)
  return response.data
}

export async function updateAccessGrant(input: {
  employeeId: string
  assignedRoles: UserRole[]
  note?: string
}): Promise<AccessGrant> {
  const response = await apiClient.patch(`/access-grants/${input.employeeId}`, {
    assignedRoles: input.assignedRoles,
    note: input.note,
  })
  return response.data
}

export async function suspendAccessGrant(input: {
  employeeId: string
  note?: string
}): Promise<AccessGrant> {
  const response = await apiClient.post(`/access-grants/${input.employeeId}/suspend`, {
    note: input.note,
  })
  return response.data
}

export async function reactivateAccessGrant(input: {
  employeeId: string
  assignedRoles: UserRole[]
  note?: string
}): Promise<AccessGrant> {
  const response = await apiClient.post(`/access-grants/${input.employeeId}/reactivate`, {
    assignedRoles: input.assignedRoles,
    note: input.note,
  })
  return response.data
}
