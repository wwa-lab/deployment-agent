import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'
import { AGENT_ID } from '../../config/agentId'

/**
 * Build Agent workspace — DEV-stage build and packaging.
 *
 * <p>Created via the platform {@code createAgentWorkspace} factory so all
 * shared plumbing (client, store, API) stays consistent across agents.
 * Views use thin wrappers around the shared
 * {@code platform/components/ReleaseFlow{Summary,Detail}View.vue},
 * following the same pattern as Deployment and Testing agents.
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
