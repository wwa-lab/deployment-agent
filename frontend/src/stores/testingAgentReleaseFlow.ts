import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listReleaseFlows, getReleaseFlow } from '../api/testingAgentReleaseFlows'
import type { ReleaseFlowDetail, ReleaseFlowListItem } from '../types'

export const useTestingAgentReleaseFlowStore = defineStore('testingAgentReleaseFlow', () => {
  const list = ref<ReleaseFlowListItem[]>([])
  const total = ref(0)
  const page = ref(0)
  const size = ref(10)
  const filters = ref<{
    project?: string
    status?: string
    stage?: string
    application?: string
    snowGroup?: string
    agent?: string
    attemptView?: 'latest' | 'history'
    includeArchived?: boolean
  }>({})
  const selectedId = ref<string | null>(null)
  const detail = ref<ReleaseFlowDetail | null>(null)
  const loading = ref(false)
  const pollingInterval = ref<number | null>(null)
  const detailIncludeArchived = ref(false)
  const detailLinked = ref<string | null>(null)

  async function fetchList() {
    loading.value = true
    try {
      const result = await listReleaseFlows({
        ...filters.value,
        view: 'stitched',
        attemptView: filters.value.attemptView ?? 'latest',
        page: page.value,
        size: size.value,
      })
      list.value = result.data
      total.value = result.total
    } finally {
      loading.value = false
    }
  }

  async function selectFlow(id: string) {
    selectedId.value = id
    detailIncludeArchived.value = false
    detailLinked.value = null
    loading.value = true
    try {
      detail.value = await getReleaseFlow(id)
    } finally {
      loading.value = false
    }
  }

  async function selectFlowWithArchived(id: string, includeArchived = false, linked?: string) {
    selectedId.value = id
    detailIncludeArchived.value = includeArchived
    detailLinked.value = linked ?? null
    loading.value = true
    try {
      detail.value = await getReleaseFlow(id, {
        ...(includeArchived ? { includeArchived: true } : {}),
        ...(linked ? { linked } : {}),
      })
    } finally {
      loading.value = false
    }
  }

  async function refreshDetail() {
    if (selectedId.value) {
      detail.value = await getReleaseFlow(
        selectedId.value,
        {
          ...(detailIncludeArchived.value ? { includeArchived: true } : {}),
          ...(detailLinked.value ? { linked: detailLinked.value } : {}),
        },
      )
    }
  }

  function startPolling() {
    if (pollingInterval.value !== null) return
    pollingInterval.value = window.setInterval(() => {
      fetchList()
    }, 10000)
  }

  function stopPolling() {
    if (pollingInterval.value !== null) {
      clearInterval(pollingInterval.value)
      pollingInterval.value = null
    }
  }

  function setFilter(key: keyof typeof filters.value, value: string | boolean | undefined) {
    filters.value = { ...filters.value, [key]: value }
    page.value = 0
  }

  function setPage(newPage: number) {
    page.value = newPage
  }

  return {
    list,
    total,
    page,
    size,
    filters,
    selectedId,
    detail,
    loading,
    pollingInterval,
    detailIncludeArchived,
    detailLinked,
    fetchList,
    selectFlow,
    selectFlowWithArchived,
    refreshDetail,
    startPolling,
    stopPolling,
    setFilter,
    setPage,
  }
})
