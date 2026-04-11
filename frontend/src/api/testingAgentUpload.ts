import apiClient from './testingAgentClient'
import platformClient from './platformClient'
import type { Stage, UploadResponse } from '../types'

export interface UploadOptions {
  releaseId?: string
  snowGroup?: string
  application?: string
  agent?: string
}

export async function uploadFile(
  file: File,
  stage: Stage,
  options: UploadOptions = {},
): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('stage', stage)
  if (options.releaseId) formData.append('releaseId', options.releaseId)
  if (options.snowGroup) formData.append('snowGroup', options.snowGroup)
  if (options.application) formData.append('application', options.application)
  if (options.agent) formData.append('agent', options.agent)

  const response = await apiClient.post('/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data
}

export async function downloadTemplate(): Promise<Blob> {
  const response = await platformClient.get('/upload/template', {
    responseType: 'blob',
  })
  return response.data
}
