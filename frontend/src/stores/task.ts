import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useReleaseFlowStore } from './releaseFlow'
import type { Task } from '../types'

export const useTaskStore = defineStore('task', () => {
  const loading = ref(false)
  const selectedRequestId = ref<string | null>(null)

  const tasks = computed<Task[]>(() => {
    const releaseFlowStore = useReleaseFlowStore()
    if (!releaseFlowStore.detail) return []
    if (selectedRequestId.value) {
      const req = releaseFlowStore.detail.requests.find(
        (r) => r.id === selectedRequestId.value
      )
      return req?.tasks ?? []
    }
    return releaseFlowStore.detail.requests.flatMap((r) => r.tasks)
  })

  function selectRequest(id: string | null) {
    selectedRequestId.value = id
  }

  return {
    tasks,
    loading,
    selectedRequestId,
    selectRequest,
  }
})
