import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createAccessGrant,
  listAccessGrants,
  reactivateAccessGrant,
  suspendAccessGrant,
  updateAccessGrant,
} from '../api/accessGrants'
import type { AccessGrant, AccessGrantStatus, AccessScope, UserRole } from '../types'

export const useAccessGrantStore = defineStore('accessGrants', () => {
  const grants = ref<AccessGrant[]>([])
  const total = ref(0)
  const page = ref(0)
  const size = ref(20)
  const loading = ref(false)
  const error = ref('')
  const query = ref('')
  const status = ref<'ALL' | AccessGrantStatus>('ALL')

  async function fetchGrants() {
    loading.value = true
    error.value = ''
    try {
      const result = await listAccessGrants({
        page: page.value,
        size: size.value,
        query: query.value.trim() || undefined,
        status: status.value === 'ALL' ? undefined : status.value,
      })
      grants.value = result.data
      total.value = result.total
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load access grants'
    } finally {
      loading.value = false
    }
  }

  async function grantAccess(input: {
    employeeId: string
    grantStatus: AccessGrantStatus
    assignedRoles: UserRole[]
    scopeGrants: AccessScope[]
    note?: string
  }) {
    error.value = ''
    const result = await createAccessGrant(input)
    await fetchGrants()
    return result
  }

  async function editGrant(input: {
    employeeId: string
    assignedRoles: UserRole[]
    scopeGrants: AccessScope[]
    note?: string
  }) {
    error.value = ''
    const result = await updateAccessGrant(input)
    await fetchGrants()
    return result
  }

  async function suspendGrant(employeeId: string, note?: string) {
    error.value = ''
    const result = await suspendAccessGrant({ employeeId, note })
    await fetchGrants()
    return result
  }

  async function reactivateGrant(input: {
    employeeId: string
    assignedRoles: UserRole[]
    scopeGrants: AccessScope[]
    note?: string
  }) {
    error.value = ''
    const result = await reactivateAccessGrant(input)
    await fetchGrants()
    return result
  }

  function setPage(nextPage: number) {
    page.value = nextPage
  }

  function setQuery(value: string) {
    query.value = value
    page.value = 0
  }

  function setStatus(value: 'ALL' | AccessGrantStatus) {
    status.value = value
    page.value = 0
  }

  return {
    grants,
    total,
    page,
    size,
    loading,
    error,
    query,
    status,
    fetchGrants,
    grantAccess,
    editGrant,
    suspendGrant,
    reactivateGrant,
    setPage,
    setQuery,
    setStatus,
  }
})
