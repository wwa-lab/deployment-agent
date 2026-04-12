import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  deleteScopeDirectoryEntry,
  listScopeDirectoryEntries,
  saveScopeDirectoryEntry,
} from '../api/config'
import type { ScopeDirectoryEntry } from '../types'

export const useScopeDirectoryStore = defineStore('scope-directory', () => {
  const entries = ref<ScopeDirectoryEntry[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  async function fetchEntries(force = false) {
    if (loading.value) return
    if (loaded.value && !force) return

    loading.value = true
    error.value = ''
    try {
      const result = await listScopeDirectoryEntries()
      entries.value = result.data
      loaded.value = true
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load scope directory'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function saveEntry(entry: {
    id?: string
    application: string
    snowGroup?: string
    agent?: string
  }) {
    error.value = ''
    try {
      const updated = await saveScopeDirectoryEntry(entry)
      await fetchEntries(true)
      return updated
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to save scope directory entry'
      throw e
    }
  }

  async function removeEntry(id: string) {
    error.value = ''
    try {
      await deleteScopeDirectoryEntry(id)
      await fetchEntries(true)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to delete scope directory entry'
      throw e
    }
  }

  return {
    entries,
    loading,
    loaded,
    error,
    fetchEntries,
    saveEntry,
    removeEntry,
  }
})
