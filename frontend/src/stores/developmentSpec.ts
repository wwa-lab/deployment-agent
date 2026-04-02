import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createDevelopmentSpec,
  exportDevelopmentSpec,
  generateDevelopmentSpec,
  getDevelopmentSpec,
  listDevelopmentSpecs,
  updateDevelopmentSpec,
} from '../api/developmentSpecs'
import type {
  DevelopmentSpec,
  DevelopmentSpecStatus,
  DevelopmentSpecUpsertRequest,
} from '../types'

export const useDevelopmentSpecStore = defineStore('developmentSpec', () => {
  const list = ref<DevelopmentSpec[]>([])
  const total = ref(0)
  const page = ref(0)
  const size = ref(20)
  const filters = ref<{
    query?: string
    status?: DevelopmentSpecStatus
  }>({})
  const selectedId = ref<string | null>(null)
  const detail = ref<DevelopmentSpec | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const generating = ref(false)
  const exporting = ref(false)
  const error = ref<string | null>(null)

  async function fetchList() {
    loading.value = true
    error.value = null
    try {
      const result = await listDevelopmentSpecs({
        ...filters.value,
        page: page.value,
        size: size.value,
      })
      list.value = result.data
      total.value = result.total
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load Development Specs'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function createSpec(input: DevelopmentSpecUpsertRequest) {
    saving.value = true
    error.value = null
    try {
      const created = await createDevelopmentSpec(input)
      return created
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to create Development Spec'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function selectSpec(id: string) {
    selectedId.value = id
    loading.value = true
    error.value = null
    try {
      detail.value = await getDevelopmentSpec(id)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load Development Spec'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function refreshDetail() {
    if (!selectedId.value) return
    detail.value = await getDevelopmentSpec(selectedId.value)
  }

  async function saveSpec(input: DevelopmentSpecUpsertRequest) {
    if (!selectedId.value) throw new Error('No Development Spec selected')
    saving.value = true
    error.value = null
    try {
      detail.value = await updateDevelopmentSpec(selectedId.value, input)
      return detail.value
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to save Development Spec'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function runGenerate() {
    if (!selectedId.value) throw new Error('No Development Spec selected')
    generating.value = true
    error.value = null
    try {
      detail.value = await generateDevelopmentSpec(selectedId.value)
      return detail.value
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to generate Development Spec'
      throw e
    } finally {
      generating.value = false
    }
  }

  async function downloadExport(format: 'markdown' | 'json') {
    if (!selectedId.value) throw new Error('No Development Spec selected')
    exporting.value = true
    error.value = null
    try {
      return await exportDevelopmentSpec(selectedId.value, format)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to export Development Spec'
      throw e
    } finally {
      exporting.value = false
    }
  }

  function setFilter(key: keyof typeof filters.value, value: DevelopmentSpecStatus | string | undefined) {
    filters.value = { ...filters.value, [key]: value || undefined }
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
    saving,
    generating,
    exporting,
    error,
    fetchList,
    createSpec,
    selectSpec,
    refreshDetail,
    saveSpec,
    runGenerate,
    downloadExport,
    setFilter,
    setPage,
  }
})
