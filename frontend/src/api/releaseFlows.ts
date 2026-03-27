import apiClient from './client'
import type {
  CreateRundownFromTemplateInput,
  PaginatedResponse,
  ReleaseFlowDetail,
  ReleaseFlowListItem,
  Request,
  RequestArchiveResult,
  RequestPurgeResult,
  UploadResponse,
} from '../types'

export interface ListReleaseFlowsParams {
  project?: string
  status?: string
  stage?: string
  application?: string
  snowGroup?: string
  agent?: string
  view?: 'flow' | 'stitched'
  attemptView?: 'latest' | 'history'
  includeArchived?: boolean
  page?: number
  size?: number
}

export async function listReleaseFlows(
  params: ListReleaseFlowsParams = {}
): Promise<PaginatedResponse<ReleaseFlowListItem>> {
  const response = await apiClient.get('/release-flows', { params })
  return response.data
}

export async function getReleaseFlow(
  id: string,
  params?: { includeArchived?: boolean; linked?: string }
): Promise<ReleaseFlowDetail> {
  const response = await apiClient.get(`/release-flows/${id}`, { params })
  return response.data
}

export async function createRundownFromTemplate(
  input: CreateRundownFromTemplateInput
): Promise<UploadResponse> {
  const response = await apiClient.post('/release-flows/from-template', input)
  return response.data
}

export interface UpdateRequestRundownInput {
  snowGroup?: string
  application?: string
  agent?: string
  owner?: string
  site?: string
  estimatedRemainingMinutes?: number
}

export async function updateRequestRundown(
  flowId: string,
  requestId: string,
  input: UpdateRequestRundownInput
): Promise<Request> {
  const response = await apiClient.patch(`/release-flows/${flowId}/requests/${requestId}/rundown`, input)
  return response.data
}

export async function startRequestDeployment(flowId: string, requestId: string): Promise<Request> {
  const response = await apiClient.post(`/release-flows/${flowId}/requests/${requestId}/start`)
  return response.data
}

export async function markRequestFailed(flowId: string, requestId: string): Promise<Request> {
  const response = await apiClient.post(`/release-flows/${flowId}/requests/${requestId}/fail`)
  return response.data
}

export async function archiveRequestRundown(
  flowId: string,
  requestId: string
): Promise<RequestArchiveResult> {
  const response = await apiClient.post(`/release-flows/${flowId}/requests/${requestId}/archive`)
  return response.data
}

export async function restoreRequestRundown(
  flowId: string,
  requestId: string
): Promise<RequestArchiveResult> {
  const response = await apiClient.post(`/release-flows/${flowId}/requests/${requestId}/restore`)
  return response.data
}

export async function purgeRequestRundown(
  flowId: string,
  requestId: string
): Promise<RequestPurgeResult> {
  const response = await apiClient.delete(`/release-flows/${flowId}/requests/${requestId}/purge`)
  return response.data
}
