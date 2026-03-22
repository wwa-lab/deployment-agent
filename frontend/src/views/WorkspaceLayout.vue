<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '../stores/user'
import { useRoute, useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

type NavItem = {
  key: string
  label: string
  to: string
  icon: string
  visible: boolean
}

const navItems = computed<NavItem[]>(() => [
  {
    key: 'deployment-agent',
    label: 'Deployment Agent',
    to: '/wwa/deployment-agent',
    icon: '🚀',
    visible: true,
  },
  {
    key: 'template-management',
    label: 'Template Management',
    to: '/wwa/template-management',
    icon: '🧩',
    visible: true,
  },
  {
    key: 'configuration-management',
    label: 'Configuration Management',
    to: '/wwa/configuration-management',
    icon: '⚙️',
    visible: userStore.isDevOpsAdmin,
  },
  {
    key: 'audit-log',
    label: 'Audit Log',
    to: '/wwa/audit-log',
    icon: '📊',
    visible: userStore.canViewAudit,
  },
])

const activeSectionTitle = computed(() => (route.meta.sectionTitle as string) ?? 'WWA')

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <div class="sidebar-logo">
        <span class="logo-icon">▣</span>
        <div class="logo-copy">
          <span class="logo-text">WWA</span>
          <span class="logo-subtitle">Work With Agent</span>
        </div>
      </div>
      <div class="sidebar-section-label">Workspace</div>
      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems.filter((item) => item.visible)"
          :key="item.key"
          :to="item.to"
          class="nav-link"
        >
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-branding">
          <div class="topbar-kicker">WWA</div>
          <div class="topbar-title">{{ activeSectionTitle }}</div>
        </div>
        <div class="topbar-user">
          <span class="user-name">{{ userStore.displayName || userStore.userId }}</span>
          <span class="badge badge-role">{{ userStore.role }}</span>
          <button class="btn btn-secondary btn-sm" @click="handleLogout">Logout</button>
        </div>
      </header>
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #1e293b;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid #334155;
  font-weight: 700;
  font-size: 15px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #2563eb;
  color: white;
  font-size: 14px;
}

.logo-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-text {
  color: #f8fafc;
}

.logo-subtitle {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.sidebar-section-label {
  padding: 12px 16px 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  padding: 12px 8px;
  gap: 2px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s, color 0.15s;
}

.nav-label {
  min-width: 0;
}

.nav-link:hover {
  background: #334155;
  color: #e2e8f0;
}

.nav-link.router-link-active {
  background: #2563eb;
  color: white;
}

.nav-icon { font-size: 16px; }

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  min-height: 60px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.topbar-branding {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.topbar-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.topbar-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
