import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listConfig, listConfigComponents, updateConfig, updateConfigComponent } from '../api/config'
import type { ConfigComponent, ConfigItem } from '../types'

export const useConfigStore = defineStore('config', () => {
  const items = ref<ConfigItem[]>([])
  const components = ref<ConfigComponent[]>([])
  const loading = ref(false)
  const error = ref('')

  async function fetchConfig() {
    loading.value = true
    error.value = ''
    try {
      const [configResult, componentResult] = await Promise.all([listConfig(), listConfigComponents()])
      items.value = configResult.data
      components.value = componentResult.data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load configuration'
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(item: {
    componentId?: string
    key: string
    value: string
    description?: string
  }) {
    error.value = ''
    try {
      const updated = await updateConfig(item)
      const idx = items.value.findIndex((i) => i.key === updated.key)
      if (idx !== -1) {
        items.value[idx] = updated
      } else {
        items.value.push(updated)
      }
      const componentResult = await listConfigComponents()
      components.value = componentResult.data
      return updated
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to save configuration'
      throw e
    }
  }

  async function saveComponent(component: {
    componentId: string
    displayName: string
    area: string
    application: string
    snowGroup: string
    agent: string
    serviceEndpoint: string
    serviceUser?: string
    credentialValue?: string
    description?: string
  }) {
    error.value = ''
    try {
      const updated = await updateConfigComponent(component)
      const idx = components.value.findIndex((item) => item.componentId === updated.componentId)
      if (idx !== -1) {
        components.value[idx] = updated
      } else {
        components.value.push(updated)
      }
      const result = await listConfig()
      items.value = result.data
      return updated
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to save component configuration'
      throw e
    }
  }

  return {
    items,
    components,
    loading,
    error,
    fetchConfig,
    saveConfig,
    saveComponent,
  }
})
