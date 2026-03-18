<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReleaseFlowStore } from '../stores/releaseFlow'
import { useUserStore } from '../stores/user'
import { getTaskResult, submitAutoExecution } from '../api/tasks'
import TaskEditDialog from '../components/TaskEditDialog.vue'
import RecordResultDialog from '../components/RecordResultDialog.vue'
import DecisionDialog from '../components/DecisionDialog.vue'
import type { Task, TaskResult, Request } from '../types'

const route = useRoute()
const router = useRouter()
const store = useReleaseFlowStore()
const userStore = useUserStore()

const flowId = computed(() => route.params.id as string)

const editingTask = ref<Task | null>(null)
const recordingTask = ref<Task | null>(null)
const decidingTask = ref<Task | null>(null)

const viewingResult = ref<{ task: Task; result: TaskResult | null; loading: boolean } | null>(null)

onMounted(async () => {
  await store.selectFlow(flowId.value)
})

function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    Pending: 'badge-pending',
    Running: 'badge-running',
    Executing: 'badge-executing',
    Completed: 'badge-completed',
    Failed: 'badge-failed',
    Rejected: 'badge-rejected',
    Approved: 'badge-approved',
    Awaiting_Review: 'badge-awaiting-review',
    Skipped: 'badge-skipped',
    Ready_For_Execution: 'badge-ready-for-execution',
    Pending_Review: 'badge-pending-review',
  }
  return map[status] ?? 'badge-pending'
}

function executionTypeBadgeClass(type: string): string {
  return type === 'MANUAL' ? 'badge-manual' : 'badge-auto'
}

function canEdit(task: Task): boolean {
  return (
    userStore.isTL &&
    (task.taskStatus === 'Pending' || task.taskStatus === 'Ready_For_Execution')
  )
}

function canRecordResult(task: Task): boolean {
  return (
    userStore.isTL &&
    task.executionType === 'MANUAL' &&
    task.taskStatus === 'Ready_For_Execution'
  )
}

function canDecide(task: Task): boolean {
  return userStore.isTL && task.taskStatus === 'Awaiting_Review'
}

function canSubmitAuto(task: Task): boolean {
  return (
    (userStore.isTL || userStore.isDevOpsAdmin) &&
    task.executionType === 'AUTO' &&
    task.taskStatus === 'Ready_For_Execution'
  )
}

const submittingAuto = ref<string | null>(null)

async function handleSubmitAuto(task: Task) {
  submittingAuto.value = task.id
  try {
    await submitAutoExecution(task.id)
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    submittingAuto.value = null
  }
}

function canViewResult(task: Task): boolean {
  return !!task.latestExecutionId
}

async function openViewResult(task: Task) {
  viewingResult.value = { task, result: null, loading: true }
  try {
    const result = await getTaskResult(task.id, task.latestExecutionId)
    viewingResult.value = { task, result, loading: false }
  } catch {
    viewingResult.value = { task, result: null, loading: false }
  }
}

async function onTaskSaved() {
  editingTask.value = null
  recordingTask.value = null
  await store.refreshDetail()
}

async function onDecisionMade() {
  decidingTask.value = null
  await store.refreshDetail()
}

function activeStageIndex(requests: Request[]): number {
  const detail = store.detail
  if (!detail) return 0
  const idx = requests.findIndex((r) => r.stage === detail.currentStage)
  return idx >= 0 ? idx : 0
}

const activeTab = ref(0)

// Watch for detail load
watch(() => store.detail, (val) => {
  if (val) {
    activeTab.value = activeStageIndex(val.requests)
  }
})
</script>

