import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listConfig, updateConfig } from '../api/config'
import type { ConfigItem } from '../types'

export const useConfigStore = defineStore('config', () => {
  const items = ref<ConfigItem[]>([])
  const loading = ref(false)

  async function fetchConfig() {
    loading.value = true
    try {
      const result = await listConfig()
      items.value = result.data
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(item: { key: string; value: string; description?: string }) {
    const updated = await updateConfig(item)
    const idx = items.value.findIndex((i) => i.key === updated.key)
    if (idx !== -1) {
      items.value[idx] = updated
    } else {
      items.value.push(updated)
    }
    return updated
  }

  return {
    items,
    loading,
    fetchConfig,
    saveConfig,
  }
})
