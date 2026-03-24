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
  const error = ref('')
  const operatorId = ref('')
  const application = ref('')
  const snowGroup = ref('')
  const agent = ref('')

  async function fetchLogs() {
    loading.value = true
    error.value = ''
    try {
      const result = await listAuditLogs({
        page: page.value,
        size: size.value,
        operatorId: operatorId.value || undefined,
        application: application.value || undefined,
        snowGroup: snowGroup.value || undefined,
        agent: agent.value || undefined,
      })
      logs.value = result.data
      total.value = result.total
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load audit logs'
    } finally {
      loading.value = false
    }
  }

  function setPage(newPage: number) {
    page.value = newPage
  }

  function setOperatorId(value: string) {
    operatorId.value = value
    page.value = 0
  }

  function setScopeFilters(filters: { application: string; snowGroup: string; agent: string }) {
    application.value = filters.application
    snowGroup.value = filters.snowGroup
    agent.value = filters.agent
    page.value = 0
  }

  return {
    logs,
    total,
    page,
    size,
    loading,
    error,
    operatorId,
    application,
    snowGroup,
    agent,
    fetchLogs,
    setPage,
    setOperatorId,
    setScopeFilters,
  }
})
