import platformClient from './platformClient'
import type {
  DirectoryGroupType,
  DirectoryLinkKind,
  DirectoryScopeLayout,
  SdlcStageKey,
  ResourceCenterCatalog,
} from '../types'

export interface DirectoryScopeUpsertPayload {
  key: string
  title: string
  description?: string
  layout: DirectoryScopeLayout
  enabled?: boolean
  sortOrder?: number
}

export interface DirectoryGroupUpsertPayload {
  key: string
  title: string
  description?: string
  type: DirectoryGroupType
  stageKey?: SdlcStageKey
  stageOrder?: number
  agentName?: string
  enabled?: boolean
  sortOrder?: number
}

export interface DirectoryLinkUpsertPayload {
  title: string
  description?: string
  url: string
  kind: DirectoryLinkKind
  kindLabel?: string
  iconKey?: string
  enabled?: boolean
  sortOrder?: number
  targetScopeKey?: string
  targetGroupKey?: string
}

export async function getCatalog(includeDisabled = false): Promise<ResourceCenterCatalog> {
  const response = await platformClient.get('/resource-center', {
    params: { includeDisabled },
  })
  return response.data as ResourceCenterCatalog
}

export async function createScope(payload: DirectoryScopeUpsertPayload): Promise<ResourceCenterCatalog> {
  const response = await platformClient.post('/resource-center/scopes', payload)
  return response.data as ResourceCenterCatalog
}

export async function updateScope(
  scopeKey: string,
  expectedVersion: number,
  payload: DirectoryScopeUpsertPayload,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.put(`/resource-center/scopes/${scopeKey}`, payload, {
    params: { expectedVersion },
  })
  return response.data as ResourceCenterCatalog
}

export async function deleteScope(
  scopeKey: string,
  expectedVersion: number,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.delete(`/resource-center/scopes/${scopeKey}`, {
    params: { expectedVersion },
  })
  return response.data as ResourceCenterCatalog
}

export async function createGroup(
  scopeKey: string,
  payload: DirectoryGroupUpsertPayload,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.post(`/resource-center/scopes/${scopeKey}/groups`, payload)
  return response.data as ResourceCenterCatalog
}

export async function updateGroup(
  scopeKey: string,
  groupKey: string,
  expectedVersion: number,
  payload: DirectoryGroupUpsertPayload,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.put(
    `/resource-center/scopes/${scopeKey}/groups/${groupKey}`,
    payload,
    { params: { expectedVersion } },
  )
  return response.data as ResourceCenterCatalog
}

export async function deleteGroup(
  scopeKey: string,
  groupKey: string,
  expectedVersion: number,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.delete(
    `/resource-center/scopes/${scopeKey}/groups/${groupKey}`,
    { params: { expectedVersion } },
  )
  return response.data as ResourceCenterCatalog
}

export async function createLink(
  scopeKey: string,
  groupKey: string,
  payload: DirectoryLinkUpsertPayload,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.post(
    `/resource-center/scopes/${scopeKey}/groups/${groupKey}/links`,
    payload,
  )
  return response.data as ResourceCenterCatalog
}

export async function updateLink(
  linkId: string,
  expectedVersion: number,
  payload: DirectoryLinkUpsertPayload,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.put(`/resource-center/links/${linkId}`, payload, {
    params: { expectedVersion },
  })
  return response.data as ResourceCenterCatalog
}

export async function deleteLink(
  linkId: string,
  expectedVersion: number,
): Promise<ResourceCenterCatalog> {
  const response = await platformClient.delete(`/resource-center/links/${linkId}`, {
    params: { expectedVersion },
  })
  return response.data as ResourceCenterCatalog
}
