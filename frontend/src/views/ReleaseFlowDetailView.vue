<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReleaseFlowStore } from '../stores/releaseFlow'
import { useUserStore } from '../stores/user'
import { getTaskResult, submitAutoExecution } from '../api/tasks'
import {
  archiveRequestRundown,
  markRequestFailed,
  purgeRequestRundown,
  restoreRequestRundown,
  startRequestDeployment,
} from '../api/releaseFlows'
import TaskEditDialog from '../components/TaskEditDialog.vue'
import DecisionDialog from '../components/DecisionDialog.vue'
import RundownEditDialog from '../components/RundownEditDialog.vue'
import TaskActivityDialog from '../components/TaskActivityDialog.vue'
import type { Task, TaskResult, Request } from '../types'

type DecisionOption = 'Approve' | 'Reject' | 'Rerun' | 'Skip'
type TaskDialogMode = 'edit' | 'run'

const route = useRoute()
const router = useRouter()
const store = useReleaseFlowStore()
const userStore = useUserStore()

const flowId = computed(() => route.params.id as string)
const includeArchivedView = computed(() => userStore.isDevOpsAdmin && route.query.archived === '1')

const editingTask = ref<Task | null>(null)
const taskDialogMode = ref<TaskDialogMode>('edit')
const decidingTask = ref<Task | null>(null)
const viewingActivityTask = ref<Task | null>(null)
const initialDecision = ref<DecisionOption | null>(null)
const allowedDecisionOptions = ref<DecisionOption[]>(['Approve', 'Reject', 'Skip'])
const editingRundown = ref<Request | null>(null)
const requestActionLoadingId = ref<string | null>(null)
const refreshingDetail = ref(false)

const viewingResult = ref<{ task: Task; result: TaskResult | null; loading: boolean } | null>(null)

watch(
  [flowId, includeArchivedView],
  async ([id, includeArchived]) => {
    await store.selectFlowWithArchived(id, includeArchived)
  },
  { immediate: true },
)

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

function criticalBadgeClass(isCritical: boolean): string {
  return isCritical ? 'badge-critical-yes' : 'badge-critical-no'
}

function criticalLabel(isCritical: boolean): string {
  return isCritical ? 'Y' : 'N'
}

function normalizeIdentity(value: string | null | undefined): string {
  return (value ?? '').toLowerCase().replace(/[^a-z0-9]/g, '')
}

function isTaskAdmin(): boolean {
  return userStore.isDevOpsAdmin
}

function isTaskOwner(task: Task): boolean {
  const owner = normalizeIdentity(task.owner)
  if (!owner) return false

  const displayName = userStore.displayName.replace(/\s*\(.*\)$/, '').trim()
  const firstName = displayName.split(/\s+/)[0] ?? ''
  const candidates = [
    userStore.userId,
    displayName,
    firstName,
  ]
    .map((value) => normalizeIdentity(value))
    .filter(Boolean)

  return candidates.includes(owner)
}

function canModifyTask(task: Task): boolean {
  return isTaskAdmin() || isTaskOwner(task)
}

function canEdit(task: Task): boolean {
  return (
    canModifyTask(task) &&
    (task.taskStatus === 'Pending' || task.taskStatus === 'Ready_For_Execution')
  )
}

function editDisabledReason(task: Task): string | null {
  if (canEdit(task)) return null
  if (!canModifyTask(task)) return 'Task owner or admin only'
  return 'Available only when task is Pending or Ready_For_Execution'
}

function canDecide(task: Task): boolean {
  return canModifyTask(task) && task.taskStatus === 'Awaiting_Review'
}

function decisionDisabledReason(task: Task): string | null {
  if (canDecide(task)) return null
  if (!canModifyTask(task)) return 'Task owner or admin only'
  return 'Available only when task status is Awaiting_Review'
}

function canRun(task: Task): boolean {
  return canModifyTask(task) && task.taskStatus === 'Ready_For_Execution'
}

function runDisabledReason(task: Task): string | null {
  if (canRun(task)) return null
  if (!canModifyTask(task)) return 'Task owner or admin only'
  return 'Available only when task status is Ready_For_Execution'
}

