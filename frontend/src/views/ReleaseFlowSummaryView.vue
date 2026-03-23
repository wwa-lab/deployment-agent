<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useReleaseFlowStore } from '../stores/releaseFlow'
import UploadDialog from '../components/UploadDialog.vue'
import type { FlowStatus, RequestStatus, Stage } from '../types'

const router = useRouter()
const store = useReleaseFlowStore()

const showUpload = ref(false)

const flowStatuses: FlowStatus[] = ['Pending', 'Running', 'Completed', 'Failed', 'Rejected']
const stages: Stage[] = ['SIT', 'UAT', 'PROD']

onMounted(() => {
  store.fetchList()
  store.startPolling()
})

onUnmounted(() => {
  store.stopPolling()
})

function onFilterChange(key: 'project' | 'status' | 'stage', value: string) {
  store.setFilter(key, value || undefined)
  store.fetchList()
}

function goToDetail(id: string) {
  router.push(`/wwa/deployment-agent/release-flows/${id}`)
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

function statusLabel(status: string) {
  const labelMap: Record<string, string> = {
    Completed: 'Done',
    Pending_Review: 'Pending Review',
    Ready_For_Execution: 'Ready',
  }

  return labelMap[status] ?? status.replaceAll('_', ' ')
}

function stageStatus(flow: { sitStatus: RequestStatus; uatStatus: RequestStatus; prodStatus: RequestStatus }, stage: Stage) {
  const stageMap: Record<Stage, RequestStatus> = {
    SIT: flow.sitStatus,
    UAT: flow.uatStatus,
    PROD: flow.prodStatus,
  }
  return stageMap[stage]
}

const totalPages = computed(() => Math.max(1, Math.ceil(store.total / store.size)))
</script>

<template>
  <div class="summary-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Agent</p>
        <h1 class="view-title">Deployment Agent</h1>
        <p class="view-subtitle">Track release flows, upload deployment files, and monitor stage progress.</p>
      </div>
      <button class="btn btn-primary" @click="showUpload = true">+ Upload</button>
    </div>

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
        <label class="form-label">Stage</label>
        <select
          class="form-control"
          :value="store.filters.stage ?? ''"
          @change="onFilterChange('stage', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">All</option>
          <option v-for="s in stages" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="store.loading && store.list.length === 0" class="loading-state">
      <span class="spinner"></span>
      <span>Loading release flows...</span>
    </div>

    <!-- Empty state -->
    <div v-else-if="!store.loading && store.list.length === 0" class="empty-state">
      <p>No release flows found.</p>
      <p style="margin-top: 8px; font-size: 12px;">Upload a release file to get started.</p>
    </div>

    <!-- Table -->
    <div v-else class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>Project</th>
            <th>Release ID</th>
            <th v-for="stage in stages" :key="stage" class="stage-column">{{ stage }}</th>
            <th>Overall Status</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="flow in store.list"
            :key="flow.id"
            class="clickable"
            @click="goToDetail(flow.id)"
          >
            <td>{{ flow.projectName }}</td>
            <td class="release-id">{{ flow.releaseId }}</td>
            <td v-for="stage in stages" :key="`${flow.id}-${stage}`" class="stage-column">
              <span class="badge" :class="statusBadgeClass(stageStatus(flow, stage))">
                {{ statusLabel(stageStatus(flow, stage)) }}
              </span>
            </td>
            <td>
              <span class="badge" :class="statusBadgeClass(flow.flowStatus)">
                {{ statusLabel(flow.flowStatus) }}
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
    <UploadDialog v-if="showUpload" @close="showUpload = false" />
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

.stage-column {
  text-align: center;
  white-space: nowrap;
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
