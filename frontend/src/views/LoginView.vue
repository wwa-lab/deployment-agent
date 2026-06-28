<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const router = useRouter()

const employeeId = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const guestLoading = ref(false)

async function handleGuestLogin() {
  error.value = ''
  guestLoading.value = true
  try {
    await userStore.loginAsGuest()
    router.push('/wwa/home')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Unable to start guest session'
  } finally {
    guestLoading.value = false
  }
}

async function handleLogin() {
  error.value = ''
  const hasEmployeeId = employeeId.value.trim().length > 0
  const hasPassword = password.value.trim().length > 0

  if (!hasEmployeeId && !hasPassword) {
    error.value = 'Please enter employee ID and a password.'
    return
  }

  if (!hasEmployeeId) {
    error.value = 'Please enter employee ID.'
    return
  }

  if (!hasPassword) {
    error.value = 'Please enter any non-empty password for local testing.'
    return
  }
  loading.value = true
  try {
    await userStore.login(employeeId.value, password.value)
    router.push('/wwa/home')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-shell">
      <section class="login-story" aria-labelledby="login-story-title">
        <div class="login-story-grid">
          <div class="login-story-copy-block">
            <div class="login-story-kicker">Atlas Engineering Delivery Hub</div>
            <h1 id="login-story-title" class="login-story-title">Team delivery framework</h1>
            <p class="login-story-copy">
              Plan, build, test, deploy, and govern delivery work from one shared hub.
            </p>
            <div class="login-story-pills">
              <span class="login-story-pill">Build Workflow</span>
              <span class="login-story-pill">Testing Workflow</span>
              <span class="login-story-pill">Deployment Workflow</span>
              <span class="login-story-pill">Platform Controls</span>
            </div>
          </div>

          <div class="login-console" aria-hidden="true">
            <div class="login-console-topbar">
              <div class="login-console-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <span class="login-console-caption">ATLAS HUB CONTROL PLANE</span>
              <span class="login-console-status">Live</span>
            </div>

            <div class="login-console-metrics">
              <article class="login-console-metric">
                <span class="metric-label">Current Focus</span>
                <strong class="metric-value">Agent Ops</strong>
              </article>
              <article class="login-console-metric">
                <span class="metric-label">Approval Model</span>
                <strong class="metric-value">Human-in-the-Loop</strong>
              </article>
              <article class="login-console-metric">
                <span class="metric-label">Execution</span>
                <strong class="metric-value">Jenkins + Ansible</strong>
              </article>
            </div>

            <div class="login-console-flow">
              <div class="console-block-label">Active Agents</div>
              <div class="login-flow-track">
                <span class="login-flow-node is-active">Build</span>
                <span class="login-flow-line"></span>
                <span class="login-flow-node is-active">Testing</span>
                <span class="login-flow-line"></span>
                <span class="login-flow-node is-active">Deploy</span>
                <span class="login-flow-line is-dim"></span>
                <span class="login-flow-node">Platform</span>
              </div>
            </div>

            <div class="login-console-stream">
              <div class="login-console-stream-row">
                <span class="stream-dot"></span>
                <span>Upload, track, and execute rundowns across stages.</span>
              </div>
              <div class="login-console-stream-row">
                <span class="stream-dot"></span>
                <span>Human-in-the-loop approval at every checkpoint.</span>
              </div>
              <div class="login-console-stream-row">
                <span class="stream-dot"></span>
                <span>Config, audit, and access controls as shared services.</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="login-card">
        <div class="login-header">
          <div class="login-kicker">Atlas Hub Access</div>
          <h2 class="login-title">Sign in to continue</h2>
          <p class="login-subtitle">Use your Team Book credentials to enter Atlas Engineering Delivery Hub.</p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <div v-if="error" class="login-error">{{ error }}</div>

          <div class="form-group">
            <label for="employeeId" class="form-label">Employee ID</label>
            <input
              id="employeeId"
              v-model="employeeId"
              type="text"
              class="form-input"
              placeholder="e.g. emp-001"
              autocomplete="username"
            />
          </div>

          <div class="form-group">
            <label for="password" class="form-label">Password</label>
            <input
              id="password"
              v-model="password"
              type="password"
              class="form-input"
              placeholder="Password"
              autocomplete="current-password"
            />
            <p class="field-hint">For local testing, any non-empty password works.</p>
          </div>

          <button type="submit" class="btn btn-primary btn-full" :disabled="loading || guestLoading">
            {{ loading ? 'Signing in...' : 'Sign In' }}
          </button>
        </form>

        <div class="login-guest">
          <span class="login-guest-divider">or</span>
          <button
            type="button"
            class="btn btn-secondary btn-full login-guest-btn"
            :disabled="loading || guestLoading"
            @click="handleGuestLogin"
          >
            {{ guestLoading ? 'Entering...' : 'Continue as Guest (read-only)' }}
          </button>
          <p class="login-guest-hint">
            No account needed. Browse read-only — write actions are disabled.
          </p>
        </div>

        <div class="login-hint">
          <p>Dev accounts: emp-001 (Developer), emp-002 (TL), emp-003 (DevOps Admin), emp-004 (Audit), emp-005 (Management)</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  position: relative;
  overflow: hidden;
}

.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(70px);
}

