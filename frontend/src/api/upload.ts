import apiClient from './client'
import type { Stage, UploadResponse } from '../types'

export interface UploadScopeInput {
  snowGroup?: string
  application?: string
  agent?: string
}

export async function uploadFile(
  file: File,
  stage: Stage,
  scope: UploadScopeInput = {},
): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('stage', stage)
  if (scope.snowGroup) formData.append('snowGroup', scope.snowGroup)
  if (scope.application) formData.append('application', scope.application)
  if (scope.agent) formData.append('agent', scope.agent)

  const response = await apiClient.post('/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data
}

export async function downloadTemplate(): Promise<Blob> {
  const response = await apiClient.get('/upload/template', {
    responseType: 'blob',
  })
  return response.data
}
