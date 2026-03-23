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
    router.push('/wwa/deployment-agent')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">WWA</h1>
        <p class="login-subtitle">Sign in with your team book credentials</p>
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

        <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
          {{ loading ? 'Signing in...' : 'Sign In' }}
        </button>
      </form>

      <div class="login-hint">
        <p>Dev accounts: emp-001 (Developer), emp-002 (TL), emp-003 (DevOps), emp-004 (Audit), emp-005 (Mgmt)</p>
        <p>Any password works in dev mode</p>
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
  background: #f1f5f9;
}

.login-card {
  width: 400px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  padding: 40px 32px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 6px;
}

.login-subtitle {
  font-size: 14px;
  color: #64748b;
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
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  color: #1e293b;
  transition: border-color 0.15s;
}

.form-input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.field-hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}

.btn-full {
  width: 100%;
  padding: 10px;
  margin-top: 4px;
}

.login-error {
  background: #fef2f2;
  color: #dc2626;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  border: 1px solid #fecaca;
}

.login-hint {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  line-height: 1.6;
}
</style>
