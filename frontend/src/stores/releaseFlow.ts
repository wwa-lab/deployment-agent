import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listReleaseFlows, getReleaseFlow } from '../api/releaseFlows'
import type { ReleaseFlowDetail, ReleaseFlowListItem } from '../types'

export const useReleaseFlowStore = defineStore('releaseFlow', () => {
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
    includeArchived?: boolean
  }>({})
  const selectedId = ref<string | null>(null)
  const detail = ref<ReleaseFlowDetail | null>(null)
  const loading = ref(false)
  const pollingInterval = ref<number | null>(null)
  const detailIncludeArchived = ref(false)

  async function fetchList() {
    loading.value = true
    try {
      const result = await listReleaseFlows({
        ...filters.value,
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
    loading.value = true
    try {
      detail.value = await getReleaseFlow(id)
    } finally {
      loading.value = false
    }
  }

  async function selectFlowWithArchived(id: string, includeArchived = false) {
    selectedId.value = id
    detailIncludeArchived.value = includeArchived
    loading.value = true
    try {
      detail.value = await getReleaseFlow(id, includeArchived ? { includeArchived: true } : undefined)
    } finally {
      loading.value = false
    }
  }

  async function refreshDetail() {
    if (selectedId.value) {
      detail.value = await getReleaseFlow(
        selectedId.value,
        detailIncludeArchived.value ? { includeArchived: true } : undefined,
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