.login-page::before {
  top: 6%;
  left: 8%;
  width: 360px;
  height: 360px;
  background: rgba(96, 150, 255, 0.26);
}

.login-page::after {
  right: 5%;
  bottom: 8%;
  width: 320px;
  height: 320px;
  background: rgba(78, 221, 185, 0.18);
}

.login-shell {
  width: min(1160px, 100%);
  display: grid;
  grid-template-columns: minmax(320px, 1.2fr) minmax(320px, 400px);
  gap: 24px;
  align-items: stretch;
  position: relative;
  z-index: 1;
}

.login-story {
  position: relative;
  padding: 40px;
  border-radius: 30px;
  background:
    linear-gradient(180deg, rgba(17, 29, 53, 0.96) 0%, rgba(24, 39, 72, 0.92) 52%, rgba(17, 28, 53, 0.94) 100%);
  border: 1px solid rgba(87, 122, 185, 0.4);
  box-shadow: 0 28px 70px rgba(16, 24, 43, 0.32);
  overflow: hidden;
}

.login-story::before,
.login-story::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.login-story::before {
  inset: 0;
  background:
    linear-gradient(rgba(113, 151, 223, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(113, 151, 223, 0.12) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.78), rgba(0, 0, 0, 0.22));
}

.login-story::after {
  top: -80px;
  right: -60px;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(60, 138, 255, 0.34) 0%, rgba(60, 138, 255, 0) 72%);
}

.login-story-grid {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 28px;
  height: 100%;
}

.login-story-copy-block {
  max-width: 34rem;
}

.login-story-kicker,
.login-kicker {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #91a8d8;
}

.login-story-title {
  margin: 14px 0 14px;
  font-size: clamp(32px, 4.2vw, 46px);
  line-height: 1.05;
  color: #f3f7ff;
}

.login-story-copy {
  font-size: 15px;
  line-height: 1.7;
  color: rgba(222, 232, 255, 0.78);
}

.login-story-pills {
  margin-top: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.login-story-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(83, 108, 160, 0.18);
  border: 1px solid rgba(120, 154, 222, 0.32);
  color: #dbe8ff;
  font-size: 12px;
  font-weight: 600;
}

.login-console {
  position: relative;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(10, 18, 34, 0.92) 0%, rgba(14, 24, 44, 0.9) 100%);
  border: 1px solid rgba(93, 127, 196, 0.24);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 40px rgba(4, 9, 21, 0.34);
  overflow: hidden;
}

.login-console::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(83, 118, 183, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(83, 118, 183, 0.12) 1px, transparent 1px);
  background-size: 34px 34px;
  opacity: 0.6;
}

.login-console::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 -30%;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(92, 164, 255, 0.08), transparent);
  transform: skewX(-18deg);
  animation: console-sweep 7s ease-in-out infinite;
}

.login-console-topbar,
.login-console-metrics,
.login-console-flow,
.login-console-stream {
  position: relative;
  z-index: 1;
}

.login-console-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.login-console-dots {
  display: inline-flex;
  gap: 6px;
}

.login-console-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(162, 189, 239, 0.9);
}

.login-console-caption {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  color: #86a4e4;
}

.login-console-status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  color: #8ef0d2;
  text-transform: uppercase;
}

