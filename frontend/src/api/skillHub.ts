import platformClient from './platformClient'

export type SkillStatus = 'ACTIVE' | 'DRAFT' | 'DEPRECATED' | 'ARCHIVED'

export type SkillHubSkill = {
  id: string
  name: string
  description: string
  category: string
  tags: string[]
  owner: string
  status: SkillStatus
  currentVersion: string
  versionNotes?: string
  contentSourceType: 'FILE_PATH'
  sourcePath: string
  contentSha256?: string
  currentVersionId?: string
  lastIndexedAt?: string
  currentContentSnapshot?: string
  versions: SkillHubVersionSummary[]
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export type SkillHubVersionSummary = {
  id: string
  version: string
  versionNotes?: string
  sourcePath: string
  contentSha256: string
  createdBy: string
  createdAt: string
}

export type SkillHubVersionDetail = SkillHubVersionSummary & {
  skillId: string
  contentSnapshot: string
}

export type SkillHubSkillPayload = {
  name: string
  description: string
  category: string
  tags: string[]
  owner: string
  status: SkillStatus
  currentVersion: string
  versionNotes: string
  sourcePath?: string
  content?: string
}

export type SkillHubVersionPayload = {
  version: string
  versionNotes: string
  content: string
}

export type SkillHubListResponse = {
  data: SkillHubSkill[]
  total: number
  page: number
  size: number
}

export type SkillHubListParams = {
  query?: string
  category?: string
  status?: SkillStatus | ''
  page?: number
  size?: number
}

export async function listSkillHubSkills(params: SkillHubListParams = {}): Promise<SkillHubListResponse> {
  const response = await platformClient.get('/skill-hub/skills', { params })
  return response.data as SkillHubListResponse
}

export async function getSkillHubSkill(id: string): Promise<SkillHubSkill> {
  const response = await platformClient.get(`/skill-hub/skills/${id}`)
  return response.data as SkillHubSkill
}

export async function createSkillHubSkill(payload: SkillHubSkillPayload): Promise<SkillHubSkill> {
  const response = await platformClient.post('/skill-hub/skills', payload)
  return response.data as SkillHubSkill
}

export async function updateSkillHubSkill(
  id: string,
  payload: SkillHubSkillPayload,
): Promise<SkillHubSkill> {
  const response = await platformClient.put(`/skill-hub/skills/${id}`, payload)
  return response.data as SkillHubSkill
}

export async function createSkillHubVersion(
  id: string,
  payload: SkillHubVersionPayload,
): Promise<SkillHubVersionDetail> {
  const response = await platformClient.post(`/skill-hub/skills/${id}/versions`, payload)
  return response.data as SkillHubVersionDetail
}

export async function getSkillHubVersion(id: string, versionId: string): Promise<SkillHubVersionDetail> {
  const response = await platformClient.get(`/skill-hub/skills/${id}/versions/${versionId}`)
  return response.data as SkillHubVersionDetail
}
