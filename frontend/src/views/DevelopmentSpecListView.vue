<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useDevelopmentSpecStore } from '../stores/developmentSpec'
import { useUserStore } from '../stores/user'
import type {
  DevelopmentSpec,
  DevelopmentSpecCodeStyle,
  DevelopmentSpecProgramType,
  DevelopmentSpecStatus,
  DevelopmentSpecUpsertRequest,
} from '../types'

const router = useRouter()
const store = useDevelopmentSpecStore()
const userStore = useUserStore()

const programTypes: DevelopmentSpecProgramType[] = ['RPGLE', 'SQLRPGLE', 'CLLE', 'DSPF', 'PRTF']
const codeStyles: DevelopmentSpecCodeStyle[] = ['FREE_FORMAT', 'FIXED_FORMAT', 'BOTH']
const statuses: DevelopmentSpecStatus[] = ['DRAFT', 'GENERATED', 'REVIEWED']
const workflowStages = ['Draft Input', 'Generate Spec', 'Review & Export']

const createForm = reactive({
  title: '',
  moduleName: '',
  programType: 'RPGLE' as DevelopmentSpecProgramType,
  codeStyle: 'FREE_FORMAT' as DevelopmentSpecCodeStyle,
  application: '',
  snowGroup: '',
  businessObjective: '',
  implementationObjective: '',
  inputs: '',
  outputs: '',
})

const canManageSpecs = computed(
  () => userStore.isDeveloper || userStore.isTL || userStore.isDevOpsAdmin,
)
const totalPages = computed(() => Math.max(1, Math.ceil(store.total / store.size)))

onMounted(() => {
  store.fetchList()
})

