import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listAuditLogs } from '../api/audit'
import type { AuditLogEntry } from '../types'

export const useAuditStore = defineStore('audit', () => {
  const logs = ref<AuditLogEntry[]>([])
  const total = ref(0)
  const page = ref(0)
  const size = ref(20)
  const loading = ref(false)

  async function fetchLogs() {
    loading.value = true
    try {
      const result = await listAuditLogs({ page: page.value, size: size.value })
      logs.value = result.data
      total.value = result.total
    } finally {
      loading.value = false
    }
  }

  function setPage(newPage: number) {
    page.value = newPage
  }

  return {
    logs,
    total,
    page,
    size,
    loading,
    fetchLogs,
    setPage,
  }
})
