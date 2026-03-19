import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
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
          meta: { requiresRole: ['DEVOPS_ADMIN'] },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('../views/AuditLogView.vue'),
          meta: { requiresRole: ['AUDIT', 'MANAGEMENT'] },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // Public routes (login) don't require auth
  if (to.meta.public) {
    // Redirect to home if already authenticated
    if (userStore.isAuthenticated) {
      return { name: 'release-flows' }
    }
    return
  }

  // Check authentication
  if (!userStore.isAuthenticated) {
    // Try to restore session
    await userStore.initSession()
    if (!userStore.isAuthenticated) {
      return { name: 'login' }
    }
  }

  // Check role-based access
  const requiredRoles = to.meta.requiresRole as string[] | undefined
  if (requiredRoles && !requiredRoles.includes(userStore.role as string)) {
    return { name: 'release-flows' }
  }
})

export default router
