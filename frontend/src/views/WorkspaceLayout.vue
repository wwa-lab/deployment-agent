<script setup lang="ts">
import { useUserStore } from '../stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <div class="sidebar-logo">
        <span class="logo-icon">🚀</span>
        <span class="logo-text">Deployment Agent</span>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/release-flows" class="nav-link">
          <span class="nav-icon">📋</span>
          Release Flows
        </router-link>
        <router-link
          v-if="userStore.isDevOpsAdmin"
          to="/config"
          class="nav-link"
        >
          <span class="nav-icon">⚙️</span>
          Configuration
        </router-link>
        <router-link
          v-if="userStore.canViewAudit"
          to="/audit"
          class="nav-link"
        >
          <span class="nav-icon">📊</span>
          Audit Log
        </router-link>
      </nav>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-title">Deployment Agent</div>
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

.logo-icon { font-size: 20px; }

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
  height: 52px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
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
