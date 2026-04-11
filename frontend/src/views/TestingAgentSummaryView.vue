<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useTestingAgentReleaseFlowStore } from '../stores/testingAgentReleaseFlow'
import { useUserStore } from '../stores/user'
import UploadDialog from '../components/UploadDialog.vue'
import { uploadFile, downloadTemplate } from '../api/testingAgentUpload'
import type { FlowStatus, ReleaseFlowListItem, RequestStatus, Stage } from '../types'

const router = useRouter()
const store = useTestingAgentReleaseFlowStore()
const userStore = useUserStore()

const showUpload = ref(false)

const flowStatuses: FlowStatus[] = ['Pending', 'Running', 'Completed', 'Failed', 'Rejected']
const stages: Stage[] = ['UAT']
const attemptViews = [
  { value: 'latest', label: 'Latest Attempt' },
  { value: 'history', label: 'Include History' },
] as const

onMounted(() => {
  if (!userStore.isDevOpsAdmin && store.filters.includeArchived) {
    store.setFilter('includeArchived', undefined)
  }
  store.fetchList()
  store.startPolling()
})

onUnmounted(() => {
  store.stopPolling()
})

function onFilterChange(
  key: 'project' | 'status' | 'stage' | 'application' | 'snowGroup' | 'agent' | 'attemptView',
  value: string,
) {
  store.setFilter(key, value || undefined)
  store.fetchList()
}

function goToDetail(flow: ReleaseFlowListItem) {
  const linked = flow.linkedReleaseFlowIds.length > 1 ? flow.linkedReleaseFlowIds.join(',') : undefined
  router.push({
    path: `/wwa/testing-agent/release-flows/${flow.id}`,
    query: {
      ...(showArchived.value ? { archived: '1' } : {}),
      ...(linked ? { linked } : {}),
    },
  })
}

function onPageChange(newPage: number) {
  store.setPage(newPage)
  store.fetchList()
}

function statusBadgeClass(status: string) {
  const map: Record<string, string> = {
    Pending: 'badge-pending',
    Running: 'badge-running',
    Completed: 'badge-completed',
    Failed: 'badge-failed',
    Rejected: 'badge-rejected',
    Pending_Review: 'badge-pending-review',
    Approved: 'badge-approved',
    Skipped: 'badge-skipped',
  }
  return map[status] ?? 'badge-pending'
}

function archiveBadgeClass(archivedAt?: string) {
  return archivedAt ? 'badge-rejected' : 'badge-pending'
}

function statusLabel(status: string) {
  const labelMap: Record<string, string> = {
    Completed: 'Done',
    Pending_Review: 'Pending Review',
    Ready_For_Execution: 'Ready',
  }

  return labelMap[status] ?? status.replaceAll('_', ' ')
}

function stageStatus(flow: ReleaseFlowListItem, stage: Stage): RequestStatus {
  return flow.stageStatuses?.[stage] ?? 'Pending'
}

function stagePresent(flow: ReleaseFlowListItem, stage: Stage) {
  return flow.stagesPresent?.includes(stage) ?? false
}

const totalPages = computed(() => Math.max(1, Math.ceil(store.total / store.size)))
const showArchived = computed(() => store.filters.includeArchived === true)
const uploadScope = computed(() => ({
  application: store.filters.application,
  snowGroup: store.filters.snowGroup,
  agent: store.filters.agent,
}))

function scopeSummary(flow: {
  application?: string
  snowGroup?: string
  agent?: string
  projectName: string
}) {
  return {
    application: flow.application || flow.projectName,
    snowGroup: flow.snowGroup || '—',
    agent: flow.agent || '—',
  }
}

function rundownOwnerLabel(owner?: string) {
  return owner?.trim() || '—'
}

function linkedReleaseLabel(flow: ReleaseFlowListItem) {
  if (flow.linkedReleaseCount <= 1) return null
  return `${flow.linkedReleaseCount} linked uploads`
}

function toggleArchivedVisibility() {
  if (!userStore.isDevOpsAdmin) return
  store.setFilter('includeArchived', showArchived.value ? undefined : true)
  store.fetchList()
}
</script>

