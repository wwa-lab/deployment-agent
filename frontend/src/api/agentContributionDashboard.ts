import platformClient from './platformClient'

export type StageImplementationStatus = 'implemented' | 'in-progress' | 'backlog' | 'not-implemented'

export type StageStatusResponse = {
  statuses: Record<string, StageImplementationStatus>
  updatedBy?: string
  updatedAt?: string
}

export async function getAgentContributionDashboardStatuses(): Promise<StageStatusResponse> {
  const response = await platformClient.get('/agent-contribute-dashboard/statuses')
  return response.data as StageStatusResponse
}

export async function updateAgentContributionDashboardStatuses(
  statuses: Record<string, StageImplementationStatus>,
): Promise<StageStatusResponse> {
  const response = await platformClient.put('/agent-contribute-dashboard/statuses', { statuses })
  return response.data as StageStatusResponse
}
