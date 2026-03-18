import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserRole } from '../types'

export const useUserStore = defineStore('user', () => {
  const userId = ref<string>('dev-user')
  const role = ref<UserRole>('TL')

  const isTL = computed(() => role.value === 'TL')
  const isDeveloper = computed(() => role.value === 'DEVELOPER')
  const isDevOpsAdmin = computed(() => role.value === 'DEVOPS_ADMIN')
  const isAuditMgmt = computed(() => role.value === 'AUDIT_MGMT')

  function setUser(newUserId: string, newRole: UserRole) {
    userId.value = newUserId
    role.value = newRole
  }

  return {
    userId,
    role,
    isTL,
    isDeveloper,
    isDevOpsAdmin,
    isAuditMgmt,
    setUser,
  }
})
