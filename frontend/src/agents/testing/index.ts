import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'

/**
 * Testing Agent workspace — UAT-stage A/B testing for iSeries programs.
 *
 * Built via the platform {@link createAgentWorkspace} factory so that the
 * Testing Agent shares all release-flow list/detail plumbing with Deployment
 * and Build agents. Extra task/request/upload endpoints are published below
 * via {@link testingApi}.
 */
export const testingAgent = createAgentWorkspace({
  agentKey: 'testing-agent',
  agentName: 'Testing Agent',
  stages: ['UAT'],
  supportsStitching: false,
  defaultStage: 'UAT',
})

export const testingClient = testingAgent.client
export const useTestingAgentReleaseFlowStore = testingAgent.useStore
