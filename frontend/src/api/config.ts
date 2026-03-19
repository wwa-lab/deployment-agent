import apiClient from './client'
import type { ConfigItem } from '../types'

export async function listConfig(): Promise<{ data: ConfigItem[] }> {
  const response = await apiClient.get('/config')
  return response.data
}

export async function updateConfig(item: {
  key: string
  value: string
  description?: string
}): Promise<ConfigItem> {
  const response = await apiClient.post('/config', item)
  return response.data
}
