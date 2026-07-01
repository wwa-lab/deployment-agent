/**
 * Agent Registry — static configuration for Phase 1.
 *
 * Each entry defines the metadata WWA-Atlas Hub uses to render agent cards on the home page
 * and drive the shell flyout navigation. When a second agent is onboarded (WWA-019),
 * add an entry here. No shell code changes should be required.
 *
 * Fields align with the minimum agent intake information defined in
 * docs/00-context/multi-agent-integration-standard.md section 8.
 */

export type AgentCategory = 'deployment' | 'testing' | 'build' | 'platform' | 'other'

export type AgentDescriptor = {
  /** Unique stable key. Used as route segment and CSS key. */
  key: string
  /** Display name shown in nav and home page cards. */
  name: string
  /** One-line description for the home page agent card. */
  description: string
  /** Vue Router named route to navigate into this agent. */
  route: string
  /** Emoji or ASCII icon for nav and card display. */
  icon: string
  /** Whether this agent is currently active and visible to users. */
  enabled: boolean
  /** Logical grouping for nav section headers and home page layout. */
  category: AgentCategory
}

export const agentRegistry: AgentDescriptor[] = [
  {
    key: 'deployment-agent',
    name: 'Deployment Agent',
    description: 'Controlled, human-in-the-loop deployment workflow across SIT, UAT, and PROD stages.',
    route: '/wwa/deployment-agent',
    icon: '🚀',
    enabled: true,
    category: 'deployment',
  },
  {
    key: 'testing-agent',
    name: 'Testing Agent',
    description: 'Controlled, human-in-the-loop A/B testing workflow for iSeries programs.',
    route: '/wwa/testing-agent',
    icon: '🧪',
    enabled: true,
    category: 'testing',
  },
  {
    key: 'build-agent',
    name: 'Build Agent',
    description: 'DEV-stage build and packaging workspace for pre-deployment automation.',
    route: '/wwa/build-agent',
    icon: '🛠️',
    enabled: true,
    category: 'build',
  },
]

export function getAgentDescriptor(key: string): AgentDescriptor | undefined {
  return agentRegistry.find((agent) => agent.key === key)
}

/** WWA-Atlas Hub-owned shared capability nav items. Not agent workspaces. */
export type PlatformCapabilityDescriptor = {
  key: string
  label: string
  to: string
  icon: string
  /** If true, the item is visible but the user may not have access. */
  accessPermission?: string
}

export const platformCapabilities: PlatformCapabilityDescriptor[] = [
  {
    key: 'agent-contribute-dashboard',
    label: 'Agent Contribute Dashboard',
    to: '/wwa/agent-contribute-dashboard',
    icon: '▦',
  },
  {
    key: 'skill-hub',
    label: 'Skill Hub',
    to: '/wwa/skill-hub',
    icon: 'S',
  },
  {
    key: 'template-management',
    label: 'Template Management',
    to: '/wwa/template-management',
    icon: '🧩',
  },
  {
    key: 'configuration-management',
    label: 'Configuration Management',
    to: '/wwa/configuration-management',
    icon: '⚙️',
  },
  {
    key: 'audit-log',
    label: 'Audit Log',
    to: '/wwa/audit-log',
    icon: '📊',
    accessPermission: 'audit.view',
  },
  {
    key: 'access-management',
    label: 'Access Management',
    to: '/wwa/access-management',
    icon: '🛂',
    accessPermission: 'access.manage',
  },
]
