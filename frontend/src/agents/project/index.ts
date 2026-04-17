import { createAgentWorkspace } from '../../platform/composables/createAgentWorkspace'
import { AGENT_ID } from '../../config/agentId'

export const projectAgent = createAgentWorkspace({
  agentKey: AGENT_ID.PROJECT,
  agentName: 'Project Agent',
  stages: [
    'REQUIREMENT',
    'FUNCTIONAL_DESIGN',
    'TECHNICAL_DESIGN',
    'DEVELOPMENT',
    'TESTING',
    'PERFORMANCE_TEST',
    'RESULT_SIGNOFF',
    'BUSINESS_ENDORSEMENT',
    'CAB',
    'DEPLOYMENT',
    'POST_IMPLEMENTATION',
  ],
  supportsStitching: false,
})

export const projectClient = projectAgent.client
export const useProjectAgentStore = projectAgent.useStore
