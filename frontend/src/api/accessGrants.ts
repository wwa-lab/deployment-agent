import platformClient from './platformClient'
import type {
  AccessGrant,
  AccessGrantDirectoryCandidate,
  AccessScope,
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
  const response = await platformClient.get('/access-grants', { params })
  return response.data
}

export async function searchAccessGrantDirectory(query: string, limit = 8): Promise<AccessGrantDirectoryCandidate[]> {
  const response = await platformClient.get('/access-grants/directory', {
    params: { query, limit },
  })
  return response.data
}

export async function createAccessGrant(input: {
  employeeId: string
  displayName?: string
  grantStatus: AccessGrantStatus
  assignedRoles: UserRole[]
  scopeGrants: AccessScope[]
  note?: string
}): Promise<AccessGrant> {
  const response = await platformClient.post('/access-grants', input)
  return response.data
}

export async function updateAccessGrant(input: {
  employeeId: string
  assignedRoles: UserRole[]
  scopeGrants: AccessScope[]
  note?: string
}): Promise<AccessGrant> {
  const response = await platformClient.patch(`/access-grants/${input.employeeId}`, {
    assignedRoles: input.assignedRoles,
    scopeGrants: input.scopeGrants,
    note: input.note,
  })
  return response.data
}

export async function suspendAccessGrant(input: {
  employeeId: string
  note?: string
}): Promise<AccessGrant> {
  const response = await platformClient.post(`/access-grants/${input.employeeId}/suspend`, {
    note: input.note,
  })
  return response.data
}

export async function reactivateAccessGrant(input: {
  employeeId: string
  assignedRoles: UserRole[]
  scopeGrants: AccessScope[]
  note?: string
}): Promise<AccessGrant> {
  const response = await platformClient.post(`/access-grants/${input.employeeId}/reactivate`, {
    assignedRoles: input.assignedRoles,
    scopeGrants: input.scopeGrants,
    note: input.note,
  })
  return response.data
}
