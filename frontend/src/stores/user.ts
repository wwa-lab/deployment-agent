import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserRole } from '../types'
import { login as apiLogin, logout as apiLogout, checkSession } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const userId = ref<string>('')
  const role = ref<UserRole | ''>('')
  const displayName = ref<string>('')
  const isAuthenticated = ref(false)

  const isTL = computed(() => role.value === 'TL')
  const isDeveloper = computed(() => role.value === 'DEVELOPER')
  const isDevOpsAdmin = computed(() => role.value === 'DEVOPS_ADMIN')
  const isAudit = computed(() => role.value === 'AUDIT')
  const isManagement = computed(() => role.value === 'MANAGEMENT')
  const canViewAudit = computed(() => role.value === 'AUDIT' || role.value === 'MANAGEMENT')

  async function login(employeeId: string, password: string) {
    const response = await apiLogin(employeeId, password)
    userId.value = response.userId
    role.value = response.role
    displayName.value = response.displayName
    isAuthenticated.value = true
  }

  async function logout() {
    await apiLogout()
    userId.value = ''
    role.value = ''
    displayName.value = ''
    isAuthenticated.value = false
  }

  async function initSession() {
    try {
      const response = await checkSession()
      userId.value = response.userId
      role.value = response.role
      displayName.value = response.displayName
      isAuthenticated.value = true
    } catch {
      isAuthenticated.value = false
    }
  }

  return {
    userId,
    role,
    displayName,
    isAuthenticated,
    isTL,
    isDeveloper,
    isDevOpsAdmin,
    isAudit,
    isManagement,
    canViewAudit,
    login,
    logout,
    initSession,
  }
})
