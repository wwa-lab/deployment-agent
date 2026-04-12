export const AGENT_ID = {
  DEPLOYMENT: 'deployment-agent',
  TESTING: 'testing-agent',
  BUILD: 'build-agent',
} as const

export type AgentIdValue = typeof AGENT_ID[keyof typeof AGENT_ID]