.login-console-status::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ee0b7;
  box-shadow: 0 0 0 0 rgba(78, 224, 183, 0.45);
  animation: status-pulse 1.8s ease-out infinite;
}

.login-console-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.login-console-metric {
  padding: 14px 12px;
  border-radius: 16px;
  background: rgba(58, 78, 118, 0.16);
  border: 1px solid rgba(105, 137, 203, 0.2);
}

.metric-label,
.console-block-label {
  display: block;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7f98c9;
}

.metric-value {
  display: block;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #f3f7ff;
}

.login-console-flow {
  margin-top: 18px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(43, 61, 98, 0.16);
  border: 1px solid rgba(105, 137, 203, 0.18);
}

.login-flow-track {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.login-flow-node {
  min-width: 62px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid rgba(121, 150, 211, 0.3);
  background: rgba(29, 42, 69, 0.9);
  color: #9bb0d8;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-mono);
  text-align: center;
}

.login-flow-node.is-active {
  border-color: rgba(104, 180, 255, 0.7);
  background: rgba(44, 86, 162, 0.5);
  color: #f4f9ff;
  box-shadow: inset 0 0 0 1px rgba(122, 195, 255, 0.18);
}

.login-flow-line {
  flex: 1;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(92, 164, 255, 0.88), rgba(92, 164, 255, 0.28));
}

.login-flow-line.is-dim {
  background: linear-gradient(90deg, rgba(92, 164, 255, 0.36), rgba(92, 164, 255, 0.14));
}

.login-console-stream {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.login-console-stream-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #d6e2ff;
  font-size: 13px;
  line-height: 1.5;
}

.stream-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5ca4ff;
  margin-top: 6px;
  flex-shrink: 0;
}

.login-card {
  position: relative;
  width: 100%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(246, 250, 255, 0.84) 100%);
  border: 1px solid rgba(190, 209, 243, 0.9);
  border-radius: 24px;
  box-shadow: 0 24px 54px rgba(31, 42, 68, 0.16);
  backdrop-filter: blur(24px);
  padding: 36px 32px;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, rgba(70, 109, 214, 0), rgba(70, 109, 214, 0.92), rgba(70, 109, 214, 0));
}

.login-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(132, 156, 205, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(132, 156, 205, 0.08) 1px, transparent 1px);
  background-size: 30px 30px;
  opacity: 0.5;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.28), transparent 55%);
  pointer-events: none;
}

.login-header {
  position: relative;
  z-index: 1;
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(215, 227, 247, 0.9);
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 10px 0 6px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.login-form {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.form-input {
  padding: 9px 12px;
  border: 1px solid #d8e3f3;
  border-radius: 10px;
  font-size: 14px;
  color: var(--color-text-primary);
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.form-input:focus {
  outline: none;
  border-color: #2563eb;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.btn-full {
  width: 100%;
  padding: 11px;
  margin-top: 8px;
  justify-content: center;
}

.login-error {
  background: #fef2f2;
  color: #dc2626;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  border: 1px solid #fecaca;
}

.login-guest {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
}

.login-guest-divider {
  align-self: center;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #94a3b8;
}

.login-guest-btn {
  padding: 10px;
  justify-content: center;
}

.login-guest-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
  line-height: 1.5;
}

.login-hint {
  position: relative;
  z-index: 1;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(227, 234, 247, 0.92);
  font-size: 12px;
  color: #7f90af;
  line-height: 1.6;
}

@keyframes status-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(78, 224, 183, 0.45);
  }

  60% {
    box-shadow: 0 0 0 10px rgba(78, 224, 183, 0);
  }
}

@keyframes console-sweep {
  0%,
  100% {
    transform: translateX(-8%) skewX(-18deg);
  }

  50% {
    transform: translateX(228%) skewX(-18deg);
  }
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 620px;
  }

  .login-story {
    padding: 28px;
  }

  .login-console-metrics {
    grid-template-columns: 1fr;
  }

  .login-story-title {
    font-size: 34px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 20px 14px;
  }

  .login-card,
  .login-story {
    padding: 24px 20px;
    border-radius: 20px;
  }

  .login-console {
    padding: 16px;
  }

  .login-flow-track {
    gap: 8px;
  }

  .login-flow-node {
    min-width: 54px;
    padding: 8px 8px;
  }
}
</style>
