import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createGroup,
  createLink,
  createScope,
  deleteGroup,
  deleteLink,
  deleteScope,
  getCatalog,
  updateGroup,
  updateLink,
  updateScope,
  type DirectoryGroupUpsertPayload,
  type DirectoryLinkUpsertPayload,
  type DirectoryScopeUpsertPayload,
} from '../api/resourceCenter'
import type { ResourceCenterCatalog } from '../types'

const CONFLICT_MESSAGE =
  'The directory changed in another session. Reloaded — please reapply your edit.'

function isConflictError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }
  return (
    error.message.includes('Concurrent update conflict') ||
    error.message.includes('OPTIMISTIC_LOCK_CONFLICT')
  )
}

export const useResourceCenterStore = defineStore('resourceCenter', () => {
  const catalog = ref<ResourceCenterCatalog | null>(null)
  const loading = ref(false)
  const error = ref('')
  const saving = ref(false)
  const saveError = ref('')

  async function fetchCatalog(includeDisabled = false) {
    loading.value = true
    error.value = ''
    try {
      catalog.value = await getCatalog(includeDisabled)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load service directory'
    } finally {
      loading.value = false
    }
  }

  async function runMutation(
    mutation: () => Promise<ResourceCenterCatalog>,
    includeDisabled: boolean,
  ) {
    saving.value = true
    saveError.value = ''
    try {
      catalog.value = await mutation()
      return catalog.value
    } catch (e: unknown) {
      if (isConflictError(e)) {
        saveError.value = CONFLICT_MESSAGE
        await fetchCatalog(includeDisabled)
      } else {
        saveError.value = e instanceof Error ? e.message : 'Failed to save changes'
      }
      throw e
    } finally {
      saving.value = false
    }
  }

  async function addScope(payload: DirectoryScopeUpsertPayload, includeDisabled: boolean) {
    return runMutation(() => createScope(payload), includeDisabled)
  }

  async function editScope(
    scopeKey: string,
    payload: DirectoryScopeUpsertPayload,
    includeDisabled: boolean,
  ) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => updateScope(scopeKey, version, payload), includeDisabled)
  }

  async function removeScope(scopeKey: string, includeDisabled: boolean) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => deleteScope(scopeKey, version), includeDisabled)
  }

  async function addGroup(
    scopeKey: string,
    payload: DirectoryGroupUpsertPayload,
    includeDisabled: boolean,
  ) {
    return runMutation(() => createGroup(scopeKey, payload), includeDisabled)
  }

  async function editGroup(
    scopeKey: string,
    groupKey: string,
    payload: DirectoryGroupUpsertPayload,
    includeDisabled: boolean,
  ) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => updateGroup(scopeKey, groupKey, version, payload), includeDisabled)
  }

  async function removeGroup(scopeKey: string, groupKey: string, includeDisabled: boolean) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => deleteGroup(scopeKey, groupKey, version), includeDisabled)
  }

  async function addLink(
    scopeKey: string,
    groupKey: string,
    payload: DirectoryLinkUpsertPayload,
    includeDisabled: boolean,
  ) {
    return runMutation(() => createLink(scopeKey, groupKey, payload), includeDisabled)
  }

  async function editLink(
    linkId: string,
    payload: DirectoryLinkUpsertPayload,
    includeDisabled: boolean,
  ) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => updateLink(linkId, version, payload), includeDisabled)
  }

  async function removeLink(linkId: string, includeDisabled: boolean) {
    const version = catalog.value?.version
    if (version == null) {
      throw new Error('Catalog version is unavailable')
    }
    return runMutation(() => deleteLink(linkId, version), includeDisabled)
  }

  return {
    catalog,
    loading,
    error,
    saving,
    saveError,
    fetchCatalog,
    addScope,
    editScope,
    removeScope,
    addGroup,
    editGroup,
    removeGroup,
    addLink,
    editLink,
    removeLink,
  }
})
