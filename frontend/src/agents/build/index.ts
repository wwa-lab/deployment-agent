import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'

/**
 * Build Agent workspace — DEV-stage build and packaging.
 *
 * <p>Created via the platform {@code createAgentWorkspace} factory so that no
 * bespoke per-agent code exists. Consumes the generic
 * {@link AgentSummaryView} / {@link AgentDetailView} components registered in
 * the router.
 */
export const buildAgent = createAgentWorkspace({
  agentKey: 'build-agent',
  agentName: 'Build Agent',
  stages: ['DEV'],
  supportsStitching: false,
  defaultStage: 'DEV',
})

export const useBuildAgentStore = buildAgent.useStore
