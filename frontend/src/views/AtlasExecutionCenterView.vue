<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAtlasIntegrationStore } from '../stores/atlasIntegration'
import { useVisiblePolling } from '../platform/composables/useVisiblePolling'
import type {
  IntegrationArtifact,
  IntegrationClientType,
  IntegrationExecution,
  IntegrationReviewDecision,
} from '../platform/integration/types'
import './AtlasExecutionCenterView.css'

const store = useAtlasIntegrationStore()
const activePanel = ref<'operations' | 'usage'>('operations')
const reviewDecision = ref<IntegrationReviewDecision>('APPROVED')
const reviewComment = ref('')
const reviewSubmitted = ref(false)

const clientTypes: IntegrationClientType[] = [
  'COPILOT',
  'OPENCODE',
  'KIRO',
  'MANUAL',
  'PIPELINE',
]

const selectedTask = computed(() => store.selectedTask)
const usageRows = computed(() => store.capabilityUsage?.items ?? [])

onMounted(() => {
  void Promise.all([store.fetchTasks(), store.fetchCapabilityUsage()])
})

useVisiblePolling(() => store.refreshOperationalData(), 10_000)

watch(
  () => store.selectedTaskId,
  () => {
    reviewDecision.value = 'APPROVED'
    reviewComment.value = ''
    reviewSubmitted.value = false
    store.clearErrors()
  },
)

async function applyTaskFilters() {
  await store.fetchTasks()
}

async function resetTaskFilters() {
  store.taskFilters.status = undefined
  store.taskFilters.projectId = undefined
  store.taskFilters.team = undefined
  store.taskFilters.agentModuleId = undefined
  await store.fetchTasks()
}

async function applyUsageFilters() {
  await store.fetchCapabilityUsage()
}

async function resetUsageFilters() {
  store.usageFilters.capabilityId = undefined
  store.usageFilters.skillId = undefined
  store.usageFilters.team = undefined
  store.usageFilters.projectId = undefined
  store.usageFilters.agent = undefined
  store.usageFilters.from = undefined
  store.usageFilters.to = undefined
  store.usageFilters.clientType = undefined
  await store.fetchCapabilityUsage()
}

async function submitReview() {
  reviewSubmitted.value = await store.submitReview(reviewDecision.value, reviewComment.value)
  if (reviewSubmitted.value) reviewComment.value = ''
}

async function rerunTask() {
  await store.rerunSelectedTask()
}

async function downloadArtifact(artifact: IntegrationArtifact) {
  const content = await store.downloadArtifact(artifact)
  if (!content) return

  const objectUrl = window.URL.createObjectURL(content)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = `atlas-artifact-${artifact.artifactId}`
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(objectUrl)
}

function statusLabel(value: string): string {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function statusClass(value: string): string {
  return `atlas-status-${value.toLowerCase().replaceAll('_', '-')}`
}

function formatTimestamp(value?: string): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString()
}

