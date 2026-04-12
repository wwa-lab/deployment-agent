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
            <div class="login-story-kicker">WWA Platform</div>
            <h1 id="login-story-title" class="login-story-title">Workflow automation first, AI next</h1>
            <p class="login-story-copy">
              WWA currently focuses on controlled workflow automation, bringing testing, deployment,
              platform controls, and audit visibility into one shared workspace. AI-assisted
              workflows will be layered in gradually in future phases.
            </p>
            <div class="login-story-pills">
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
              <span class="login-console-caption">WWA CONTROL PLANE</span>
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
                <span class="metric-label">Next Phase</span>
                <strong class="metric-value">AI Assist</strong>
              </article>
            </div>

            <div class="login-console-flow">
              <div class="console-block-label">Active Workspaces</div>
              <div class="login-flow-track">
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
                <span>Testing and deployment status stay visible from one workspace hub.</span>
              </div>
              <div class="login-console-stream-row">
                <span class="stream-dot"></span>
                <span>Config, audit, and access controls stay available as shared services.</span>
              </div>
              <div class="login-console-stream-row">
                <span class="stream-dot"></span>
                <span>AI copilots join only after the automation control plane is solid.</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="login-card">
        <div class="login-header">
          <div class="login-kicker">Workspace Access</div>
          <h2 class="login-title">Sign in to continue</h2>
          <p class="login-subtitle">Use your Team Book credentials to enter the WWA workspace.</p>
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
            No account needed. Browse every page read-only — uploads, executions, and edits are disabled.
          </p>
        </div>

        <div class="login-hint">
          <p>Dev accounts: emp-001 (Developer), emp-002 (TL), emp-003 (DevOps Admin), emp-004 (Audit), emp-005 (Management)</p>
          <p>Any password works in dev mode</p>
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
}

.login-shell {
  width: min(1160px, 100%);
  display: grid;
  grid-template-columns: minmax(320px, 1.2fr) minmax(320px, 400px);
  gap: 24px;
  align-items: stretch;
}

.login-story {
  position: relative;
  padding: 40px;
  border-radius: 16px;
  background: #1e293b;
  overflow: hidden;
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
  border-radius: 12px;
  background: #0f172a;
  border: 1px solid #334155;
  overflow: hidden;
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
}

.login-console-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.login-console-metric {
  padding: 14px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid #334155;
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
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid #334155;
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
  background: #ffffff;
  border: 1px solid var(--color-border-subtle);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  padding: 36px 32px;
}

.login-header {
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 10px 0 6px;
}

.login-subtitle {
  font-size: 14px;
  color: #5f6f8c;
}

.login-form {
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
  color: #374151;
}

.form-input {
  padding: 9px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 6px;
  font-size: 14px;
  color: #1e293b;
  transition: border-color 0.15s, box-shadow 0.15s;
  background: #ffffff;
}

.form-input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.field-hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
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
  border-radius: 6px;
  font-size: 13px;
  border: 1px solid #fecaca;
}

.login-guest {
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
  color: #64748b;
  text-align: center;
  line-height: 1.5;
}

.login-hint {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-subtle);
  font-size: 12px;
  color: #7f90af;
  line-height: 1.6;
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
