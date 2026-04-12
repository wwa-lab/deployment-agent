import type { AxiosInstance } from 'axios'
import type {
  PaginatedResponse,
  ReleaseFlowDetail,
  ReleaseFlowListItem,
  Request,
  RequestArchiveResult,
  RequestPurgeResult,
} from '../../types'

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

export interface ReleaseFlowApi {
  list(params?: ListReleaseFlowsParams): Promise<PaginatedResponse<ReleaseFlowListItem>>
  getById(id: string, params?: { includeArchived?: boolean; linked?: string }): Promise<ReleaseFlowDetail>
  startRequest(flowId: string, requestId: string): Promise<Request>
  failRequest(flowId: string, requestId: string): Promise<Request>
  archiveRequest(flowId: string, requestId: string): Promise<RequestArchiveResult>
  restoreRequest(flowId: string, requestId: string): Promise<RequestArchiveResult>
  purgeRequest(flowId: string, requestId: string): Promise<RequestPurgeResult>
}

/**
 * Builds an agent-scoped Release Flow API bound to the provided Axios instance.
 *
 * <p>`supportsStitching` controls whether the `?linked=` query param is forwarded
 * on `getById`. Agents without stitching (Testing, Build) will always pass
 * `linked=undefined`, which drops the param from the URL.
 */
export function createReleaseFlowApi(
  client: AxiosInstance,
  config: { supportsStitching: boolean },
): ReleaseFlowApi {
  return {
    async list(params = {}) {
      const response = await client.get('/release-flows', { params })
      return response.data
    },
    async getById(id, params) {
      const forwarded = config.supportsStitching
        ? params
        : params
          ? { includeArchived: params.includeArchived }
          : undefined
      const response = await client.get(`/release-flows/${id}`, { params: forwarded })
      return response.data
    },
    async startRequest(flowId, requestId) {
      const response = await client.post(`/release-flows/${flowId}/requests/${requestId}/start`)
      return response.data
    },
    async failRequest(flowId, requestId) {
      const response = await client.post(`/release-flows/${flowId}/requests/${requestId}/fail`)
      return response.data
    },
    async archiveRequest(flowId, requestId) {
      const response = await client.post(`/release-flows/${flowId}/requests/${requestId}/archive`)
      return response.data
    },
    async restoreRequest(flowId, requestId) {
      const response = await client.post(`/release-flows/${flowId}/requests/${requestId}/restore`)
      return response.data
    },
    async purgeRequest(flowId, requestId) {
      const response = await client.delete(`/release-flows/${flowId}/requests/${requestId}/purge`)
      return response.data
    },
  }
}
