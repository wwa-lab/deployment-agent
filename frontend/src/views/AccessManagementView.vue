<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AccessGrantDialog from '../components/AccessGrantDialog.vue'
import { useAccessGrantStore } from '../stores/accessGrants'
import { useUserStore } from '../stores/user'
import type { AccessGrant, AccessGrantStatus } from '../types'

const store = useAccessGrantStore()
const userStore = useUserStore()

const queryInput = ref(store.query)
const successMessage = ref('')
const dialogMode = ref<'create' | 'edit' | 'reactivate'>('create')
const dialogGrant = ref<AccessGrant | null>(null)
const dialogOpen = ref(false)
const dialogSaving = ref(false)
const dialogError = ref('')

const canManageAccess = computed(() => userStore.canManageAccess)
const totalPages = computed(() => Math.max(1, Math.ceil(store.total / store.size)))
const statusOptions: Array<'ALL' | AccessGrantStatus> = ['ALL', 'ACTIVE', 'SUSPENDED']

onMounted(() => {
  if (canManageAccess.value) {
    store.fetchGrants()
  }
})

function formatTimestamp(ts?: string): string {
  if (!ts) return '—'
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ts
  }
}

function openCreateDialog() {
  dialogMode.value = 'create'
  dialogGrant.value = null
  dialogError.value = ''
  dialogOpen.value = true
}

function openEditDialog(grant: AccessGrant) {
  dialogMode.value = 'edit'
  dialogGrant.value = grant
  dialogError.value = ''
  dialogOpen.value = true
}

function openReactivateDialog(grant: AccessGrant) {
  dialogMode.value = 'reactivate'
  dialogGrant.value = grant
  dialogError.value = ''
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
  dialogError.value = ''
}

async function submitDialog(payload: {
  employeeId: string
  grantStatus: AccessGrantStatus
  assignedRoles: AccessGrant['assignedRoles']
  scopeGrants: AccessGrant['scopeGrants']
  note?: string
}) {
  dialogSaving.value = true
  dialogError.value = ''
  successMessage.value = ''
  try {
    if (dialogMode.value === 'create') {
      await store.grantAccess(payload)
      successMessage.value = `Access granted for ${payload.employeeId}.`
    } else if (dialogMode.value === 'reactivate') {
      await store.reactivateGrant({
        employeeId: payload.employeeId,
        assignedRoles: payload.assignedRoles,
        note: payload.note,
      })
      successMessage.value = `Access reactivated for ${payload.employeeId}.`
    } else {
      await store.editGrant({
        employeeId: payload.employeeId,
        assignedRoles: payload.assignedRoles,
        note: payload.note,
      })
      successMessage.value = `Access grant updated for ${payload.employeeId}.`
    }
    closeDialog()
  } catch (e: unknown) {
    dialogError.value = e instanceof Error ? e.message : 'Failed to save access grant'
  } finally {
    dialogSaving.value = false
  }
}

async function suspendGrant(grant: AccessGrant) {
  const note = window.prompt(`Suspend access for ${grant.employeeId}? Add an optional note:`, grant.note ?? '')
  if (note === null) return

  successMessage.value = ''
  try {
    await store.suspendGrant(grant.employeeId, note.trim() || undefined)
    successMessage.value = `Access suspended for ${grant.employeeId}.`
  } catch (e: unknown) {
    store.error = e instanceof Error ? e.message : 'Failed to suspend access grant'
  }
}

function applyFilters() {
  successMessage.value = ''
  store.setQuery(queryInput.value.trim())
  store.fetchGrants()
}

function resetFilters() {
  successMessage.value = ''
  queryInput.value = ''
  store.setQuery('')
  store.setStatus('ALL')
  store.fetchGrants()
}

function onStatusChange(value: 'ALL' | AccessGrantStatus) {
  store.setStatus(value)
  store.fetchGrants()
}

function onPageChange(nextPage: number) {
  store.setPage(nextPage)
  store.fetchGrants()
}

function statusClass(status: AccessGrantStatus) {
  return status === 'ACTIVE' ? 'badge-success' : 'badge-warning'
}
</script>