function runButtonLabel(task: Task): string {
  if (task.executionType === 'AUTO' && submittingAuto.value === task.id) {
    return 'Running...'
  }
  if (task.taskStatus === 'Executing') {
    return 'Running...'
  }
  return 'Run'
}

function canRerun(task: Task): boolean {
  return canModifyTask(task) && (task.taskStatus === 'Failed' || task.taskStatus === 'Rejected')
}

function rerunDisabledReason(task: Task): string | null {
  if (canRerun(task)) return null
  if (!canModifyTask(task)) return 'Task owner or admin only'
  return 'Available only when task status is Failed or Rejected'
}

function canEditRundown(): boolean {
  return userStore.isDeveloper || userStore.isTL || userStore.isDevOpsAdmin
}

function canRestoreRundown(): boolean {
  return userStore.isDevOpsAdmin
}

function canPurgeRundown(request: Request): boolean {
  return userStore.isDevOpsAdmin && isArchivedRequest(request)
}

function isArchivedRequest(request: Request): boolean {
  return !!request.archivedAt
}

function archivedRequestReason(request: Request): string | null {
  if (!isArchivedRequest(request)) return null
  return 'Archived rundowns are read-only until restored.'
}

function taskActionReason(request: Request, reason: string | null): string | null {
  return archivedRequestReason(request) ?? reason
}

function canStartDeployment(request: Request): boolean {
  return (
    canEditRundown() &&
    !isArchivedRequest(request) &&
    request.requestStatus === 'Pending' &&
    request.tasks.some((task) => task.taskStatus === 'Pending')
  )
}

