import apiClient from './client'
import type {
  DevelopmentSpec,
  DevelopmentSpecStatus,
  DevelopmentSpecUpsertRequest,
  PaginatedResponse,
} from '../types'

export interface ListDevelopmentSpecsParams {
  query?: string
  status?: DevelopmentSpecStatus
  page?: number
  size?: number
}

export interface DevelopmentSpecExportResult {
  blob: Blob
  filename: string
}

function extractFilename(contentDisposition?: string): string {
  if (!contentDisposition) return 'development-spec-export.md'

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const simpleMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return simpleMatch?.[1] ?? 'development-spec-export.md'
}

export async function listDevelopmentSpecs(
  params: ListDevelopmentSpecsParams = {}
): Promise<PaginatedResponse<DevelopmentSpec>> {
  const response = await apiClient.get('/development-specs', { params })
  return response.data
}

export async function getDevelopmentSpec(id: string): Promise<DevelopmentSpec> {
  const response = await apiClient.get(`/development-specs/${id}`)
  return response.data
}

export async function createDevelopmentSpec(
  input: DevelopmentSpecUpsertRequest
): Promise<DevelopmentSpec> {
  const response = await apiClient.post('/development-specs', input)
  return response.data
}

export async function updateDevelopmentSpec(
  id: string,
  input: DevelopmentSpecUpsertRequest
): Promise<DevelopmentSpec> {
  const response = await apiClient.put(`/development-specs/${id}`, input)
  return response.data
}

export async function generateDevelopmentSpec(id: string): Promise<DevelopmentSpec> {
  const response = await apiClient.post(`/development-specs/${id}/generate`)
  return response.data
}

export async function exportDevelopmentSpec(
  id: string,
  format: 'markdown' | 'json'
): Promise<DevelopmentSpecExportResult> {
  const response = await apiClient.get(`/development-specs/${id}/export`, {
    params: { format },
    responseType: 'blob',
  })

  return {
    blob: response.data,
    filename: extractFilename(response.headers['content-disposition']),
  }
}
