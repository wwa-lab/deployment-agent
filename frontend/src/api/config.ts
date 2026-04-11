import platformClient from './platformClient'
import type { ConfigComponent, ConfigItem, ScopeDirectoryEntry } from '../types'

interface ConfigApiItem {
  componentInstanceId?: string
  componentId?: ConfigItem['componentId']
  configKey: ConfigItem['key']
  configValue: string
  description?: string
  updatedBy?: string
  updatedAt?: string
  application?: string
  snowGroup?: string
  agent?: string
  area?: string
  integration?: string
  scopeSource?: string
  sensitive?: boolean
  configured?: boolean
}

interface ConfigApiComponent {
  componentInstanceId?: string
  componentId: ConfigComponent['componentId']
  systemType: string
  displayName: string
  area: string
  application?: string
  snowGroup?: string
  agent?: string
  scopeSource: ConfigComponent['scopeSource']
  trackServiceUser: boolean
  trackCredential: boolean
  serviceEndpoint: string
  serviceUser?: string
  credentialConfigured: boolean
  description?: string
  updatedBy?: string
  updatedAt?: string
}

interface ScopeDirectoryApiEntry {
  id: string
  application: string
  snowGroup?: string
  scopeSource: ScopeDirectoryEntry['scopeSource']
  updatedBy?: string
  updatedAt?: string
}

function mapConfigItem(item: ConfigApiItem): ConfigItem {
  return {
    componentInstanceId: item.componentInstanceId,
    componentId: item.componentId,
    key: item.configKey,
    value: item.configValue,
    description: item.description,
    updatedBy: item.updatedBy,
    updatedAt: item.updatedAt,
    application: item.application,
    snowGroup: item.snowGroup,
    agent: item.agent,
    area: item.area,
    integration: item.integration,
    scopeSource: item.scopeSource,
    sensitive: item.sensitive,
    configured: item.configured,
  }
}

function mapConfigComponent(component: ConfigApiComponent): ConfigComponent {
  return {
    componentInstanceId: component.componentInstanceId,
    componentId: component.componentId,
    systemType: component.systemType,
    displayName: component.displayName,
    area: component.area,
    application: component.application,
    snowGroup: component.snowGroup,
    agent: component.agent,
    scopeSource: component.scopeSource,
    trackServiceUser: component.trackServiceUser,
    trackCredential: component.trackCredential,
    serviceEndpoint: component.serviceEndpoint,
    serviceUser: component.serviceUser,
    credentialConfigured: component.credentialConfigured,
    description: component.description,
    updatedBy: component.updatedBy,
    updatedAt: component.updatedAt,
  }
}

function mapScopeDirectoryEntry(entry: ScopeDirectoryApiEntry): ScopeDirectoryEntry {
  return {
    id: entry.id,
    application: entry.application,
    snowGroup: entry.snowGroup,
    scopeSource: entry.scopeSource,
    updatedBy: entry.updatedBy,
    updatedAt: entry.updatedAt,
  }
}

export async function listConfig(): Promise<{ data: ConfigItem[] }> {
  const response = await platformClient.get('/config')
  return {
    data: (response.data as ConfigApiItem[]).map(mapConfigItem),
  }
}

export async function listConfigComponents(): Promise<{ data: ConfigComponent[] }> {
  const response = await platformClient.get('/config/components')
  return {
    data: (response.data as ConfigApiComponent[]).map(mapConfigComponent),
  }
}

export async function updateConfig(item: {
  componentInstanceId?: string
  componentId?: string
  key: string
  value: string
  description?: string
}): Promise<ConfigItem> {
  const response = await platformClient.post('/config', item)
  return mapConfigItem(response.data as ConfigApiItem)
}

export async function updateConfigComponent(component: {
  componentInstanceId?: string
  componentId: string
  displayName: string
  area: string
  application?: string
  snowGroup?: string
  agent?: string
  serviceEndpoint: string
  serviceUser?: string
  credentialValue?: string
  description?: string
}): Promise<ConfigComponent> {
  const response = await platformClient.post('/config/components', component)
  return mapConfigComponent(response.data as ConfigApiComponent)
}

export async function deleteConfigComponent(componentInstanceId: string): Promise<void> {
  await platformClient.delete(`/config/components/${componentInstanceId}`)
}

export async function listScopeDirectoryEntries(): Promise<{ data: ScopeDirectoryEntry[] }> {
  const response = await platformClient.get('/config/scopes')
  return {
    data: (response.data as ScopeDirectoryApiEntry[]).map(mapScopeDirectoryEntry),
  }
}

export async function saveScopeDirectoryEntry(entry: {
  id?: string
  application: string
  snowGroup?: string
}): Promise<ScopeDirectoryEntry> {
  const response = await platformClient.post('/config/scopes', entry)
  return mapScopeDirectoryEntry(response.data as ScopeDirectoryApiEntry)
}

export async function deleteScopeDirectoryEntry(id: string): Promise<void> {
  await platformClient.delete(`/config/scopes/${id}`)
}
