<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { agentRegistry, platformCapabilities } from '../config/agentRegistry'
import { FINBLOCK_URL } from '../config/platformConfig'

const router = useRouter()
const userStore = useUserStore()

const enabledAgents = agentRegistry.filter((a) => a.enabled)
</script>

<template>
  <div class="wwa-home">
    <div class="home-header">
      <div class="home-header-text">
        <div class="home-platform-label">WWA Platform</div>
        <h1 class="home-title">Agent Workspace Hub</h1>
        <p class="home-subtitle">
          Choose a workspace below. Shared platform capabilities are available in the left navigation.
        </p>
      </div>
      <div class="home-user-block">
        <div class="home-user-name">{{ userStore.displayName || userStore.userId }}</div>
        <div class="home-user-role badge badge-role">{{ userStore.role }}</div>
        <a :href="FINBLOCK_URL" class="finblock-return-link">← Return to FinBlock</a>
      </div>
    </div>

    <section class="agent-section">
      <h2 class="section-heading">Agent Workspaces</h2>
      <div class="agent-grid">
        <button
          v-for="agent in enabledAgents"
          :key="agent.key"
          class="agent-card"
          type="button"
          @click="router.push(agent.route)"
        >
          <div class="agent-card-icon">{{ agent.icon }}</div>
          <div class="agent-card-body">
            <div class="agent-card-name">{{ agent.name }}</div>
            <div class="agent-card-desc">{{ agent.description }}</div>
          </div>
          <div class="agent-card-arrow" aria-hidden="true">›</div>
        </button>
      </div>
    </section>

    <section class="platform-section">
      <h2 class="section-heading">Platform Capabilities</h2>
      <div class="platform-grid">
        <router-link
          v-for="cap in platformCapabilities"
          :key="cap.key"
          :to="cap.to"
          class="platform-card"
        >
          <span class="platform-card-icon">{{ cap.icon }}</span>
          <span class="platform-card-label">{{ cap.label }}</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.wwa-home {
  max-width: 960px;
  margin: 0 auto;
  padding: 8px 0 40px;
  display: flex;
  flex-direction: column;
  gap: 36px;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e2e8f0;
}

.home-platform-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
  margin-bottom: 6px;
}

.home-title {
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px;
}

.home-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.home-user-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.home-user-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.home-user-role {
  font-size: 11px;
}

.finblock-return-link {
  font-size: 12px;
  color: #2563eb;
  text-decoration: none;
  margin-top: 4px;
}

.finblock-return-link:hover {
  text-decoration: underline;
}

.section-heading {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  margin: 0 0 16px;
}

.agent-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
  width: 100%;
}

.agent-card:hover {
  border-color: #2563eb;
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.1);
}

.agent-card-icon {
  font-size: 32px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border-radius: 10px;
  flex-shrink: 0;
}

.agent-card-body {
  flex: 1;
}

.agent-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.agent-card-desc {
  font-size: 13px;
  color: #64748b;
}

.agent-card-arrow {
  font-size: 22px;
  color: #94a3b8;
  flex-shrink: 0;
}

.platform-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.platform-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
  min-width: 200px;
}

.platform-card:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.platform-card-icon {
  font-size: 18px;
}
</style>
