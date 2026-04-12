<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuditStore } from '../stores/audit'
import type { AuditLogEntry } from '../types'

const store = useAuditStore()

// Platform-level action types: access governance events (WWA-010)
const PLATFORM_ACTION_TYPES = new Set([
  'access_grant_create',
  'access_grant_update',
  'access_grant_suspend',
  'access_grant_reactivate',
])

type ScopeFilter = 'all' | 'platform' | 'agent'
const scopeFilter = ref<ScopeFilter>('all')

const filteredLogs = computed<AuditLogEntry[]>(() => {
  if (scopeFilter.value === 'platform') {
    return store.logs.filter((l) => PLATFORM_ACTION_TYPES.has(l.actionType))
  }
  if (scopeFilter.value === 'agent') {
    return store.logs.filter((l) => !PLATFORM_ACTION_TYPES.has(l.actionType))
  }
  return store.logs
})

const operatorQuery = ref(store.operatorId)
const applicationQuery = ref(store.application)
const snowGroupQuery = ref(store.snowGroup)
const agentQuery = ref(store.agent)

onMounted(() => {
  store.fetchLogs()
})

const totalPages = computed(() => Math.max(1, Math.ceil(store.total / store.size)))

function formatTimestamp(ts: string): string {
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ts
  }
}

function onPageChange(newPage: number) {
  store.setPage(newPage)
  store.fetchLogs()
}

function applySearch() {
  store.setOperatorId(operatorQuery.value.trim())
  store.setScopeFilters({
    application: applicationQuery.value.trim(),
    snowGroup: snowGroupQuery.value.trim(),
    agent: agentQuery.value.trim(),
  })
  store.fetchLogs()
}

function resetSearch() {
  operatorQuery.value = ''
  applicationQuery.value = ''
  snowGroupQuery.value = ''
  agentQuery.value = ''
  store.setOperatorId('')
  store.setScopeFilters({
    application: '',
    snowGroup: '',
    agent: '',
  })
  store.fetchLogs()
}

function actionLabel(log: AuditLogEntry): string {
  if (log.actionType === 'view_result' && log.contextPayload?.action === 'record_result') {
    return 'Record Result'
  }

  const labels: Record<string, string> = {
    upload: 'Upload',
    edit: 'Edit',
    view_result: 'View Result',
    approve: 'Approve',
    reject: 'Reject',
    rerun: 'Rerun',
    skip: 'Skip',
    config_update: 'Config Update',
    auto_submit: 'Auto Submit',
    request_start: 'Start Workflow',
    request_fail: 'Mark as Failed',
    access_grant_create: 'Access Granted',
    access_grant_update: 'Access Updated',
    access_grant_suspend: 'Access Suspended',
    access_grant_reactivate: 'Access Reactivated',
  }

  return labels[log.actionType] ?? log.actionType
}

function resultBadge(log: AuditLogEntry): { label: string; tone: 'success' | 'fail' | 'neutral' } {
  if (log.actionType === 'request_fail' || log.actionType === 'reject') {
    return { label: 'Fail', tone: 'fail' }
  }

  if (log.actionType === 'skip') {
    return { label: 'Skipped', tone: 'neutral' }
  }

  if (log.actionType === 'auto_submit') {
    const submissionStatus = String(log.contextPayload?.submissionStatus ?? '').toUpperCase()
    if (submissionStatus === 'FAILED') {
      return { label: 'Fail', tone: 'fail' }
    }
    if (submissionStatus) {
      return { label: 'Success', tone: 'success' }
    }
  }

  return { label: 'Success', tone: 'success' }
}

function compactJson(value: unknown): string {
  if (value === undefined || value === null) {
    return ''
  }

  try {
    const raw = JSON.stringify(value)
    if (!raw) return ''
    return raw.length > 180 ? `${raw.slice(0, 177)}...` : raw
  } catch {
    return String(value)
  }
}

