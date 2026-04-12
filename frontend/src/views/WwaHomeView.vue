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
  position: relative;
}

.wwa-home::before,
.wwa-home::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  filter: blur(68px);
  pointer-events: none;
  z-index: 0;
}

.wwa-home::before {
  top: 18px;
  right: 40px;
  width: 180px;
  height: 180px;
  background: rgba(220, 235, 255, 0.72);
}

.wwa-home::after {
  top: 220px;
  left: -30px;
  width: 160px;
  height: 160px;
  background: rgba(232, 247, 242, 0.6);
}

.home-hero,
.agent-section,
.platform-section {
  position: relative;
  z-index: 1;
}

.home-hero {
  position: relative;
  padding: 30px;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.82) 0%, rgba(245, 249, 255, 0.96) 58%, rgba(241, 249, 246, 0.92) 100%);
  border: 1px solid rgba(208, 221, 245, 0.94);
  box-shadow: 0 22px 54px rgba(31, 42, 68, 0.12);
  backdrop-filter: blur(22px);
  overflow: hidden;
}

.home-hero::before,
.home-hero::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.home-hero::before {
  inset: 0;
  background:
    linear-gradient(rgba(128, 154, 209, 0.1) 1px, transparent 1px),
    linear-gradient(90deg, rgba(128, 154, 209, 0.1) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.42), transparent 68%);
}

.home-hero::after {
  inset: auto -70px -120px auto;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  background: rgba(220, 235, 255, 0.52);
  filter: blur(12px);
}

.home-hero-layout {
  position: relative;
  z-index: 1;
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
  border-bottom: 1px solid rgba(227, 234, 247, 0.92);
}

.home-platform-label {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}

.home-title {
  font-size: 30px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.home-subtitle {
  font-size: 15px;
  color: var(--color-text-secondary);
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
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(214, 226, 245, 0.92);
}

.home-user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
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
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(14, 25, 45, 0.96) 0%, rgba(16, 30, 56, 0.94) 100%);
  border: 1px solid rgba(85, 120, 188, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 20px 42px rgba(10, 18, 34, 0.24);
  overflow: hidden;
}

.home-console::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(87, 122, 190, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(87, 122, 190, 0.12) 1px, transparent 1px);
  background-size: 34px 34px;
  opacity: 0.58;
}

.home-console::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 -28%;
  width: 38%;
  background: linear-gradient(90deg, transparent, rgba(92, 164, 255, 0.08), transparent);
  transform: skewX(-18deg);
  animation: home-console-sweep 8s ease-in-out infinite;
}

.home-console-topbar,
.home-console-panels,
.home-console-track,
.home-console-stream {
  position: relative;
  z-index: 1;
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
  box-shadow: 0 0 0 0 rgba(78, 224, 183, 0.45);
  animation: home-status-pulse 1.8s ease-out infinite;
}

.home-console-panels {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-console-panel {
  padding: 14px;
  border-radius: 16px;
  background: rgba(60, 79, 118, 0.18);
  border: 1px solid rgba(105, 137, 203, 0.2);
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
  border-radius: 16px;
  background: rgba(43, 61, 98, 0.16);
  border: 1px solid rgba(105, 137, 203, 0.16);
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
  color: var(--color-text-muted);
  margin: 0 0 16px;
}

.agent-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.84) 0%, rgba(247, 250, 255, 0.78) 100%);
  border: 1px solid rgba(220, 230, 246, 0.96);
  border-radius: 16px;
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s, border-color 0.15s, box-shadow 0.15s;
  width: 100%;
  backdrop-filter: blur(18px);
  box-shadow: 0 12px 28px rgba(31, 42, 68, 0.08);
  overflow: hidden;
}

.agent-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(61, 107, 218, 0), rgba(61, 107, 218, 0.92), rgba(61, 107, 218, 0));
  opacity: 0;
  transition: opacity 0.2s;
}

.agent-card:hover {
  border-color: #2563eb;
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.12);
  transform: translateY(-2px);
}

.agent-card:hover::before {
  opacity: 1;
}

.agent-card-icon {
  font-size: 32px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, rgba(239, 244, 255, 0.98), rgba(234, 244, 241, 0.92));
  border-radius: 10px;
  flex-shrink: 0;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.agent-card-body {
  flex: 1;
}

.agent-card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.agent-card-desc {
  font-size: 13px;
  color: var(--color-text-muted);
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
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82) 0%, rgba(247, 250, 255, 0.78) 100%);
  border: 1px solid rgba(221, 231, 247, 0.94);
  border-radius: 14px;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s, transform 0.2s;
  min-width: 200px;
  backdrop-filter: blur(16px);
  box-shadow: 0 10px 24px rgba(31, 42, 68, 0.06);
  overflow: hidden;
}

.platform-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(61, 107, 218, 0), rgba(61, 107, 218, 0.82), rgba(61, 107, 218, 0));
  opacity: 0;
  transition: opacity 0.2s;
}

.platform-card:hover {
  border-color: #9db5df;
  background: rgba(243, 247, 255, 0.92);
  transform: translateY(-1px);
}

.platform-card:hover::before {
  opacity: 1;
}

.platform-card-icon {
  font-size: 18px;
}

@keyframes home-status-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(78, 224, 183, 0.45);
  }

  60% {
    box-shadow: 0 0 0 10px rgba(78, 224, 183, 0);
  }
}

@keyframes home-console-sweep {
  0%,
  100% {
    transform: translateX(-8%) skewX(-18deg);
  }

  50% {
    transform: translateX(226%) skewX(-18deg);
  }
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