<template>
  <div class="summary-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Workspace</p>
        <h1 class="view-title">Testing Agent</h1>
        <p class="view-subtitle">Track iSeries A/B testing rundowns by program level with human-in-the-loop controls.</p>
      </div>
      <div class="header-actions">
        <button
          v-if="userStore.isDevOpsAdmin"
          class="btn btn-secondary"
          type="button"
          @click="toggleArchivedVisibility"
        >
          {{ showArchived ? 'Hide Archived' : 'Show Archived' }}
        </button>
        <button
          class="btn btn-primary"
          :disabled="!userStore.canUploadRelease"
          :title="userStore.canUploadRelease ? '' : 'Upload is available to DEVELOPER, TL, and DEVOPS_ADMIN.'"
          @click="userStore.canUploadRelease && (showUpload = true)"
        >
          + Upload
        </button>
      </div>
    </div>

    <section class="wwa-intro-card" aria-labelledby="wwa-testing-intro-title">
      <div class="wwa-intro-kicker">WWA Today</div>
      <h2 id="wwa-testing-intro-title" class="wwa-intro-title">iSeries A/B testing with human control</h2>
      <p class="wwa-intro-text">
        WWA currently supports a Testing Agent workspace. Testing Agent is the A/B testing
        workspace for iSeries programs, enabling controlled rundowns by program level
        with human-in-the-loop sign-off at every step.
      </p>
    </section>

    <!-- Filter bar -->
    <div class="filter-bar card">
      <div class="filter-group">
        <label class="form-label">Project</label>
        <input
          class="form-control"
          type="text"
          placeholder="Filter by project..."
          :value="store.filters.project ?? ''"
          @input="onFilterChange('project', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="filter-group">
        <label class="form-label">Status</label>
        <select
          class="form-control"
          :value="store.filters.status ?? ''"
          @change="onFilterChange('status', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">All</option>
          <option v-for="s in flowStatuses" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label class="form-label">Application</label>
        <input
          class="form-control"
          type="text"
          placeholder="Filter by application..."
          :value="store.filters.application ?? ''"
          @input="onFilterChange('application', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="filter-group">
        <label class="form-label">SNOW Group</label>
        <input
          class="form-control"
          type="text"
          placeholder="Filter by SNOW Group..."
          :value="store.filters.snowGroup ?? ''"
          @input="onFilterChange('snowGroup', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="filter-group">
        <label class="form-label">Agent</label>
        <input
          class="form-control"
          type="text"
          placeholder="Filter by agent..."
          :value="store.filters.agent ?? ''"
          @input="onFilterChange('agent', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="filter-group">
        <label class="form-label">Stage</label>
        <input class="form-control" type="text" value="UAT" disabled />
      </div>
      <div class="filter-group">
        <label class="form-label">Attempt View</label>
        <select
          class="form-control"
          :value="store.filters.attemptView ?? 'latest'"
          @change="onFilterChange('attemptView', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="mode in attemptViews" :key="mode.value" :value="mode.value">
            {{ mode.label }}
          </option>
        </select>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="store.loading && store.list.length === 0" class="loading-state">
      <span class="spinner"></span>
      <span>Loading workflows...</span>
    </div>

    <!-- Empty state -->
    <div v-else-if="!store.loading && store.list.length === 0" class="empty-state">
      <p>No workflows found.</p>
      <p style="margin-top: 8px; font-size: 12px;">Upload a workflow file to get started.</p>
    </div>

    <!-- Table -->
    <div v-else class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>Project</th>
            <th>Workflow ID</th>
            <th>Scope</th>
            <th>Rundown Owner</th>
            <th>Current Stage</th>
            <th v-for="stage in stages" :key="stage" class="stage-column">{{ stage }}</th>
            <th>Overall Status</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="flow in store.list"
            :key="flow.id"
            class="clickable"
            :class="{ 'row-archived': !!flow.archivedAt }"
            @click="goToDetail(flow)"
          >
            <td>{{ flow.projectName }}</td>
            <td>
              <div class="release-id">{{ flow.releaseId }}</div>
              <div v-if="linkedReleaseLabel(flow)" class="release-linkage">
                {{ linkedReleaseLabel(flow) }}
              </div>
              <span v-if="flow.archivedAt" class="badge badge-rejected archive-chip">Archived</span>
            </td>
            <td class="scope-cell">
              <div class="scope-primary">{{ scopeSummary(flow).application }}</div>
              <div class="scope-meta">SNOW: {{ scopeSummary(flow).snowGroup }}</div>
              <div class="scope-meta">Agent: {{ scopeSummary(flow).agent }}</div>
            </td>
            <td class="owner-cell">{{ rundownOwnerLabel(flow.owner) }}</td>
            <td class="stage-column">
              <span class="badge badge-running">{{ flow.currentStage }}</span>
            </td>
            <td v-for="stage in stages" :key="`${flow.id}-${stage}`" class="stage-column">
              <span v-if="stagePresent(flow, stage)" class="badge" :class="statusBadgeClass(stageStatus(flow, stage))">
                {{ statusLabel(stageStatus(flow, stage)) }}
              </span>
              <span v-else class="stage-empty">—</span>
            </td>
            <td>
              <span class="badge" :class="statusBadgeClass(flow.flowStatus)">
                {{ statusLabel(flow.flowStatus) }}
              </span>
              <span
                v-if="flow.archivedAt"
                class="badge archive-status-chip"
                :class="archiveBadgeClass(flow.archivedAt)"
              >
                Archived
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Pagination -->
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

    <!-- Upload dialog -->
    <UploadDialog
      v-if="showUpload"
      :initial-scope="uploadScope"
      :allowed-stages="['UAT']"
      :upload-fn="uploadFile"
      :download-template-fn="downloadTemplate"
      :on-upload-success="store.fetchList"
      @close="showUpload = false"
    />
  </div>
</template>

<style scoped>
.summary-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.view-title {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.view-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: #475569;
}

.filter-bar {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
  padding: 14px 16px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 160px;
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  overflow: hidden;
}

.release-id {
  font-family: monospace;
  font-size: 13px;
  color: #2563eb;
}

.release-linkage {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.scope-cell {
  min-width: 220px;
}

.owner-cell {
  min-width: 140px;
  white-space: nowrap;
  font-weight: 600;
  color: #334155;
}

.scope-primary {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.scope-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.archive-chip,
.archive-status-chip {
  margin-top: 6px;
}

.row-archived {
  opacity: 0.72;
}

.stage-column {
  text-align: center;
  white-space: nowrap;
}

.stage-empty {
  color: #94a3b8;
  font-weight: 600;
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
  color: #64748b;
}

.pagination-controls {
  display: flex;
  gap: 8px;
}
</style>