function detailText(log: AuditLogEntry): string {
  const payload = log.contextPayload ?? {}

  switch (log.actionType) {
    case 'upload':
      return `Stage: ${payload.stage ?? '—'} | Imported tasks: ${payload.taskCount ?? '—'}`
    case 'edit':
      if (payload.transitionFrom || payload.transitionTo) {
        return `Status changed from ${payload.transitionFrom ?? '—'} to ${payload.transitionTo ?? '—'}`
      }
      if (payload.fieldChanged) {
        return `Updated ${String(payload.fieldChanged)}`
      }
      return payload.configKey ? `Updated ${String(payload.configKey)}` : compactJson(payload)
    case 'view_result':
      return payload.action === 'record_result'
        ? `Recorded manual result${payload.attemptNumber ? ` (attempt ${payload.attemptNumber})` : ''}`
        : compactJson(payload)
    case 'approve':
    case 'reject':
    case 'rerun':
    case 'skip':
      return `Decision: ${String(payload.decisionType ?? actionLabel(log))}${
        payload.comment ? ` | Comment: ${String(payload.comment)}` : ''
      }`
    case 'config_update':
      return `Config item: ${String(payload.configKey ?? '—')}`
    case 'auto_submit':
      return `System: ${String(payload.systemType ?? '—')} | Attempt: ${String(payload.attemptNumber ?? '—')}${
        payload.externalJobUrl ? ' | External job link recorded' : ''
      }`
    case 'request_start':
    case 'request_fail':
      return `Stage: ${String(payload.stage ?? '—')}`
    case 'access_grant_create':
    case 'access_grant_update':
    case 'access_grant_suspend':
    case 'access_grant_reactivate':
      return `Employee: ${String(payload.employeeId ?? '—')}${
        payload.newGrantStatus ? ` | Status: ${String(payload.newGrantStatus)}` : ''
      }${
        payload.newAssignedRoles ? ` | Roles: ${String(payload.newAssignedRoles)}` : ''
      }`
    default:
      return compactJson(payload) || 'No additional detail'
  }
}

function detailMeta(log: AuditLogEntry): string {
  const refs: string[] = []
  if (log.releaseFlowId) refs.push(`Flow ${log.releaseFlowId}`)
  if (log.requestId) refs.push(`Request ${log.requestId}`)
  if (log.taskId) refs.push(`Task ${log.taskId}`)
  return refs.join(' | ')
}

function scopeValue(value?: string): string {
  return value && value.trim().length > 0 ? value : '—'
}
</script>

