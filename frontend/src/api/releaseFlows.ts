import apiClient from './client'
import type { PaginatedResponse, ReleaseFlowDetail, ReleaseFlowListItem, Request } from '../types'

export interface ListReleaseFlowsParams {
  project?: string
  status?: string
  stage?: string
  page?: number
  size?: number
}

export async function listReleaseFlows(
  params: ListReleaseFlowsParams = {}
): Promise<PaginatedResponse<ReleaseFlowListItem>> {
  const response = await apiClient.get('/release-flows', { params })
  return response.data
}

export async function getReleaseFlow(id: string): Promise<ReleaseFlowDetail> {
  const response = await apiClient.get(`/release-flows/${id}`)
  return response.data
}

export interface UpdateRequestRundownInput {
  snowGroup?: string
  application?: string
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
