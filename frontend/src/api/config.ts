import apiClient from './client'
import type { ConfigItem } from '../types'

interface ConfigApiItem {
  configKey: ConfigItem['key']
  configValue: string
  description?: string
  updatedBy?: string
  updatedAt?: string
}

function mapConfigItem(item: ConfigApiItem): ConfigItem {
  return {
    key: item.configKey,
    value: item.configValue,
    description: item.description,
    updatedBy: item.updatedBy,
    updatedAt: item.updatedAt,
  }
}

export async function listConfig(): Promise<{ data: ConfigItem[] }> {
  const response = await apiClient.get('/config')
  return {
    data: (response.data as ConfigApiItem[]).map(mapConfigItem),
  }
}

export async function updateConfig(item: {
  key: string
  value: string
  description?: string
}): Promise<ConfigItem> {
  const response = await apiClient.post('/config', item)
  return mapConfigItem(response.data as ConfigApiItem)
}
