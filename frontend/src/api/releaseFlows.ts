import apiClient from './client'
import type { PaginatedResponse, ReleaseFlowDetail, ReleaseFlowListItem } from '../types'

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