function normalizeMultilineList(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function buildCreatePayload(): DevelopmentSpecUpsertRequest {
  return {
    title: createForm.title.trim(),
    moduleName: createForm.moduleName.trim() || undefined,
    programType: createForm.programType,
    codeStyle: createForm.codeStyle,
    application: createForm.application.trim(),
    snowGroup: createForm.snowGroup.trim(),
    sourcePayload: {
      businessObjective: createForm.businessObjective.trim() || undefined,
      implementationObjective: normalizeMultilineList(createForm.implementationObjective),
      inputs: normalizeMultilineList(createForm.inputs),
      outputs: normalizeMultilineList(createForm.outputs),
    },
  }
}

function resetCreateForm() {
  createForm.title = ''
  createForm.moduleName = ''
  createForm.programType = 'RPGLE'
  createForm.codeStyle = 'FREE_FORMAT'
  createForm.application = ''
  createForm.snowGroup = ''
  createForm.businessObjective = ''
  createForm.implementationObjective = ''
  createForm.inputs = ''
  createForm.outputs = ''
}

async function submitCreate() {
  if (!canManageSpecs.value) return
  const created = await store.createSpec(buildCreatePayload())
  resetCreateForm()
  await store.fetchList()
  await router.push(`/wwa/development-specs/${created.id}`)
}

async function refreshList() {
  await store.fetchList()
}

async function onFilterChange(
  key: 'query' | 'status',
  value: string | DevelopmentSpecStatus | undefined,
) {
  store.setFilter(key, value)
  await store.fetchList()
}

async function onPageChange(newPage: number) {
  store.setPage(newPage)
  await store.fetchList()
}

function goToDetail(id: string) {
  router.push(`/wwa/development-specs/${id}`)
}

function statusBadgeClass(status: DevelopmentSpecStatus) {
  const map: Record<DevelopmentSpecStatus, string> = {
    DRAFT: 'badge-pending',
    GENERATED: 'badge-running',
    REVIEWED: 'badge-approved',
  }
  return map[status]
}

function formatDate(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function formatStatus(status: DevelopmentSpecStatus) {
  return status.toLowerCase().replace(/^./, (char) => char.toUpperCase())
}

function countItems(value: unknown) {
  if (Array.isArray(value)) return value.filter(Boolean).length
  if (typeof value === 'string') return value.trim() ? 1 : 0
  return 0
}

function buildTaskUnits(spec: DevelopmentSpec) {
  return [
    {
      label: 'Business Objective',
      count: countItems(spec.sourcePayload?.businessObjective),
    },
    {
      label: 'Implementation Objectives',
      count: countItems(spec.sourcePayload?.implementationObjective),
    },
    {
      label: 'Inputs',
      count: countItems(spec.sourcePayload?.inputs),
    },
    {
      label: 'Outputs',
      count: countItems(spec.sourcePayload?.outputs),
    },
  ]
}

function getWorkflowStageState(spec: DevelopmentSpec, stageIndex: number) {
  if (stageIndex === 0) return 'done'
  if (stageIndex === 1) return spec.status === 'DRAFT' ? 'current' : 'done'
  if (stageIndex === 2) return spec.status === 'REVIEWED' ? 'done' : spec.status === 'GENERATED' ? 'current' : 'upcoming'
  return 'upcoming'
}

function workflowStageLabel(spec: DevelopmentSpec, stageIndex: number) {
  if (stageIndex === 0) {
    return spec.updatedAt || spec.createdAt ? `Saved ${formatDate(spec.updatedAt ?? spec.createdAt)}` : 'Source draft captured'
  }
  if (stageIndex === 1) {
    return spec.generatedAt ? `Generated ${formatDate(spec.generatedAt)}` : 'Waiting for generation'
  }
  return spec.status === 'REVIEWED' ? 'Ready for delivery' : 'Pending review/export'
}
</script>

<template>
  <div class="summary-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Workspace</p>
        <h1 class="view-title">Development Spec</h1>
        <p class="view-subtitle">
          Draft structured development specifications, generate outputs, and move into review from one workspace.
        </p>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary" type="button" @click="refreshList" :disabled="store.loading">
          Refresh
        </button>
      </div>
    </div>

    <section class="wwa-intro-card" aria-labelledby="wwa-development-spec-intro-title">
      <div class="wwa-intro-kicker">WWA Today</div>
      <h2 id="wwa-development-spec-intro-title" class="wwa-intro-title">Guided spec drafting for IBM i delivery work</h2>
      <p class="wwa-intro-text">
        Phase 1 exposes the full backend Development Spec flow in the browser so teams can create, inspect, generate,
        and export specifications without leaving WWA.
      </p>
    </section>

    <section class="card create-card">
      <div class="section-header">
        <div>
          <h2 class="section-title">Create Development Spec</h2>
          <p class="section-subtitle">Capture the minimum source inputs required by the backend contract.</p>
        </div>
      </div>

      <div v-if="store.error" class="alert alert-error">{{ store.error }}</div>

      <form class="create-form" @submit.prevent="submitCreate">
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">Title</label>
            <input v-model="createForm.title" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving" />
          </div>
          <div class="form-group">
            <label class="form-label">Module Name</label>
            <input v-model="createForm.moduleName" class="form-control" type="text" :disabled="!canManageSpecs || store.saving" />
          </div>
          <div class="form-group">
            <label class="form-label">Program Type</label>
            <select v-model="createForm.programType" class="form-control" :disabled="!canManageSpecs || store.saving">
              <option v-for="programType in programTypes" :key="programType" :value="programType">{{ programType }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Code Style</label>
            <select v-model="createForm.codeStyle" class="form-control" :disabled="!canManageSpecs || store.saving">
              <option v-for="codeStyle in codeStyles" :key="codeStyle" :value="codeStyle">{{ codeStyle }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Application</label>
            <input v-model="createForm.application" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving" />
          </div>
          <div class="form-group">
            <label class="form-label">SNOW Group</label>
            <input v-model="createForm.snowGroup" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Business Objective</label>
          <textarea v-model="createForm.businessObjective" class="form-control multiline-input" rows="3" :disabled="!canManageSpecs || store.saving" />
        </div>

        <div class="form-grid form-grid-longtext">
          <div class="form-group">
            <label class="form-label">Implementation Objectives</label>
            <textarea
              v-model="createForm.implementationObjective"
              class="form-control multiline-input"
              rows="5"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Inputs</label>
            <textarea
              v-model="createForm.inputs"
              class="form-control multiline-input"
              rows="5"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Outputs</label>
            <textarea
              v-model="createForm.outputs"
              class="form-control multiline-input"
              rows="5"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving"
            />
          </div>
        </div>

        <div class="form-actions">
          <button class="btn btn-primary" type="submit" :disabled="!canManageSpecs || store.saving">
            {{ store.saving ? 'Creating...' : 'Create Spec' }}
          </button>
          <p v-if="!canManageSpecs" class="hint-text">Create and edit actions are available to DEVELOPER, TL, and DEVOPS_ADMIN.</p>
        </div>
      </form>
    </section>

    <section class="card">
      <div class="section-header section-header-list">
        <div>
          <h2 class="section-title">Existing Development Specs</h2>
          <p class="section-subtitle">Browse saved specs and open one to edit, generate, or export.</p>
        </div>
        <div class="list-filters">
          <div class="filter-group">
            <label class="form-label">Search</label>
            <input
              class="form-control"
              type="text"
              placeholder="Title, module, application..."
              :value="store.filters.query ?? ''"
              @input="onFilterChange('query', ($event.target as HTMLInputElement).value || undefined)"
            />
          </div>
          <div class="filter-group filter-group-status">
            <label class="form-label">Status</label>
            <select
              class="form-control"
              :value="store.filters.status ?? ''"
              @change="onFilterChange('status', (($event.target as HTMLSelectElement).value || undefined) as DevelopmentSpecStatus | undefined)"
            >
              <option value="">All</option>
              <option v-for="status in statuses" :key="status" :value="status">{{ status }}</option>
            </select>
          </div>
        </div>
      </div>

      <div v-if="store.loading && store.list.length === 0" class="loading-state">
        <span class="spinner"></span>
        <span>Loading development specs...</span>
      </div>

      <div v-else-if="!store.loading && store.list.length === 0" class="empty-state">
        <p>No development specs found.</p>
        <p class="empty-state-detail">Create a new spec to get started.</p>
      </div>

      <template v-else>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Module</th>
                <th>Type</th>
                <th>Scope</th>
                <th>Status</th>
                <th>Generated</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="spec in store.list" :key="spec.id" class="clickable" @click="goToDetail(spec.id)">
                <td>
                  <div class="primary-cell">{{ spec.title }}</div>
                  <div class="secondary-cell">{{ spec.id }}</div>
                </td>
                <td>{{ spec.moduleName || '—' }}</td>
                <td>
                  <div class="primary-cell">{{ spec.programType }}</div>
                  <div class="secondary-cell">{{ spec.codeStyle }}</div>
                </td>
                <td>
                  <div class="primary-cell">{{ spec.application }}</div>
                  <div class="secondary-cell">SNOW: {{ spec.snowGroup }}</div>
                </td>
                <td>
                  <span class="badge" :class="statusBadgeClass(spec.status)">{{ formatStatus(spec.status) }}</span>
                </td>
                <td>{{ formatDate(spec.generatedAt) }}</td>
                <td>{{ formatDate(spec.updatedAt ?? spec.createdAt) }}</td>
              </tr>
            </tbody>
          </table>

          <div class="pagination">
            <span class="pagination-info">{{ store.total }} total | Page {{ store.page + 1 }} of {{ totalPages }}</span>
            <div class="pagination-controls">
              <button class="btn btn-secondary btn-sm" :disabled="store.page === 0" @click="onPageChange(store.page - 1)">
                Prev
              </button>
              <button class="btn btn-secondary btn-sm" :disabled="store.page >= totalPages - 1" @click="onPageChange(store.page + 1)">
                Next
              </button>
            </div>
          </div>
        </div>

        <div class="workflow-section">
          <div class="workflow-header">
            <div>
              <h3 class="workflow-title">Task Units & Agent Workflow</h3>
              <p class="workflow-subtitle">Each spec moves through the same draft, generate, and delivery workflow.</p>
            </div>
          </div>

          <div class="workflow-cards">
            <article
              v-for="spec in store.list"
              :key="`${spec.id}-workflow`"
              class="workflow-card"
              @click="goToDetail(spec.id)"
            >
              <div class="workflow-card-header">
                <div>
                  <div class="primary-cell">{{ spec.title }}</div>
                  <div class="secondary-cell">{{ spec.moduleName || spec.programType }}</div>
                </div>
                <span class="badge" :class="statusBadgeClass(spec.status)">{{ formatStatus(spec.status) }}</span>
              </div>

              <div class="task-unit-list">
                <div v-for="unit in buildTaskUnits(spec)" :key="unit.label" class="task-unit-item">
                  <span class="task-unit-label">{{ unit.label }}</span>
                  <span class="task-unit-value">{{ unit.count }}</span>
                </div>
              </div>

              <div class="workflow-stepper">
                <div
                  v-for="(stage, index) in workflowStages"
                  :key="stage"
                  class="workflow-step"
                  :class="`workflow-step-${getWorkflowStageState(spec, index)}`"
                >
                  <div class="workflow-node">{{ index + 1 }}</div>
                  <div class="workflow-step-body">
                    <div class="workflow-step-title">{{ stage }}</div>
                    <div class="workflow-step-caption">{{ workflowStageLabel(spec, index) }}</div>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </template>
    </section>
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

.create-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-header-list {
  align-items: flex-end;
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

.create-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-grid-longtext {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.multiline-input {
  resize: vertical;
  min-height: 96px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.hint-text {
  font-size: 12px;
  color: #64748b;
}

.list-filters {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-group {
  min-width: 220px;
}

.filter-group-status {
  min-width: 160px;
}

.primary-cell {
  font-weight: 600;
  color: #0f172a;
}

.secondary-cell {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.empty-state-detail {
  margin-top: 8px;
  font-size: 12px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pagination-info {
  color: #64748b;
  font-size: 13px;
}

.workflow-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-subtle);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflow-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.workflow-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.workflow-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.workflow-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.workflow-card {
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: #fff;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  cursor: pointer;
}

.workflow-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-unit-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.task-unit-item {
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.95);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-unit-label {
  font-size: 12px;
  color: #64748b;
}

.task-unit-value {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.workflow-stepper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.workflow-step:not(:last-child) {
  position: relative;
}

.workflow-step:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 13px;
  top: 32px;
  width: 2px;
  height: calc(100% + 4px);
  background: var(--color-border-subtle);
}

.workflow-node {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid var(--color-border-subtle);
  background: #fff;
  color: #475569;
  position: relative;
  z-index: 1;
}

.workflow-step-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.workflow-step-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.workflow-step-caption {
  font-size: 12px;
  color: #64748b;
}

.workflow-step-done .workflow-node {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.35);
  color: #166534;
}

.workflow-step-current .workflow-node {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.35);
  color: #1d4ed8;
}

.workflow-step-upcoming .workflow-node {
  background: #fff;
  color: #94a3b8;
}

@media (max-width: 1100px) {
  .form-grid,
  .form-grid-longtext,
  .workflow-cards,
  .task-unit-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .view-header,
  .section-header,
  .section-header-list,
  .workflow-header,
  .workflow-card-header,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group,
  .filter-group-status {
    min-width: 0;
  }
}
</style>
