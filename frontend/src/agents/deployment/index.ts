import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'

/**
 * Deployment Agent workspace — SIT/UAT/PROD release execution.
 *
 * Built via the platform {@link createAgentWorkspace} factory so that all
 * release-flow list/detail plumbing is shared with Testing and Build agents.
 * Deployment is the only agent with {@code supportsStitching=true} — its
 * getById forwards the {@code ?linked=} query param so the family view
 * returns linked SIT/UAT/PROD uploads as one logical rollout.
 */
export const deploymentAgent = createAgentWorkspace({
  agentKey: 'deployment-agent',
  agentName: 'Deployment Agent',
  stages: ['SIT', 'UAT', 'PROD'],
  supportsStitching: true,
})

export const deploymentClient = deploymentAgent.client
export const useReleaseFlowStore = deploymentAgent.useStore
