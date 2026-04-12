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
    <section class="home-hero">
      <div class="home-hero-layout">
        <div class="home-hero-main">
          <div class="home-header">
            <div class="home-header-text">
              <div class="home-platform-label">WWA Platform</div>
              <h1 class="home-title">WWA Control Center</h1>
              <p class="home-subtitle">
                Operate agent workspaces and shared platform controls from one command surface.
              </p>
            </div>
            <div class="home-user-block">
              <div class="home-user-name">{{ userStore.displayName || userStore.userId }}</div>
              <div class="home-user-role badge badge-role">{{ userStore.role }}</div>
              <a :href="FINBLOCK_URL" class="finblock-return-link">← Return to FinBlock</a>
            </div>
          </div>

          <section class="wwa-intro-card home-intro-card" aria-labelledby="wwa-home-intro-title">
            <div class="wwa-intro-kicker">WWA Today</div>
              <h2 id="wwa-home-intro-title" class="wwa-intro-title">A shared workflow automation workspace</h2>
              <p class="wwa-intro-text">
              WWA currently serves controlled workflow operations, including Build Agent,
              Testing Agent, Deployment Agent, and shared platform capabilities. Use this hub to
              enter the active workspaces today, while AI-assisted capabilities are introduced in
              later phases.
              </p>
            </section>
        </div>

        <aside class="home-console" aria-label="WWA control summary">
          <div class="home-console-topbar">
            <span class="home-console-kicker">Control Plane</span>
            <span class="home-console-status">Online</span>
          </div>

          <div class="home-console-panels">
            <article class="home-console-panel">
              <span class="home-console-panel-label">Current Focus</span>
              <strong class="home-console-panel-value">Agent Workflows</strong>
              <p class="home-console-panel-copy">Build, testing, and deployment workspaces with human checkpoints.</p>
            </article>
            <article class="home-console-panel">
              <span class="home-console-panel-label">Shared Controls</span>
              <strong class="home-console-panel-value">Config + Audit</strong>
              <p class="home-console-panel-copy">Configuration, access, and traceability stay centralized.</p>
            </article>
            <article class="home-console-panel">
              <span class="home-console-panel-label">Roadmap</span>
              <strong class="home-console-panel-value">AI Assist</strong>
              <p class="home-console-panel-copy">Intelligence layers join after the control plane is stable.</p>
            </article>
          </div>

          <div class="home-console-track">
            <div class="home-console-track-label">Active Workspaces</div>
            <div class="home-console-track-row">
              <span class="home-console-node is-active">Build</span>
              <span class="home-console-line"></span>
              <span class="home-console-node is-active">Testing</span>
              <span class="home-console-line"></span>
              <span class="home-console-node is-active">Deploy</span>
              <span class="home-console-line is-dim"></span>
              <span class="home-console-node">Platform</span>
            </div>
          </div>

          <div class="home-console-stream">
            <div class="home-console-stream-row">
              <span class="home-console-stream-dot"></span>
              <span>Build, testing, and deployment workspaces stay available from one shared hub.</span>
            </div>
            <div class="home-console-stream-row">
              <span class="home-console-stream-dot"></span>
              <span>Shared controls stay visible from the navigation rail at all times.</span>
            </div>
            <div class="home-console-stream-row">
              <span class="home-console-stream-dot"></span>
              <span>Future AI capability is planned as an overlay, not a replacement.</span>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <section class="agent-section">
      <h2 class="section-heading">Active Workspaces</h2>
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
      <h2 class="section-heading">Shared Controls</h2>
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
  gap: 32px;
}

.home-hero {
  padding: 30px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid var(--color-border-subtle);
  box-shadow: var(--shadow-card);
}

.home-hero-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(280px, 360px);
  gap: 24px;
  align-items: stretch;
}

.home-hero-main {
  min-width: 0;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.home-platform-label {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
  margin-bottom: 6px;
}

.home-title {
  font-size: 30px;
  font-weight: 700;
  color: #17294a;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.home-subtitle {
  font-size: 15px;
  color: #566989;
  margin: 0;
  max-width: 60ch;
}

.home-user-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f9fafb;
  border: 1px solid var(--color-border-subtle);
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

.home-intro-card {
  margin-top: 24px;
}

.home-console {
  position: relative;
  padding: 18px;
  border-radius: 12px;
  background: #1e293b;
  border: 1px solid #334155;
  overflow: hidden;
}

.home-console-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.home-console-kicker,
.home-console-track-label,
.home-console-panel-label {
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.home-console-kicker,
.home-console-track-label {
  color: #8ea8dd;
}

.home-console-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8ef0d2;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.home-console-status::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ee0b7;
}

.home-console-panels {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-console-panel {
  padding: 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid #334155;
}

.home-console-panel-label {
  color: #7f98c9;
}

.home-console-panel-value {
  display: block;
  margin-top: 8px;
  color: #f4f8ff;
  font-size: 17px;
  font-weight: 700;
}

.home-console-panel-copy {
  margin: 8px 0 0;
  color: rgba(214, 226, 255, 0.74);
  font-size: 13px;
  line-height: 1.5;
}

.home-console-track {
  margin-top: 14px;
  padding: 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid #334155;
}

.home-console-track-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.home-console-node {
  min-width: 60px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid rgba(121, 150, 211, 0.3);
  background: rgba(29, 42, 69, 0.88);
  color: #9bb0d8;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-mono);
  text-align: center;
}

.home-console-node.is-active {
  border-color: rgba(104, 180, 255, 0.7);
  background: rgba(44, 86, 162, 0.5);
  color: #f4f9ff;
}

.home-console-line {
  flex: 1;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(92, 164, 255, 0.88), rgba(92, 164, 255, 0.26));
}

.home-console-line.is-dim {
  background: linear-gradient(90deg, rgba(92, 164, 255, 0.34), rgba(92, 164, 255, 0.12));
}

.home-console-stream {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-console-stream-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #d7e4ff;
  font-size: 13px;
  line-height: 1.5;
}

.home-console-stream-dot {
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: #5ca4ff;
  flex-shrink: 0;
}

.section-heading {
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-mono);
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
  background: #ffffff;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
  width: 100%;
  box-shadow: var(--shadow-card);
}

.agent-card:hover {
  border-color: #2563eb;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.1);
}

.agent-card-icon {
  font-size: 32px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border-radius: 8px;
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
  background: #ffffff;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
  min-width: 200px;
  box-shadow: var(--shadow-card);
}

.platform-card:hover {
  border-color: #93c5fd;
  background: #f9fafb;
}

.platform-card-icon {
  font-size: 18px;
}

@media (max-width: 768px) {
  .home-hero {
    padding: 20px;
    border-radius: 20px;
  }

  .home-hero-layout {
    grid-template-columns: 1fr;
  }

  .home-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .home-user-block {
    align-items: flex-start;
  }

  .platform-card {
    min-width: 100%;
  }
}
</style>
