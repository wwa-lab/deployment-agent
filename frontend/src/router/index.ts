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
      redirect: '/wwa/deployment-agent',
    },
    {
      path: '/release-flows',
      redirect: '/wwa/deployment-agent',
    },
    {
      path: '/release-flows/:id',
      redirect: (to) => `/wwa/deployment-agent/release-flows/${to.params.id as string}`,
    },
    {
      path: '/config',
      redirect: '/wwa/configuration-management',
    },
    {
      path: '/audit',
      redirect: '/wwa/audit-log',
    },
    {
      path: '/wwa',
      component: () => import('../views/WorkspaceLayout.vue'),
      children: [
        {
          path: '',
          redirect: { name: 'wwa-deployment-agent' },
        },
        {
          path: 'deployment-agent',
          name: 'wwa-deployment-agent',
          component: () => import('../views/ReleaseFlowSummaryView.vue'),
          meta: {
            section: 'deployment-agent',
            sectionTitle: 'Deployment Agent',
          },
        },
        {
          path: 'deployment-agent/release-flows/:id',
          name: 'wwa-deployment-agent-detail',
          component: () => import('../views/ReleaseFlowDetailView.vue'),
          meta: {
            section: 'deployment-agent',
            sectionTitle: 'Deployment Agent',
          },
        },
        {
          path: 'template-management',
          name: 'wwa-template-management',
          component: () => import('../views/TemplateManagementView.vue'),
          meta: {
            section: 'template-management',
            sectionTitle: 'Template Management',
          },
        },
        {
          path: 'configuration-management',
          name: 'wwa-configuration-management',
          component: () => import('../views/ConfigAdminView.vue'),
          meta: {
            section: 'configuration-management',
            sectionTitle: 'Configuration Management',
          },
        },
        {
          path: 'audit-log',
          name: 'wwa-audit-log',
          component: () => import('../views/AuditLogView.vue'),
          meta: {
            section: 'audit-log',
            sectionTitle: 'Audit Log',
          },
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
      return { name: 'wwa-deployment-agent' }
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
})

export default router