<template>
  <div class="detail-view">
    <!-- Back navigation -->
    <div class="nav-back">
      <button class="btn btn-secondary btn-sm" @click="router.push('/release-flows')">
        ← Back
      </button>
    </div>

    <!-- Loading -->
    <div v-if="store.loading && !store.detail" class="loading-state">
      <span class="spinner"></span>
      <span>Loading release flow...</span>
    </div>

    <template v-else-if="store.detail">
      <!-- Header -->
      <div class="detail-header card">
        <div class="header-row">
          <div class="header-field">
            <span class="field-label">Project</span>
            <span class="field-value">{{ store.detail.projectName }}</span>
          </div>
          <div class="header-field">
            <span class="field-label">Release ID</span>
            <span class="field-value mono">{{ store.detail.releaseId }}</span>
          </div>
          <div class="header-field">
            <span class="field-label">Current Stage</span>
            <span class="badge badge-pending">{{ store.detail.currentStage }}</span>
          </div>
          <div class="header-field">
            <span class="field-label">Flow Status</span>
            <span class="badge" :class="statusBadgeClass(store.detail.flowStatus)">
              {{ store.detail.flowStatus }}
            </span>
          </div>
          <div class="header-field">
            <span class="field-label">Review Status</span>
            <span class="badge" :class="statusBadgeClass(store.detail.reviewStatus)">
              {{ store.detail.reviewStatus }}
            </span>
          </div>
        </div>
      </div>

      <!-- Stage tabs -->
      <div v-if="store.detail.requests.length > 0" class="requests-section">
        <div class="tabs">
          <button
            v-for="(req, idx) in store.detail.requests"
            :key="req.id"
            class="tab-btn"
            :class="{ active: activeTab === idx }"
            @click="activeTab = idx"
          >
            {{ req.stage }}
            <span class="badge" :class="statusBadgeClass(req.requestStatus)" style="margin-left:6px;font-size:11px">
              {{ req.requestStatus }}
            </span>
          </button>
        </div>

        <!-- Task table for active tab -->
        <div
          v-for="(req, idx) in store.detail.requests"
          :key="req.id"
          v-show="activeTab === idx"
        >
          <div v-if="req.tasks.length === 0" class="empty-state">No tasks in this request.</div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Task Group</th>
                <th>Step</th>
                <th>Task Name</th>
                <th>Type</th>
                <th>Status</th>
                <th>Owner</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in req.tasks" :key="task.id">
                <td>{{ task.taskGroupName }}</td>
                <td>{{ task.stepSeq }}</td>
                <td>{{ task.taskName }}</td>
                <td>
                  <span class="badge" :class="executionTypeBadgeClass(task.executionType)">
                    {{ task.executionType }}
                  </span>
                </td>
                <td>
                  <span class="badge" :class="statusBadgeClass(task.taskStatus)">
                    {{ task.taskStatus }}
                  </span>
                </td>
                <td>{{ task.owner ?? '—' }}</td>
                <td>
                  <div class="action-btns">
                    <button
                      v-if="canEdit(task)"
                      class="btn btn-secondary btn-sm"
                      @click.stop="editingTask = task"
                    >
                      Edit
                    </button>
                    <button
                      v-if="canRecordResult(task)"
                      class="btn btn-primary btn-sm"
                      @click.stop="recordingTask = task"
                    >
                      Record Result
                    </button>
                    <button
                      v-if="canSubmitAuto(task)"
                      class="btn btn-primary btn-sm"
                      :disabled="submittingAuto === task.id"
                      @click.stop="handleSubmitAuto(task)"
                    >
                      {{ submittingAuto === task.id ? 'Submitting...' : 'Submit Auto' }}
                    </button>
                    <button
                      v-if="canViewResult(task)"
                      class="btn btn-secondary btn-sm"
                      @click.stop="openViewResult(task)"
                    >
                      View Result
                    </button>
                    <button
                      v-if="canDecide(task)"
                      class="btn btn-primary btn-sm"
                      @click.stop="decidingTask = task"
                    >
                      Decision
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else class="empty-state">No requests found.</div>
    </template>

    <!-- View Result Modal -->
    <div v-if="viewingResult" class="modal-overlay" @click.self="viewingResult = null">
      <div class="modal modal-wide">
        <div class="modal-header">
          <span class="modal-title">Task Result — {{ viewingResult.task.taskName }}</span>
          <button class="modal-close" @click="viewingResult = null">✕</button>
        </div>
        <div class="modal-body">
          <div v-if="viewingResult.loading" class="loading-state">
            <span class="spinner"></span>
            <span>Loading result...</span>
          </div>
          <div v-else-if="!viewingResult.result" class="empty-state">No result available.</div>
          <template v-else>
            <div class="result-grid">
              <div class="result-panel">
                <div class="result-panel-title">Result Summary</div>
                <pre class="result-pre">{{ JSON.stringify(viewingResult.result.resultSummary, null, 2) }}</pre>
              </div>
              <div class="result-panel">
                <div class="result-panel-title">Expected Output</div>
                <pre class="result-pre">{{ viewingResult.task.expectedOutput ?? '—' }}</pre>
              </div>
            </div>
            <div v-if="viewingResult.result.resultLogs" style="margin-top:16px">
              <div class="result-panel-title">Logs</div>
              <pre class="result-pre log-pre">{{ viewingResult.result.resultLogs }}</pre>
            </div>
            <div v-if="viewingResult.result.externalJobUrl" class="external-link-section">
              <div class="result-panel-title">External Job</div>
              <div class="external-link-row">
                <span class="badge badge-auto">{{ viewingResult.result.externalSystemType }}</span>
                <a :href="viewingResult.result.externalJobUrl" target="_blank" rel="noopener" class="external-link">
                  {{ viewingResult.result.externalJobUrl }}
                </a>
                <span v-if="viewingResult.result.submissionStatus" class="badge" :class="viewingResult.result.submissionStatus === 'SUBMITTED' ? 'badge-completed' : 'badge-failed'">
                  {{ viewingResult.result.submissionStatus }}
                </span>
              </div>
              <div v-if="viewingResult.result.submissionMessage && viewingResult.result.submissionStatus === 'FAILED'" class="submission-error">
                {{ viewingResult.result.submissionMessage }}
              </div>
            </div>
            <div class="result-meta">
              <span>Status: <strong>{{ viewingResult.result.status }}</strong></span>
              <span>Attempt: <strong>#{{ viewingResult.result.attemptNumber }}</strong></span>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- Task Edit Dialog -->
    <TaskEditDialog
      v-if="editingTask"
      :task="editingTask"
      @saved="onTaskSaved"
      @close="editingTask = null"
    />

    <!-- Record Result Dialog -->
    <RecordResultDialog
      v-if="recordingTask"
      :task="recordingTask"
      @saved="onTaskSaved"
      @close="recordingTask = null"
    />

    <!-- Decision Dialog -->
    <DecisionDialog
      v-if="decidingTask"
      :task="decidingTask"
      @decided="onDecisionMade"
      @close="decidingTask = null"
    />
  </div>
</template>

<style scoped>
.detail-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.nav-back {
  display: flex;
  align-items: center;
}

.detail-header {
  padding: 16px 20px;
}

.header-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: center;
}

.header-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.field-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.field-value {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.mono { font-family: monospace; }

.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0;
}

.tab-btn {
  padding: 8px 16px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  margin-bottom: -1px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.tab-btn:hover { color: #1e293b; }
.tab-btn.active { color: #2563eb; border-bottom-color: #2563eb; }

.requests-section {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  padding: 16px;
}

.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.modal-wide {
  width: 760px;
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.result-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 6px;
}

.result-pre {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.log-pre {
  max-height: 300px;
}

.result-meta {
  display: flex;
  gap: 20px;
  margin-top: 12px;
  font-size: 13px;
  color: #64748b;
}

.external-link-section {
  margin-top: 16px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.external-link-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.external-link {
  font-size: 13px;
  color: #2563eb;
  word-break: break-all;
}

.external-link:hover {
  text-decoration: underline;
}

.submission-error {
  margin-top: 8px;
  font-size: 12px;
  color: #dc2626;
}
</style>