function formatDuration(milliseconds?: number): string {
  if (milliseconds == null) return '—'
  if (milliseconds < 1_000) return `${Math.round(milliseconds)} ms`
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(1)} s`
  return `${(milliseconds / 60_000).toFixed(1)} min`
}

function formatBytes(bytes: number): string {
  if (bytes < 1_024) return `${bytes} B`
  if (bytes < 1_048_576) return `${(bytes / 1_024).toFixed(1)} KB`
  return `${(bytes / 1_048_576).toFixed(1)} MB`
}

function formatRate(value: number): string {
  return `${value.toFixed(1)}%`
}

function executionIssue(execution: IntegrationExecution): string {
  if (execution.failureReason) {
    return execution.failureReason.code
  }
  return execution.status === 'CANCELLED' ? 'CANCELLED: Execution cancelled by an authorized operator.' : ''
}
</script>

<template>
  <div class="atlas-center">
    <header class="atlas-view-header">
      <div>
        <p class="view-eyebrow">WWA-Atlas Hub Shared Platform Capability</p>
        <h1 class="view-title">Atlas Execution Center</h1>
        <p class="view-subtitle">
          Platform-owned task state, execution evidence, human review, and capability telemetry.
        </p>
      </div>
      <div class="atlas-live-state" aria-live="polite">
        <span class="atlas-live-dot" aria-hidden="true"></span>
        Auto-refresh every 10 seconds while visible
      </div>
    </header>

    <section class="atlas-summary-grid" aria-label="Execution summary">
      <article class="atlas-summary-card">
        <span>Visible Tasks</span>
        <strong>{{ store.tasks.length }}</strong>
      </article>
      <article class="atlas-summary-card atlas-summary-review">
        <span>Awaiting Review</span>
        <strong>{{ store.awaitingReviewCount }}</strong>
      </article>
      <article class="atlas-summary-card atlas-summary-sync">
        <span>Pending sync</span>
        <strong>{{ store.pendingSyncCount }}</strong>
      </article>
      <article class="atlas-summary-card">
        <span>Capability Invocations</span>
        <strong>{{ store.capabilityUsage?.totals.invocationCount ?? 0 }}</strong>
      </article>
    </section>

    <nav class="atlas-panel-tabs" aria-label="Execution Center sections">
      <button
        type="button"
        :class="{ active: activePanel === 'operations' }"
        @click="activePanel = 'operations'"
      >Task Operations</button>
      <button
        type="button"
        :class="{ active: activePanel === 'usage' }"
        @click="activePanel = 'usage'"
      >Capability Usage</button>
    </nav>

    <template v-if="activePanel === 'operations'">
      <section class="atlas-filter-card" aria-label="Task filters">
        <label>
          <span>Status</span>
          <select v-model="store.taskFilters.status" class="form-control">
            <option :value="undefined">All statuses</option>
            <option value="READY_FOR_EXECUTION">Ready for execution</option>
            <option value="EXECUTING">Executing</option>
            <option value="AWAITING_REVIEW">Awaiting review</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="FAILED">Failed</option>
          </select>
        </label>
        <label>
          <span>Team</span>
          <input v-model.trim="store.taskFilters.team" class="form-control" placeholder="Team" />
        </label>
        <label>
          <span>Project</span>
          <input v-model.trim="store.taskFilters.projectId" class="form-control" placeholder="Project ID" />
        </label>
        <label>
          <span>Agent Module</span>
          <input v-model.trim="store.taskFilters.agentModuleId" class="form-control" placeholder="Agent module ID" />
        </label>
        <div class="atlas-filter-actions">
          <button class="btn btn-primary" type="button" :disabled="store.loadingTasks" @click="applyTaskFilters">
            Apply
          </button>
          <button class="btn btn-secondary" type="button" :disabled="store.loadingTasks" @click="resetTaskFilters">
            Reset
          </button>
        </div>
      </section>

      <div v-if="store.taskError" class="alert alert-error" role="alert">{{ store.taskError }}</div>
      <div v-if="store.mutationError" class="alert alert-error" role="alert">{{ store.mutationError }}</div>

      <div class="atlas-workspace-grid">
        <section class="atlas-task-panel card" aria-labelledby="atlas-task-list-heading">
          <div class="atlas-section-heading">
            <div>
              <h2 id="atlas-task-list-heading">Tasks</h2>
              <p>Authorized platform work across Agent Modules.</p>
            </div>
            <span>{{ store.tasks.length }}</span>
          </div>

          <div v-if="store.loadingTasks" class="loading-state">
            <span class="spinner"></span> Loading tasks
          </div>
          <div v-else-if="store.tasks.length === 0" class="empty-state">No tasks match these filters.</div>
          <div v-else class="atlas-task-list">
            <button
              v-for="task in store.tasks"
              :key="task.taskId"
              type="button"
              class="atlas-task-row"
              :class="{ selected: task.taskId === store.selectedTaskId }"
              @click="store.selectTask(task.taskId)"
            >
              <span class="atlas-task-row-top">
                <strong>{{ task.title }}</strong>
                <span class="atlas-status" :class="statusClass(task.status)">{{ statusLabel(task.status) }}</span>
              </span>
              <span class="atlas-task-meta">
                {{ task.projectContext.project.name || task.projectContext.project.projectId }}
                · {{ task.agentModuleId }}
              </span>
              <span class="atlas-task-meta">
                {{ task.capability.capabilityType }} · {{ task.capability.capabilityId }}
                · {{ task.executionCount }} attempt{{ task.executionCount === 1 ? '' : 's' }}
              </span>
            </button>
          </div>
        </section>

        <section class="atlas-detail-panel card" aria-labelledby="atlas-task-detail-heading">
          <div v-if="store.loadingWorkspace" class="loading-state">
            <span class="spinner"></span> Loading execution evidence
          </div>
          <div v-else-if="store.workspaceError" class="alert alert-error" role="alert">{{ store.workspaceError }}</div>
          <div v-else-if="!selectedTask" class="empty-state">Select a task to inspect its execution history.</div>
          <template v-else>
            <div class="atlas-section-heading atlas-detail-heading">
              <div>
                <p class="atlas-section-kicker">{{ selectedTask.agentModuleId }}</p>
                <h2 id="atlas-task-detail-heading">{{ selectedTask.title }}</h2>
                <p>Task {{ selectedTask.taskId }} · Work item {{ selectedTask.workItemId }}</p>
              </div>
              <span class="atlas-status" :class="statusClass(selectedTask.status)">
                {{ statusLabel(selectedTask.status) }}
              </span>
            </div>

            <dl class="atlas-context-grid">
              <div>
                <dt>Assignee</dt>
                <dd>{{ selectedTask.assignee?.displayName || 'Unassigned' }}</dd>
              </div>
              <div>
                <dt>Project</dt>
                <dd>{{ selectedTask.projectContext.project.name || selectedTask.projectContext.project.projectId }}</dd>
              </div>
              <div>
                <dt>Team</dt>
                <dd>{{ selectedTask.projectContext.team || '—' }}</dd>
              </div>
              <div>
                <dt>Capability</dt>
                <dd>{{ selectedTask.capability.capabilityId }} · {{ selectedTask.capability.capabilityVersion || 'Unversioned' }}</dd>
              </div>
              <div>
                <dt>Repository</dt>
                <dd>{{ selectedTask.projectContext.repository?.repositoryId || '—' }}</dd>
              </div>
              <div>
                <dt>Branch / revision</dt>
                <dd>{{ selectedTask.projectContext.branch || '—' }} · {{ selectedTask.projectContext.commit?.slice(0, 12) || '—' }}</dd>
              </div>
            </dl>

            <div v-if="selectedTask.actions.rerun" class="atlas-filter-actions">
              <button
                class="btn btn-primary"
                type="button"
                :disabled="store.rerunningTask"
                @click="rerunTask"
              >
                {{ store.rerunningTask ? 'Preparing rerun…' : 'Rerun latest attempt' }}
              </button>
            </div>

            <section
              v-if="selectedTask.status === 'AWAITING_REVIEW'"
              class="atlas-review-card"
              aria-labelledby="atlas-review-heading"
            >
              <div>
                <p class="atlas-section-kicker">Human gate</p>
                <h3 id="atlas-review-heading">Awaiting Review</h3>
                <p>Decision applies only to the latest successful execution.</p>
              </div>
              <template v-if="selectedTask.actions.review && store.latestExecution">
                <label>
                  <span>Decision</span>
                  <select v-model="reviewDecision" class="form-control">
                    <option value="APPROVED">Approve</option>
                    <option value="REJECTED">Reject</option>
                    <option value="SKIPPED">Skip</option>
                  </select>
                </label>
                <label>
                  <span>Review comment</span>
                  <textarea
                    v-model.trim="reviewComment"
                    class="form-control"
                    maxlength="2000"
                    rows="3"
                    placeholder="Optional bounded review context"
                  ></textarea>
                </label>
                <button
                  class="btn btn-primary"
                  type="button"
                  :disabled="store.submittingReview"
                  @click="submitReview"
                >
                  {{ store.submittingReview ? 'Submitting…' : 'Submit Review' }}
                </button>
              </template>
              <p v-else class="atlas-review-restricted">Review is restricted to an authorized human reviewer.</p>
            </section>
            <div v-if="reviewSubmitted" class="alert alert-success" role="status">Review decision recorded.</div>
            <div v-if="store.review" class="atlas-recorded-review">
              <strong>Recorded decision: {{ statusLabel(store.review.decision) }}</strong>
              <span>{{ store.review.reviewer.displayName }} · {{ formatTimestamp(store.review.decidedAt) }}</span>
              <p v-if="store.review.comment">Review comment retained in the protected audit record.</p>
            </div>

            <section class="atlas-evidence-section" aria-labelledby="execution-history-heading">
              <div class="atlas-section-heading">
                <div>
                  <h3 id="execution-history-heading">Execution History</h3>
                  <p>Server-owned attempts, terminal outcomes, and synchronization state.</p>
                </div>
              </div>
              <div v-if="store.executions.length === 0" class="empty-state">No execution attempts yet.</div>
              <div v-else class="atlas-table-scroll">
                <table class="data-table atlas-data-table">
                  <thead>
                    <tr>
                      <th>Attempt</th>
                      <th>Status</th>
                      <th>Client type</th>
                      <th>User</th>
                      <th>Duration</th>
                      <th>Artifacts</th>
                      <th>Sync</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="execution in store.executions" :key="execution.executionId">
                      <tr>
                        <td>#{{ execution.attemptNumber }}</td>
                        <td><span class="atlas-status" :class="statusClass(execution.status)">{{ statusLabel(execution.status) }}</span></td>
                        <td>{{ execution.client.clientType }}</td>
                        <td>{{ execution.user.displayName }}</td>
                        <td>{{ formatDuration(execution.durationMs) }}</td>
                        <td>{{ execution.artifactCount }}</td>
                        <td>
                          <span v-if="execution.pendingSync" class="atlas-sync-badge">Pending sync</span>
                          <span v-else>Current</span>
                        </td>
                      </tr>
                      <tr v-if="executionIssue(execution)" class="atlas-issue-row">
                        <td colspan="7">
                          <strong>Failure reason:</strong> {{ executionIssue(execution) }}
                          <span v-if="execution.failureReason?.retryable"> · Retryable</span>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
            </section>

            <section class="atlas-evidence-section" aria-labelledby="artifact-list-heading">
              <div class="atlas-section-heading">
                <div>
                  <h3 id="artifact-list-heading">Artifacts</h3>
                  <p>Validated metadata and controlled downloads for the latest execution.</p>
                </div>
              </div>
              <div v-if="store.artifacts.length === 0" class="empty-state">No artifacts recorded.</div>
              <div v-else class="atlas-artifact-list">
                <article v-for="artifact in store.artifacts" :key="artifact.artifactId" class="atlas-artifact-row">
                  <div>
                    <strong>Artifact {{ artifact.artifactId.slice(0, 12) }}</strong>
                    <span>{{ artifact.role }} · {{ formatBytes(artifact.sizeBytes) }}</span>
                    <small>{{ artifact.mediaType }} · {{ artifact.digest.algorithm }} {{ artifact.digest.value.slice(0, 12) }}…</small>
                  </div>
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :disabled="store.downloadingArtifactId === artifact.artifactId || !artifact.executionId"
                    @click="downloadArtifact(artifact)"
                  >
                    {{ store.downloadingArtifactId === artifact.artifactId ? 'Downloading…' : 'Download' }}
                  </button>
                </article>
              </div>
            </section>
          </template>
        </section>
      </div>
    </template>

    <template v-else>
      <section class="atlas-filter-card atlas-usage-filters" aria-label="Capability usage filters">
        <label><span>Capability ID</span><input v-model.trim="store.usageFilters.capabilityId" class="form-control" /></label>
        <label><span>Skill ID</span><input v-model.trim="store.usageFilters.skillId" class="form-control" /></label>
        <label><span>Team</span><input v-model.trim="store.usageFilters.team" class="form-control" /></label>
        <label><span>Project</span><input v-model.trim="store.usageFilters.projectId" class="form-control" /></label>
        <label><span>Agent</span><input v-model.trim="store.usageFilters.agent" class="form-control" /></label>
        <label><span>From date</span><input v-model="store.usageFilters.from" class="form-control" type="date" /></label>
        <label><span>To date</span><input v-model="store.usageFilters.to" class="form-control" type="date" /></label>
        <label>
          <span>Client type</span>
          <select v-model="store.usageFilters.clientType" class="form-control">
            <option :value="undefined">All client types</option>
            <option v-for="clientType in clientTypes" :key="clientType" :value="clientType">{{ clientType }}</option>
          </select>
        </label>
        <div class="atlas-filter-actions">
          <button class="btn btn-primary" type="button" :disabled="store.loadingUsage" @click="applyUsageFilters">Apply</button>
          <button class="btn btn-secondary" type="button" :disabled="store.loadingUsage" @click="resetUsageFilters">Reset</button>
        </div>
      </section>

      <div v-if="store.usageError" class="alert alert-error" role="alert">{{ store.usageError }}</div>
      <div v-if="store.loadingUsage" class="loading-state"><span class="spinner"></span> Loading capability usage</div>
      <template v-else>
        <section class="atlas-usage-summary" aria-label="Capability usage totals">
          <article><span>Invocation count</span><strong>{{ store.capabilityUsage?.totals.invocationCount ?? 0 }}</strong></article>
          <article><span>Distinct capabilities</span><strong>{{ store.capabilityUsage?.totals.distinctCapabilityCount ?? 0 }}</strong></article>
        </section>

        <section class="card atlas-usage-table-card" aria-labelledby="capability-usage-heading">
          <div class="atlas-section-heading">
            <div>
              <h2 id="capability-usage-heading">Capability Usage</h2>
              <p>Invocation outcomes and Skill version distribution across authorized platform scope.</p>
            </div>
          </div>
          <div v-if="usageRows.length === 0" class="empty-state">No capability usage matches these filters.</div>
          <div v-else class="atlas-table-scroll">
            <table class="data-table atlas-data-table">
              <thead>
                <tr>
                  <th>Capability / Skill</th>
                  <th>Invocation count</th>
                  <th>Success rate</th>
                  <th>Failure rate</th>
                  <th>Average duration</th>
                  <th>Users</th>
                  <th>Skill version distribution</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in usageRows" :key="`${row.capabilityType}:${row.capabilityId}`">
                  <td>
                    <strong>{{ row.capabilityId }}</strong>
                    <small>{{ row.capabilityType }}<template v-if="row.skillId"> · {{ row.skillId }}</template></small>
                  </td>
                  <td>{{ row.invocationCount }}</td>
                  <td>{{ formatRate(row.successRate) }} <small>{{ row.successCount }} succeeded</small></td>
                  <td>{{ formatRate(row.failureRate) }} <small>{{ row.failureCount }} failed</small></td>
                  <td>{{ formatDuration(row.averageDurationMs) }}</td>
                  <td>{{ row.userCount }}</td>
                  <td>
                    <div class="atlas-version-list">
                      <span v-for="version in row.versionDistribution" :key="version.version || 'unversioned'">
                        {{ version.version || 'Unversioned' }} · {{ version.count }} ({{ formatRate(version.percentage) }})
                      </span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
