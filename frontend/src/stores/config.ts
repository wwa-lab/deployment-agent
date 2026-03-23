import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listConfig, updateConfig } from '../api/config'
import type { ConfigItem } from '../types'

export const useConfigStore = defineStore('config', () => {
  const items = ref<ConfigItem[]>([])
  const loading = ref(false)
  const error = ref('')

  async function fetchConfig() {
    loading.value = true
    error.value = ''
    try {
      const result = await listConfig()
      items.value = result.data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load configuration'
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(item: { key: string; value: string; description?: string }) {
    error.value = ''
    try {
      const updated = await updateConfig(item)
      const idx = items.value.findIndex((i) => i.key === updated.key)
      if (idx !== -1) {
        items.value[idx] = updated
      } else {
        items.value.push(updated)
      }
      return updated
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to save configuration'
      throw e
    }
  }

  return {
    items,
    loading,
    error,
    fetchConfig,
    saveConfig,
  }
})
