import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'
import { AGENT_ID } from '../../config/agentId'

/**
 * Build Agent workspace — DEV-stage build and packaging.
 *
 * <p>Created via the platform {@code createAgentWorkspace} factory so all
 * shared plumbing (client, store, API) stays consistent across agents. The
 * Build Agent keeps its own bespoke views (`BuildAgentSummaryView` /
 * `BuildAgentDetailView`) because its DEV-stage UI is simpler than the
 * shared `platform/components/ReleaseFlow{Summary,Detail}View.vue` used by
 * Deployment and Testing agents.
 */
export const buildAgent = createAgentWorkspace({
  agentKey: AGENT_ID.BUILD,
  agentName: 'Build Agent',
  stages: ['DEV'],
  supportsStitching: false,
  defaultStage: 'DEV',
})

export const buildClient = buildAgent.client
export const useBuildAgentStore = buildAgent.useStore