<template>
  <div class="access-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">WWA Access Management</h1>
        <p class="view-subtitle">Controls platform entry and agent workspace visibility across all WWA workspaces.</p>
        <p class="view-subtitle">
          Manage who can enter Deployment Agent, what product roles they hold, and which
          `Application + SNOW Group` scopes they can view or administer.
        </p>
      </div>
      <button
        class="btn btn-primary"
        :disabled="!canManageAccess"
        :title="canManageAccess ? '' : 'Access Management is available to DEVOPS_ADMIN users.'"
        @click="openCreateDialog"
      >
        Grant Access
      </button>
    </div>

    <div
      class="helper-banner"
      :class="canManageAccess ? 'helper-banner-muted' : 'helper-banner-warn'"
    >
      <template v-if="canManageAccess">
        Access grants control product entry, while scope grants control which `Application + SNOW
        Group` data the employee can view or administer. Use this workspace to manage both without
        changing Team Book identity records.
      </template>
      <template v-else>
        Access Management is restricted to `DEVOPS_ADMIN`. The menu remains visible so teammates can
        understand the workspace model, but grant lifecycle actions are admin-only.
      </template>
    </div>

    <div v-if="successMessage" class="alert alert-success">
      {{ successMessage }}
    </div>
    <div v-if="store.error" class="alert alert-error">
      {{ store.error }}
    </div>

    <template v-if="canManageAccess">
      <div class="toolbar-card">
        <div class="toolbar-grid">
          <div class="toolbar-field toolbar-field-wide">
            <label class="toolbar-label">Employee ID / Display Name</label>
            <input
              v-model="queryInput"
              class="form-control"
              type="text"
              placeholder="Search access grants"
              @keyup.enter="applyFilters"
            />
          </div>

          <div class="toolbar-field">
            <label class="toolbar-label">Status</label>
            <select
              :value="store.status"
              class="form-control"
              @change="onStatusChange(($event.target as HTMLSelectElement).value as 'ALL' | AccessGrantStatus)"
            >
              <option v-for="option in statusOptions" :key="option" :value="option">
                {{ option }}
              </option>
            </select>
          </div>

          <div class="toolbar-actions">
            <button class="btn btn-primary" type="button" @click="applyFilters">Search</button>
            <button class="btn btn-secondary" type="button" @click="resetFilters">Reset</button>
          </div>
        </div>
      </div>

      <div v-if="store.loading && store.grants.length === 0" class="loading-state">
        <span class="spinner"></span>
        <span>Loading access grants...</span>
      </div>

      <div v-else-if="!store.loading && store.grants.length === 0" class="empty-state">
        No access grants found.
      </div>

      <div v-else class="table-container">
        <div class="table-header">
          <div>
            <h2 class="section-title">Product Access Grants</h2>
            <p class="section-subtitle">
              Effective Deployment Agent access, sorted by latest admin change.
            </p>
          </div>
        </div>

        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Status</th>
                <th>Roles</th>
                <th>Scopes</th>
                <th>Last Login</th>
                <th>Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="grant in store.grants" :key="grant.employeeId">
                <td>
                  <div class="primary-cell">{{ grant.displayName }}</div>
                  <div class="cell-meta mono">{{ grant.employeeId }}</div>
                  <div v-if="grant.note" class="cell-meta">{{ grant.note }}</div>
                </td>
                <td>
                  <span class="status-badge" :class="statusClass(grant.grantStatus)">
                    {{ grant.grantStatus }}
                  </span>
                </td>
                <td>
                  <div class="role-stack">
                    <span v-for="role in grant.assignedRoles" :key="role" class="role-pill">
                      {{ role }}
                    </span>
                    <span v-if="grant.assignedRoles.length === 0" class="cell-meta">No roles assigned</span>
                  </div>
                </td>
                <td>
                  <div v-if="grant.scopeGrants.length > 0" class="scope-stack">
                    <span
                      v-for="scope in grant.scopeGrants"
                      :key="`${scope.application}-${scope.snowGroup}`"
                      class="scope-pill"
                    >
                      {{ scope.application }} / {{ scope.snowGroup }}
                    </span>
                  </div>
                  <div v-else class="cell-meta">Global access</div>
                </td>
                <td>{{ formatTimestamp(grant.lastLoginAt) }}</td>
                <td>
                  <div>{{ formatTimestamp(grant.updatedAt) }}</div>
                  <div class="cell-meta">{{ grant.updatedBy || '—' }}</div>
                </td>
                <td>
                  <div class="action-row">
                    <button class="btn btn-secondary btn-sm" @click="openEditDialog(grant)">Edit</button>
                    <button
                      v-if="grant.grantStatus === 'ACTIVE'"
                      class="btn btn-danger btn-sm"
                      @click="suspendGrant(grant)"
                    >
                      Suspend
                    </button>
                    <button
                      v-else
                      class="btn btn-primary btn-sm"
                      @click="openReactivateDialog(grant)"
                    >
                      Reactivate
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="pagination-info">
            {{ store.total }} total | Page {{ store.page + 1 }} of {{ totalPages }}
          </span>
          <div class="pagination-controls">
            <button
              class="btn btn-secondary btn-sm"
              :disabled="store.page === 0"
              @click="onPageChange(store.page - 1)"
            >
              Prev
            </button>
            <button
              class="btn btn-secondary btn-sm"
              :disabled="store.page >= totalPages - 1"
              @click="onPageChange(store.page + 1)"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </template>

    <AccessGrantDialog
      v-if="dialogOpen"
      :mode="dialogMode"
      :grant="dialogGrant"
      :saving="dialogSaving"
      :error="dialogError"
      @close="closeDialog"
      @save="submitDialog"
    />
  </div>
</template>

<style scoped>
.access-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.view-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.view-subtitle {
  margin: 8px 0 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
}

.helper-banner {
  padding: 14px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  border: 1px solid #e2e8f0;
}

.helper-banner-muted {
  color: #475569;
  background: #f8fafc;
}

.helper-banner-warn {
  color: #854d0e;
  background: #fff7ed;
  border-color: #fdba74;
}

.toolbar-card,
.table-container {
  padding: 18px 20px;
  border-radius: 16px;
  background: white;
  border: 1px solid #e2e8f0;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
}

.toolbar-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(180px, 0.8fr) auto;
  gap: 14px;
  align-items: end;
}

.toolbar-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toolbar-field-wide {
  min-width: 0;
}

.toolbar-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.loading-state,
.empty-state {
  padding: 28px 24px;
  border-radius: 16px;
  background: white;
  border: 1px dashed #cbd5e1;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.section-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.table-scroll {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: top;
  font-size: 14px;
}

.data-table th {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.primary-cell {
  font-weight: 600;
  color: #0f172a;
}

.cell-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.status-badge,
.role-pill,
.scope-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.badge-success {
  background: #dcfce7;
  color: #166534;
}

.badge-warning {
  background: #fef3c7;
  color: #92400e;
}

.role-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-pill {
  background: #eff6ff;
  color: #1d4ed8;
}

.scope-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scope-pill {
  background: #f1f5f9;
  color: #334155;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.pagination-info {
  font-size: 13px;
  color: #64748b;
}

.pagination-controls {
  display: flex;
  gap: 8px;
}

@media (max-width: 900px) {
  .view-header {
    flex-direction: column;
  }

  .toolbar-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
