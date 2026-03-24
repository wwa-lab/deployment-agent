import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AccessScope, UserPermission, UserRole } from '../types'
import { login as apiLogin, logout as apiLogout, checkSession } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const userId = ref<string>('')
  const roles = ref<UserRole[]>([])
  const permissions = ref<UserPermission[]>([])
  const scopes = ref<AccessScope[]>([])
  const displayName = ref<string>('')
  const isAuthenticated = ref(false)

  const role = computed<UserRole | ''>(() => roles.value[0] ?? '')
  const hasRole = (targetRole: UserRole) => roles.value.includes(targetRole)
  const hasPermission = (permission: UserPermission) => permissions.value.includes(permission)

  const isTL = computed(() => hasRole('TL'))
  const isDeveloper = computed(() => hasRole('DEVELOPER'))
  const isDevOpsAdmin = computed(() => hasRole('DEVOPS_ADMIN'))
  const isAudit = computed(() => hasRole('AUDIT'))
  const isManagement = computed(() => hasRole('MANAGEMENT'))
  const canViewAudit = computed(() => hasPermission('audit.view'))
  const canUploadRelease = computed(() => hasPermission('release.upload'))
  const canManageAccess = computed(() => hasPermission('access.manage'))

  function applyAuthResponse(response: {
    userId: string
    role?: UserRole
    roles: UserRole[]
    permissions: UserPermission[]
    displayName: string
    scopes: AccessScope[]
  }) {
    userId.value = response.userId
    roles.value = response.roles.length > 0
      ? response.roles
      : response.role
        ? [response.role]
        : []
    permissions.value = response.permissions ?? []
    scopes.value = response.scopes ?? []
    displayName.value = response.displayName
    isAuthenticated.value = true
  }

  async function login(employeeId: string, password: string) {
    const response = await apiLogin(employeeId, password)
    applyAuthResponse(response)
  }

  async function logout() {
    await apiLogout()
    userId.value = ''
    roles.value = []
    permissions.value = []
    scopes.value = []
    displayName.value = ''
    isAuthenticated.value = false
  }

  async function initSession() {
    try {
      const response = await checkSession()
      applyAuthResponse(response)
    } catch {
      userId.value = ''
      roles.value = []
      permissions.value = []
      scopes.value = []
      displayName.value = ''
      isAuthenticated.value = false
    }
  }

  return {
    userId,
    role,
    roles,
    permissions,
    scopes,
    displayName,
    isAuthenticated,
    isTL,
    isDeveloper,
    isDevOpsAdmin,
    isAudit,
    isManagement,
    canViewAudit,
    canUploadRelease,
    canManageAccess,
    hasRole,
    hasPermission,
    login,
    logout,
    initSession,
  }
})