<template>
  <div class="audit-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">Audit Log</h1>
        <p class="view-subtitle">
          Trace who did what, when, and with what detail.
        </p>
      </div>
    </div>

    <div class="scope-tabs">
      <button
        class="scope-tab"
        :class="{ active: scopeFilter === 'all' }"
        type="button"
        @click="scopeFilter = 'all'"
      >All Events</button>
      <button
        class="scope-tab"
        :class="{ active: scopeFilter === 'platform' }"
        type="button"
        @click="scopeFilter = 'platform'"
      >Platform Events</button>
      <button
        class="scope-tab"
        :class="{ active: scopeFilter === 'agent' }"
        type="button"
        @click="scopeFilter = 'agent'"
      >Agent Activity</button>
    </div>

    <div class="helper-banner helper-banner-muted">
      Read-only. Use task-level Activity dialogs for step-by-step execution history.
    </div>

    <div class="toolbar-card">
        <div class="toolbar-grid">
          <div class="toolbar-field">
            <label class="toolbar-label">Operator / Staff ID</label>
            <input
              v-model="operatorQuery"
            class="form-control"
            type="text"
            placeholder="Search by operator id"
              @keyup.enter="applySearch"
            />
          </div>

          <div class="toolbar-field">
            <label class="toolbar-label">Application</label>
            <input
              v-model="applicationQuery"
              class="form-control"
              type="text"
              placeholder="Filter by application"
              @keyup.enter="applySearch"
            />
          </div>

          <div class="toolbar-field">
            <label class="toolbar-label">SNOW Group</label>
            <input
              v-model="snowGroupQuery"
              class="form-control"
              type="text"
              placeholder="Filter by SNOW Group"
              @keyup.enter="applySearch"
            />
          </div>

          <div class="toolbar-field">
            <label class="toolbar-label">Agent</label>
            <input
              v-model="agentQuery"
              class="form-control"
              type="text"
              placeholder="Filter by agent"
              @keyup.enter="applySearch"
            />
          </div>

          <div class="toolbar-actions">
            <button class="btn btn-primary" type="button" @click="applySearch">Search</button>
            <button class="btn btn-secondary" type="button" @click="resetSearch">Reset</button>
          </div>
      </div>
    </div>

    <div v-if="store.error" class="alert alert-error">
      {{ store.error }}
    </div>

    <div v-if="store.loading && store.logs.length === 0" class="loading-state">
      <span class="spinner"></span>
      <span>Loading audit logs...</span>
    </div>

    <div v-else-if="!store.loading && store.logs.length === 0" class="empty-state">
      No audit logs found.
    </div>

    <div v-else class="table-container">
      <div class="table-header">
        <div>
          <h2 class="section-title">Audit Records</h2>
          <p class="section-subtitle">
            Latest action records.
          </p>
        </div>
      </div>

      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Time</th>
              <th>Type</th>
              <th>Scope</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in filteredLogs" :key="log.id">
              <td>
                <div class="user-name">{{ log.operatorId }}</div>
                <div class="cell-meta">{{ log.operatorRole }}</div>
              </td>
              <td class="timestamp">{{ formatTimestamp(log.timestamp) }}</td>
              <td>
                <div class="type-cell">
                  <span class="type-label">{{ actionLabel(log) }}</span>
                  <span class="result-badge" :class="`result-${resultBadge(log).tone}`">
                    {{ resultBadge(log).label }}
                  </span>
                </div>
                <div class="cell-meta mono">{{ log.actionType }}</div>
              </td>
              <td class="scope-cell">
                <div class="scope-line">
                  <span class="scope-label">App</span>
                  <span>{{ scopeValue(log.application) }}</span>
                </div>
                <div class="scope-line">
                  <span class="scope-label">Group</span>
                  <span>{{ scopeValue(log.snowGroup) }}</span>
                </div>
                <div class="scope-line">
                  <span class="scope-label">Agent</span>
                  <span>{{ scopeValue(log.agent) }}</span>
                </div>
              </td>
              <td class="detail-cell">
                <div class="detail-text">{{ detailText(log) }}</div>
                <div v-if="detailMeta(log)" class="cell-meta mono">{{ detailMeta(log) }}</div>
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
  </div>
</template>

<style scoped>
.audit-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scope-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0;
}

.scope-tab {
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}

.scope-tab:hover {
  color: var(--color-text-primary);
}

.scope-tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.view-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.view-subtitle {
  margin: 8px 0 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.helper-banner {
  padding: 14px 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
  border: 1px solid #dbeafe;
  color: #1e3a8a;
  font-size: 14px;
  line-height: 1.6;
}

.helper-banner-muted {
  color: var(--color-text-secondary);
  background: var(--color-surface-secondary);
  border-color: #e2e8f0;
}

.toolbar-card,
.table-container {
  background: white;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.toolbar-card {
  padding: 18px;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: 16px;
  align-items: end;
}

.toolbar-field {
  min-width: 0;
}

.toolbar-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.table-container {
  overflow: hidden;
}

.table-header {
  padding: 18px 18px 12px;
}

.section-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.section-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.table-scroll {
  overflow-x: auto;
}

.user-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.timestamp {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.type-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.type-label {
  font-weight: 600;
  color: var(--color-text-primary);
}

.result-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.result-success {
  color: #166534;
  background: #dcfce7;
}

.result-fail {
  color: #991b1b;
  background: #fee2e2;
}

.result-neutral {
  color: #475569;
  background: #e2e8f0;
}

.detail-cell {
  max-width: 520px;
}

.scope-cell {
  min-width: 220px;
}

.scope-line {
  display: flex;
  gap: 8px;
  align-items: baseline;
  line-height: 1.5;
  color: var(--color-text-primary);
}

.scope-line + .scope-line {
  margin-top: 4px;
}

.scope-label {
  min-width: 44px;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.detail-text {
  color: var(--color-text-primary);
  line-height: 1.6;
}

.cell-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.mono {
  font-family: var(--font-mono);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
}

.pagination-info {
  font-size: 13px;
  color: var(--color-text-muted);
}

.pagination-controls {
  display: flex;
  gap: 8px;
}

@media (max-width: 900px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .view-header {
    align-items: flex-start;
  }

  .pagination {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
