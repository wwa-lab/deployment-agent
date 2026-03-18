import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/release-flows',
    },
    {
      path: '/',
      component: () => import('../views/WorkspaceLayout.vue'),
      children: [
        {
          path: 'release-flows',
          name: 'release-flows',
          component: () => import('../views/ReleaseFlowSummaryView.vue'),
        },
        {
          path: 'release-flows/:id',
          name: 'release-flow-detail',
          component: () => import('../views/ReleaseFlowDetailView.vue'),
        },
        {
          path: 'config',
          name: 'config',
          component: () => import('../views/ConfigAdminView.vue'),
          meta: { requiresRole: 'DEVOPS_ADMIN' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('../views/AuditLogView.vue'),
          meta: { requiresRole: 'AUDIT_MGMT' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const requiredRole = to.meta.requiresRole as string | undefined
  if (requiredRole) {
    const userStore = useUserStore()
    if (userStore.role !== requiredRole) {
      return { name: 'release-flows' }
    }
  }
})

export default router
