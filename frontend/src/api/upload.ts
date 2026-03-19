import apiClient from './client'
import type { Stage, UploadResponse } from '../types'

export async function uploadFile(file: File, stage: Stage): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('stage', stage)

  const response = await apiClient.post('/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data
}
