import axios, { type AxiosInstance } from 'axios'
import router from '../../router'
import { installCorrelationIdInterceptor } from '../../api/correlationId'
import { createReleaseFlowApi, type ReleaseFlowApi } from './createReleaseFlowApi'
import { createReleaseFlowStore } from './createReleaseFlowStore'

export interface AgentWorkspaceConfig {
  /** Unique agent key matching the backend route prefix, e.g. "deployment-agent". */
  agentKey: string
  /** Display name for the summary view and nav (e.g. "Deployment Agent"). */
  agentName: string
  /** Stages this agent allows (e.g. ['SIT','UAT','PROD'] or ['DEV']). */
  stages: string[]
  /** Whether `?linked=` stitching is supported on the detail view. */
  supportsStitching: boolean
  /** Optional default stage filter for the summary view. */
  defaultStage?: string
}

export interface AgentWorkspace {
  config: AgentWorkspaceConfig
  client: AxiosInstance
  api: ReleaseFlowApi
  useStore: ReturnType<typeof createReleaseFlowStore>
  routes: {
    summary: string
    detail: string
  }
}

/**
 * Builds an agent-scoped frontend workspace — API client, Release Flow API,
 * Pinia store, and route paths — from a single config. Each agent is independent;
 * two workspaces never share state because store ids and baseURLs differ.
 *
 * <p>Introduced in BA-T17 (Phase G) as a new tool. Existing agents (Deployment,
 * Testing) will migrate to it in Phase I.
 */
export function createAgentWorkspace(config: AgentWorkspaceConfig): AgentWorkspace {
  const client = axios.create({
    baseURL: `/api/${config.agentKey}`,
    headers: {
      'Content-Type': 'application/json',
    },
    withCredentials: true,
  })

  installCorrelationIdInterceptor(client)

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) {
        const currentPath = window.location.pathname
        if (currentPath !== '/login') {
          router.push('/login')
        }
      }
      const summary =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        'An unexpected error occurred'
      const details =
        typeof error.response?.data?.details === 'string' && error.response.data.details.trim()
          ? error.response.data.details.trim()
          : ''
      const message = details && details !== summary ? `${summary} ${details}` : summary
      return Promise.reject(new Error(message))
    },
  )

  const api = createReleaseFlowApi(client, { supportsStitching: config.supportsStitching })

  const useStore = createReleaseFlowStore({
    id: `${config.agentKey}-releaseFlow`,
    api,
    supportsStitching: config.supportsStitching,
    defaultStage: config.defaultStage,
  })

  const routes = {
    summary: `/wwa/${config.agentKey}`,
    detail: `/wwa/${config.agentKey}/release-flows/:id`,
  }

  return { config, client, api, useStore, routes }
}