function canMarkRequestFailed(request: Request): boolean {
  return (
    canEditRundown() &&
    !isArchivedRequest(request) &&
    !['Completed', 'Failed', 'Rejected', 'Skipped'].includes(request.requestStatus)
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

async function handleRun(task: Task) {
  if (!canRun(task)) return

  if (task.executionType === 'AUTO') {
    await handleSubmitAuto(task)
    return
  }

  taskDialogMode.value = 'run'
  editingTask.value = task
}

function openEditTask(task: Task) {
  if (!canEdit(task)) return
  taskDialogMode.value = 'edit'
  editingTask.value = task
}

function canViewResult(task: Task): boolean {
  return !!task.latestExecutionId
}

function viewResultDisabledReason(task: Task): string | null {
  if (canViewResult(task)) return null
  return 'Available after the task has execution output'
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
  taskDialogMode.value = 'edit'
  await store.refreshDetail()
}

async function onDecisionMade() {
  decidingTask.value = null
  initialDecision.value = null
  allowedDecisionOptions.value = ['Approve', 'Reject', 'Skip']
  await store.refreshDetail()
}

function closeDecisionDialog() {
  decidingTask.value = null
  initialDecision.value = null
  allowedDecisionOptions.value = ['Approve', 'Reject', 'Skip']
}

async function onRundownSaved() {
  editingRundown.value = null
  await store.refreshDetail()
}

async function handleRefreshDetail() {
  refreshingDetail.value = true
  try {
    await store.refreshDetail()
  } finally {
    refreshingDetail.value = false
  }
}

async function handleStartDeployment(request: Request) {
  requestActionLoadingId.value = `${request.id}:start`
  try {
    await startRequestDeployment(request.releaseFlowId, request.id)
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    requestActionLoadingId.value = null
  }
}

async function handleMarkRequestFailed(request: Request) {
  requestActionLoadingId.value = `${request.id}:fail`
  try {
    await markRequestFailed(request.releaseFlowId, request.id)
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    requestActionLoadingId.value = null
  }
}

function archiveRundownConfirmationMessage(request: Request): string {
  const activeRequestCount = store.detail?.requests.filter((item) => !item.archivedAt).length ?? 0
  if (activeRequestCount <= 1) {
    return `Archive the ${request.stage} rundown? This is the last active stage, so the entire release flow will move into Archived and disappear from the default list.`
  }
  return `Archive the ${request.stage} rundown and hide it from the default workflow view?`
}

async function handleArchiveRundown(request: Request) {
  if (!canEditRundown()) return
  if (!window.confirm(archiveRundownConfirmationMessage(request))) return

  requestActionLoadingId.value = `${request.id}:archive`
  try {
    const result = await archiveRequestRundown(request.releaseFlowId, request.id)
    if (result.releaseFlowArchived && !includeArchivedView.value) {
      await store.fetchList()
      await router.push('/wwa/deployment-agent')
      return
    }
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    requestActionLoadingId.value = null
  }
}

async function handleRestoreRundown(request: Request) {
  if (!canRestoreRundown()) return
  if (!window.confirm(`Restore the ${request.stage} rundown back into the active workflow?`)) return

  requestActionLoadingId.value = `${request.id}:restore`
  try {
    await restoreRequestRundown(request.releaseFlowId, request.id)
    await store.fetchList()
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    requestActionLoadingId.value = null
  }
}

function purgeRundownConfirmationMessage(request: Request): string {
  const totalRequestCount = store.detail?.requests.length ?? 0
  if (totalRequestCount <= 1) {
    return `Delete the ${request.stage} rundown permanently? This is irreversible and will permanently remove the entire release flow because no other rundowns remain.`
  }
  return `Delete the ${request.stage} rundown permanently? This is irreversible and removes its archived task history from the system.`
}

async function handlePurgeRundown(request: Request) {
  if (!canPurgeRundown(request)) return
  if (!window.confirm(purgeRundownConfirmationMessage(request))) return

  requestActionLoadingId.value = `${request.id}:purge`
  try {
    const result = await purgeRequestRundown(request.releaseFlowId, request.id)
    await store.fetchList()
    if (result.releaseFlowDeleted) {
      await router.push('/wwa/deployment-agent')
      return
    }
    await store.refreshDetail()
  } catch {
    // Error handled by axios interceptor
  } finally {
    requestActionLoadingId.value = null
  }
}

async function toggleArchivedVisibility() {
  const nextQuery = { ...route.query }
  if (includeArchivedView.value) {
    delete nextQuery.archived
  } else {
    nextQuery.archived = '1'
  }
  await router.replace({ query: nextQuery })
}

function openDecision(
  task: Task,
  options?: { decision?: DecisionOption; allowedDecisions?: DecisionOption[] },
) {
  decidingTask.value = task
  initialDecision.value = options?.decision ?? null
  allowedDecisionOptions.value = options?.allowedDecisions ?? ['Approve', 'Reject', 'Skip']
}

function openRerun(task: Task) {
  openDecision(task, {
    decision: 'Rerun',
    allowedDecisions: ['Rerun'],
  })
}

function handleDecisionSelect(task: Task, event: Event) {
  const select = event.target as HTMLSelectElement
  const decision = select.value as DecisionOption | ''
  if (!decision) return
  openDecision(task, {
    decision,
    allowedDecisions: ['Approve', 'Reject', 'Skip'],
  })
  select.value = ''
}

function activeStageIndex(requests: Request[]): number {
  const detail = store.detail
  if (!detail) return 0
  const idx = requests.findIndex((r) => r.stage === detail.currentStage && !r.archivedAt)
  if (idx >= 0) return idx
  const archivedIdx = requests.findIndex((r) => r.stage === detail.currentStage)
  if (archivedIdx >= 0) return archivedIdx
  return 0
}

function requestTabLabel(request: Request): string {
  return request.archivedAt ? `${request.stage} (Archived)` : request.stage
}

function canEditRundownFields(request: Request): boolean {
  return canEditRundown() && !isArchivedRequest(request)
}

function flowArchiveLabel(): string | null {
  if (!store.detail?.archivedAt) return null
  return `Archived on ${formatDateTime(store.detail.archivedAt)}`
}

const activeTab = ref(0)

const activeRequest = computed(() => {
  const detail = store.detail
  if (!detail || detail.requests.length === 0) return null
  return detail.requests[activeTab.value] ?? detail.requests[0] ?? null
})

const activeRequestSummary = computed(() => {
  const request = activeRequest.value
  if (!request) return null

  const uniqueTaskGroups = new Set(request.tasks.map((task) => task.taskGroupId))
  const taskNames = new Set(request.tasks.map((task) => task.taskName))
  const uniqueOwners = Array.from(
    new Set(request.tasks.map((task) => task.owner).filter((owner): owner is string => !!owner)),
  )
  const manualCount = request.tasks.filter((task) => task.executionType === 'MANUAL').length
  const autoCount = request.tasks.filter((task) => task.executionType === 'AUTO').length
  const pendingReviewCount = request.tasks.filter((task) => task.taskStatus === 'Awaiting_Review').length
  const completedTaskCount = request.tasks.filter((task) =>
    ['Approved', 'Rejected', 'Skipped', 'Completed', 'Failed'].includes(task.taskStatus),
  ).length
  const dependencyEdges = request.tasks.reduce(
    (sum, task) => sum + parseDependencyList(task.dependencies).length,
    0,
  )
  const tasksWithDependencies = request.tasks.filter(
    (task) => parseDependencyList(task.dependencies).length > 0,
  ).length
  const unresolvedDependencyCount = request.tasks.reduce((sum, task) => {
    const unresolved = parseDependencyList(task.dependencies).filter(
      (dependency) => !taskNames.has(dependency),
    )
    return sum + unresolved.length
  }, 0)

  const plannedStarts = request.tasks
    .map((task) => task.plannedStartTime)
    .filter((time): time is string => !!time)
    .map((time) => new Date(time))
    .filter((date) => !Number.isNaN(date.getTime()))

  const plannedEnds = request.tasks
    .map((task) => task.plannedEndTime)
    .filter((time): time is string => !!time)
    .map((time) => new Date(time))
    .filter((date) => !Number.isNaN(date.getTime()))

  const plannedStart =
    plannedStarts.length > 0
      ? new Date(Math.min(...plannedStarts.map((date) => date.getTime())))
      : null
  const plannedEnd =
    plannedEnds.length > 0
      ? new Date(Math.max(...plannedEnds.map((date) => date.getTime())))
      : null

  const lastUpdated = request.updatedAt ? new Date(request.updatedAt) : null

  const progressPercent =
    request.tasks.length > 0 ? Math.round((completedTaskCount / request.tasks.length) * 100) : 0
  const manualPercent =
    request.tasks.length > 0 ? Math.round((manualCount / request.tasks.length) * 100) : 0
  const autoPercent =
    request.tasks.length > 0 ? Math.round((autoCount / request.tasks.length) * 100) : 0

  return {
    taskGroupCount: uniqueTaskGroups.size,
    owners: uniqueOwners,
    manualCount,
    autoCount,
    manualPercent,
    autoPercent,
    pendingReviewCount,
    completedTaskCount,
    progressPercent,
    dependencyEdges,
    tasksWithDependencies,
    unresolvedDependencyCount,
    plannedStart,
    plannedEnd,
    lastUpdated,
  }
})

function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function hasValue(value: string | null | undefined): boolean {
  return !!value && value.trim().length > 0
}

function parseDependencyList(value?: string): string[] {
  if (!value) return []

  return Array.from(
    new Set(
      value
        .split(/[\n,;]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  )
}

function getBlockingTaskNames(task: Task, tasks: Task[]): string[] {
  return tasks
    .filter((candidate) => parseDependencyList(candidate.dependencies).includes(task.taskName))
    .map((candidate) => candidate.taskName)
}

function getMissingDependencyNames(task: Task, tasks: Task[]): string[] {
  const taskNames = new Set(tasks.map((candidate) => candidate.taskName))
  return parseDependencyList(task.dependencies).filter((dependency) => !taskNames.has(dependency))
}

function plannedWindowLabel(start: Date | null, end: Date | null): string {
  if (!start && !end) return '—'
  if (start && end) return `${formatDateTime(start)} to ${formatDateTime(end)}`
  return start ? formatDateTime(start) : formatDateTime(end)
}

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
      <button class="btn btn-secondary btn-sm" @click="router.push('/wwa/deployment-agent')">
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
        <div class="detail-header-actions">
          <span v-if="store.detail.archivedAt" class="badge badge-rejected">
            {{ flowArchiveLabel() }}
          </span>
          <button
            v-if="userStore.isDevOpsAdmin"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="toggleArchivedVisibility"
          >
            {{ includeArchivedView ? 'Hide Archived Rundowns' : 'Show Archived Rundowns' }}
          </button>
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
            {{ requestTabLabel(req) }}
            <span class="badge" :class="statusBadgeClass(req.requestStatus)" style="margin-left:6px;font-size:11px">
              {{ req.requestStatus }}
            </span>
            <span v-if="req.archivedAt" class="badge badge-rejected" style="margin-left:6px;font-size:11px">
              Archived
            </span>
          </button>
        </div>

        <!-- Task table for active tab -->
        <div
          v-for="(req, idx) in store.detail.requests"
          :key="req.id"
          v-show="activeTab === idx"
        >
          <div v-if="activeRequestSummary" class="stage-rundown">
            <div class="rundown-head">
              <div class="rundown-title">Rundown Information</div>
              <div class="rundown-head-actions">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="refreshingDetail"
                  @click="handleRefreshDetail"
                >
                  {{ refreshingDetail ? 'Refreshing...' : 'Refresh' }}
                </button>
                <button
                  v-if="canEditRundownFields(req)"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  @click="editingRundown = req"
                >
                  Edit Rundown
                </button>
                <button
                  v-if="canEditRundownFields(req)"
                  type="button"
                  class="btn btn-danger btn-sm"
                  :disabled="requestActionLoadingId === `${req.id}:archive`"
                  @click="handleArchiveRundown(req)"
                >
                  {{ requestActionLoadingId === `${req.id}:archive` ? 'Archiving...' : 'Archive Rundown' }}
                </button>
                <button
                  v-if="req.archivedAt && canRestoreRundown()"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="requestActionLoadingId === `${req.id}:restore`"
                  @click="handleRestoreRundown(req)"
                >
                  {{ requestActionLoadingId === `${req.id}:restore` ? 'Restoring...' : 'Restore Rundown' }}
                </button>
                <button
                  v-if="canPurgeRundown(req)"
                  type="button"
                  class="btn btn-danger btn-sm"
                  :disabled="requestActionLoadingId === `${req.id}:purge`"
                  @click="handlePurgeRundown(req)"
                >
                  {{
                    requestActionLoadingId === `${req.id}:purge`
                      ? 'Deleting...'
                      : 'Delete Permanently'
                  }}
                </button>
              </div>
            </div>

            <div v-if="req.archivedAt" class="rundown-section archived-rundown-note">
              This rundown is archived. Task history remains visible, but workflow actions stay disabled until it is restored. DEVOPS_ADMIN can also delete it permanently after review.
            </div>

            <div class="rundown-section">
              <div class="rundown-info-grid">
                <div class="rundown-field">
                  <span class="rundown-field-label">Status:</span>
                  <span class="badge" :class="statusBadgeClass(req.requestStatus)">
                    {{ req.requestStatus }}
                  </span>
                </div>
                <div class="rundown-field">
                  <span class="rundown-field-label">Environment:</span>
                  <span class="badge badge-pending">{{ req.stage }}</span>
                </div>
                <div v-if="hasValue(req.snowGroup)" class="rundown-field">
                  <span class="rundown-field-label">SNOW Group:</span>
                  <span class="rundown-field-value">{{ req.snowGroup }}</span>
                </div>
                <div class="rundown-field">
                  <span class="rundown-field-label">Application:</span>
                  <span class="rundown-field-value">{{ req.application ?? store.detail.projectName }}</span>
                </div>
                <div v-if="hasValue(req.site)" class="rundown-field">
                  <span class="rundown-field-label">Site:</span>
                  <span class="rundown-field-value">{{ req.site }}</span>
                </div>
                <div class="rundown-field">
                  <span class="rundown-field-label">Owners:</span>
                  <span class="rundown-field-value">
                    {{ activeRequestSummary.owners.length > 0 ? activeRequestSummary.owners.join(', ') : '—' }}
                  </span>
                </div>
                <div class="rundown-field">
                  <span class="rundown-field-label">Execution Mix:</span>
                  <div class="mix-value">
                    <span class="rundown-field-value">
                      {{ activeRequestSummary.manualCount }} manual ({{ activeRequestSummary.manualPercent }}%) /
                      {{ activeRequestSummary.autoCount }} auto ({{ activeRequestSummary.autoPercent }}%)
                    </span>
                    <div class="mix-bar" aria-label="Execution mix">
                      <div
                        class="mix-bar-segment mix-bar-manual"
                        :style="{ width: `${activeRequestSummary.manualPercent}%` }"
                      ></div>
                      <div
                        class="mix-bar-segment mix-bar-auto"
                        :style="{ width: `${activeRequestSummary.autoPercent}%` }"
                      ></div>
                    </div>
                    <div class="mix-legend">
                      <span class="mix-legend-item">
                        <span class="mix-dot mix-dot-manual"></span>
                        Manual
                      </span>
                      <span class="mix-legend-item">
                        <span class="mix-dot mix-dot-auto"></span>
                        Auto
                      </span>
                    </div>
                  </div>
                </div>
                <div class="rundown-field">
                  <span class="rundown-field-label">Planned Window:</span>
                  <span class="rundown-field-value">
                    {{
                      plannedWindowLabel(
                        activeRequestSummary.plannedStart,
                        activeRequestSummary.plannedEnd,
                      )
                    }}
                  </span>
                </div>
              </div>
            </div>

            <div class="rundown-section">
              <div class="rundown-section-title">Progress Overview</div>
              <div class="rundown-progress-grid">
                <div class="rundown-progress-block">
                  <div class="rundown-progress-label">Overall Progress</div>
                  <div class="rundown-progress-value">
                    {{ activeRequestSummary.progressPercent }}%
                  </div>
                </div>
                <div class="rundown-progress-block">
                  <div class="rundown-progress-label">Estimated Remaining Time</div>
                  <div class="rundown-progress-value">
                    {{
                      req.estimatedRemainingMinutes !== undefined && req.estimatedRemainingMinutes !== null
                        ? `${req.estimatedRemainingMinutes}m`
                        : '—'
                    }}
                  </div>
                </div>
                <div class="rundown-progress-block">
                  <div class="rundown-progress-label">Tasks Completed</div>
                  <div class="rundown-progress-value">
                    {{ activeRequestSummary.completedTaskCount }} / {{ req.tasks.length }}
                  </div>
                </div>
                <div class="rundown-progress-block">
                  <div class="rundown-progress-label">Last Updated</div>
                  <div class="rundown-progress-value">
                    {{ formatDateTime(activeRequestSummary.lastUpdated) }}
                  </div>
                </div>
              </div>
            </div>

            <div class="rundown-section">
              <div class="rundown-request-actions">
                <button
                  v-if="canStartDeployment(req)"
                  type="button"
                  class="btn btn-primary btn-start"
                  :disabled="requestActionLoadingId === `${req.id}:start`"
                  @click="handleStartDeployment(req)"
                >
                  {{ requestActionLoadingId === `${req.id}:start` ? 'Starting...' : 'Start Deployment' }}
                </button>
                <button
                  v-if="canMarkRequestFailed(req)"
                  type="button"
                  class="btn btn-danger"
                  :disabled="requestActionLoadingId === `${req.id}:fail`"
                  @click="handleMarkRequestFailed(req)"
                >
                  {{ requestActionLoadingId === `${req.id}:fail` ? 'Marking...' : 'Mark as Failed' }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="req.tasks.length === 0" class="empty-state">No tasks in this request.</div>
          <template v-else>
            <div class="task-dependency-panel">
              <div class="task-dependency-head">
                <div>
                  <div class="task-dependency-title">Task Dependencies</div>
                  <div class="task-dependency-copy">
                    Dependency details now live with the task table, where blocked relationships are easiest to act on.
                  </div>
                </div>
              </div>

              <div class="task-dependency-grid">
                <div class="task-dependency-block">
                  <div class="task-dependency-label">Dependency Links</div>
                  <div class="task-dependency-value">{{ activeRequestSummary?.dependencyEdges ?? 0 }}</div>
                </div>
                <div class="task-dependency-block">
                  <div class="task-dependency-label">Tasks With Prerequisites</div>
                  <div class="task-dependency-value">{{ activeRequestSummary?.tasksWithDependencies ?? 0 }}</div>
                </div>
                <div class="task-dependency-block">
                  <div class="task-dependency-label">Missing Links</div>
                  <div class="task-dependency-value">{{ activeRequestSummary?.unresolvedDependencyCount ?? 0 }}</div>
                </div>
              </div>

              <div
                v-if="(activeRequestSummary?.unresolvedDependencyCount ?? 0) > 0"
                class="task-dependency-warning"
              >
                {{
                  activeRequestSummary?.unresolvedDependencyCount
                }}
                dependency link{{ (activeRequestSummary?.unresolvedDependencyCount ?? 0) > 1 ? 's are' : ' is' }}
                missing in this rundown. Check the `Blocked By` column below for affected tasks.
              </div>
            </div>

            <table class="data-table">
            <thead>
              <tr>
                <th>Activity Category</th>
                <th>Task Name</th>
                <th>Step</th>
                <th>Step Name</th>
                <th>Type</th>
                <th>Critical</th>
                <th>Status</th>
                <th>Owner</th>
                <th>Blocked By</th>
                <th>Blocks</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in req.tasks" :key="task.id">
                <td>{{ task.category ?? '—' }}</td>
                <td>{{ task.taskGroupName }}</td>
                <td>{{ task.stepSeq }}</td>
                <td>{{ task.taskName }}</td>
                <td>
                  <span class="badge" :class="executionTypeBadgeClass(task.executionType)">
                    {{ task.executionType }}
                  </span>
                </td>
                <td>
                  <span
                    class="badge"
                    :class="criticalBadgeClass(task.critical)"
                    :title="task.critical ? 'Must be reviewed before the next task can be released' : 'Does not block the next task from being released'"
                  >
                    {{ criticalLabel(task.critical) }}
                  </span>
                </td>
                <td>
                  <span class="badge" :class="statusBadgeClass(task.taskStatus)">
                    {{ task.taskStatus }}
                  </span>
                </td>
                <td>{{ task.owner ?? '—' }}</td>
                <td>
                  <div v-if="parseDependencyList(task.dependencies).length > 0" class="dependency-chip-list">
                    <span
                      v-for="dependency in parseDependencyList(task.dependencies)"
                      :key="`${task.id}-blocked-by-${dependency}`"
                      class="dependency-chip"
                    >
                      {{ dependency }}
                    </span>
                  </div>
                  <span v-else class="dependency-empty-chip">—</span>
                  <div v-if="getMissingDependencyNames(task, req.tasks).length > 0" class="dependency-warning-text">
                    Missing:
                    {{ getMissingDependencyNames(task, req.tasks).join(', ') }}
                  </div>
                </td>
                <td>
                  <div v-if="getBlockingTaskNames(task, req.tasks).length > 0" class="dependency-chip-list">
                    <span
                      v-for="dependency in getBlockingTaskNames(task, req.tasks)"
                      :key="`${task.id}-blocks-${dependency}`"
                      class="dependency-chip dependency-chip-outbound"
                    >
                      {{ dependency }}
                    </span>
                  </div>
                  <span v-else class="dependency-empty-chip">—</span>
                </td>
                <td>
                  <div class="task-action-panel">
                    <div class="action-btns">
                      <span class="action-tooltip" :title="taskActionReason(req, editDisabledReason(task)) ?? ''">
                        <button
                          class="btn btn-secondary btn-sm"
                          :disabled="isArchivedRequest(req) || !canEdit(task)"
                          @click.stop="openEditTask(task)"
                        >
                          Edit
                        </button>
                      </span>
                      <button
                        class="btn btn-secondary btn-sm"
                        @click.stop="viewingActivityTask = task"
                      >
                        Activity
                      </button>
                      <span class="action-tooltip" :title="viewResultDisabledReason(task) ?? ''">
                        <button
                          class="btn btn-secondary btn-sm"
                          :disabled="!canViewResult(task)"
                          @click.stop="canViewResult(task) && openViewResult(task)"
                        >
                          View Result
                        </button>
                      </span>
                      <span class="action-tooltip" :title="taskActionReason(req, runDisabledReason(task)) ?? ''">
                        <button
                          class="btn btn-primary btn-sm"
                          :disabled="isArchivedRequest(req) || !canRun(task) || submittingAuto === task.id"
                          @click.stop="handleRun(task)"
                        >
                          {{ runButtonLabel(task) }}
                        </button>
                      </span>
                      <span class="action-tooltip" :title="taskActionReason(req, rerunDisabledReason(task)) ?? ''">
                        <button
                          class="btn btn-secondary btn-sm"
                          :disabled="isArchivedRequest(req) || !canRerun(task)"
                          @click.stop="canRerun(task) && openRerun(task)"
                        >
                          Rerun
                        </button>
                      </span>
                    </div>
                    <span class="action-tooltip" :title="taskActionReason(req, decisionDisabledReason(task)) ?? ''">
                      <select
                        class="decision-select"
                        :disabled="isArchivedRequest(req) || !canDecide(task)"
                        @change="handleDecisionSelect(task, $event)"
                      >
                        <option value="">Review Decision</option>
                        <option value="Approve">Approve</option>
                        <option value="Reject">Reject</option>
                        <option value="Skip">Skip</option>
                      </select>
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
            </table>
          </template>
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
      :mode="taskDialogMode"
      @saved="onTaskSaved"
      @close="editingTask = null; taskDialogMode = 'edit'"
    />

    <!-- Decision Dialog -->
    <DecisionDialog
      v-if="decidingTask"
      :task="decidingTask"
      :initial-decision="initialDecision"
      :allowed-decisions="allowedDecisionOptions"
      @decided="onDecisionMade"
      @close="closeDecisionDialog"
    />

    <RundownEditDialog
      v-if="editingRundown"
      :request="editingRundown"
      @saved="onRundownSaved"
      @close="editingRundown = null"
    />

    <TaskActivityDialog
      v-if="viewingActivityTask"
      :key="viewingActivityTask.id"
      :task="viewingActivityTask"
      @close="viewingActivityTask = null"
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

.detail-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
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

.stage-rundown {
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  overflow: hidden;
}

.rundown-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.rundown-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.rundown-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rundown-section {
  padding: 18px;
  border-bottom: 1px solid #eef2f7;
}

.rundown-section:last-of-type {
  border-bottom: none;
}

.archived-rundown-note {
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  font-weight: 500;
}

.rundown-section-title {
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.rundown-info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px 28px;
}

.rundown-field {
  min-width: 0;
}

.rundown-field-label {
  margin-right: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.rundown-field-value {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  word-break: break-word;
}

.mix-value {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mix-bar {
  display: flex;
  width: 100%;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.mix-bar-segment {
  height: 100%;
}

.mix-bar-manual {
  background: #8b5cf6;
}

.mix-bar-auto {
  background: #0ea5e9;
}

.mix-legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.mix-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.mix-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.mix-dot-manual {
  background: #8b5cf6;
}

.mix-dot-auto {
  background: #0ea5e9;
}

.badge-critical-yes {
  background: #fee2e2;
  color: #b91c1c;
}

.badge-critical-no {
  background: #e2e8f0;
  color: #475569;
}

.rundown-progress-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px 28px;
}

.rundown-progress-block {
  min-width: 0;
}

.rundown-progress-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}

.rundown-progress-value {
  font-size: 14px;
  color: #475569;
}

.rundown-request-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.task-dependency-panel {
  margin-bottom: 14px;
  padding: 16px 18px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.task-dependency-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.task-dependency-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.task-dependency-copy {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.task-dependency-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
}

.task-dependency-block {
  min-width: 0;
}

.task-dependency-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #64748b;
  margin-bottom: 6px;
}

.task-dependency-value {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.task-dependency-warning {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.5;
}

.dependency-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dependency-chip,
.dependency-empty-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.dependency-chip {
  background: #eff6ff;
  color: #1d4ed8;
}

.dependency-chip-outbound {
  background: #ecfdf5;
  color: #047857;
}

.dependency-empty-chip {
  background: #f8fafc;
  color: #94a3b8;
}

.dependency-warning-text {
  margin-top: 6px;
  font-size: 12px;
  color: #c2410c;
}

.btn-start {
  background: #16a34a;
  border-color: #16a34a;
}

.btn-start:hover:not(:disabled) {
  background: #15803d;
  border-color: #15803d;
}

@media (max-width: 1100px) {
  .rundown-info-grid,
  .rundown-progress-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-dependency-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .rundown-info-grid,
  .rundown-progress-grid {
    grid-template-columns: 1fr;
  }

  .task-dependency-grid {
    grid-template-columns: 1fr;
  }
}

.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.task-action-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.action-tooltip {
  display: inline-flex;
}

.decision-select {
  min-width: 140px;
  padding: 7px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: white;
  color: #334155;
  font-size: 13px;
}

.decision-select:disabled {
  background: #f8fafc;
  color: #94a3b8;
  cursor: not-allowed;
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
