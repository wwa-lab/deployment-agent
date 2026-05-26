<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getAgentContributionDashboardStatuses,
  updateAgentContributionDashboardStatuses,
  type StageImplementationStatus,
} from '../api/agentContributionDashboard'
import dashboardData from '../config/agentContributionDashboard.json'
import { useUserStore } from '../stores/user'

type Workstream = {
  name: string
  agentName: string
  subAgentOwner: string
  processOwner: string
  technicalLeader: string
  coBuild: string[]
  contribution: string
}

type StageResourceLink = {
  label: string
  description: string
  href: string
}

type SdlcStage = {
  key: string
  name: string
  focus: string
  implementationStatus: StageImplementationStatus
  implementationLabel: string
  implementationNote: string
  description: string
  agentOwner: string
  gate: string
  resourceLinks: StageResourceLink[]
  workstreams: Workstream[]
}

type DashboardData = {
  summary: {
    updatedAt: string
    evidenceSource: string
    cultureGoal: string
  }
  stages: SdlcStage[]
}

const STATUS_OPTIONS: Array<{ value: StageImplementationStatus; label: string; note: string }> = [
  {
    value: 'implemented',
    label: 'Implemented',
    note: 'This stage is available in the current platform baseline.',
  },
  {
    value: 'in-progress',
    label: 'In Progress',
    note: 'This stage is being built out in the current platform baseline.',
  },
  {
    value: 'backlog',
    label: 'Backlog',
    note: 'This stage is planned and tracked in the backlog, but implementation has not started.',
  },
  {
    value: 'not-implemented',
    label: 'Not Implemented',
    note: 'This stage is part of the target SDLC map, but it is not implemented in the current platform baseline.',
  },
]

const STATUS_BY_VALUE = new Map(STATUS_OPTIONS.map((option) => [option.value, option]))

const dashboard = dashboardData as DashboardData
const userStore = useUserStore()
const canEdit = computed(() => userStore.isDevOpsAdmin)
const statusOverrides = ref<Record<string, StageImplementationStatus>>({})
const statusUpdatedBy = ref('')
const statusUpdatedAt = ref('')
const loadingStatuses = ref(false)
const savingStatus = ref(false)
const statusError = ref('')
const statusSaved = ref(false)
const editableStatus = ref<StageImplementationStatus>('implemented')
const activeStatusFilter = ref<StageImplementationStatus | 'all'>('all')

const stages = computed<SdlcStage[]>(() =>
  dashboard.stages.map((stage) => {
    const implementationStatus = statusOverrides.value[stage.key] ?? stage.implementationStatus
    const statusCopy = STATUS_BY_VALUE.get(implementationStatus) ?? STATUS_BY_VALUE.get('not-implemented')

    return {
      ...stage,
      implementationStatus,
      implementationLabel: statusCopy?.label ?? 'Not Implemented',
      implementationNote: statusCopy?.note ?? stage.implementationNote,
    }
  }),
)
const selectedStageKey = ref(stages.value[0]?.key ?? '')

const selectedStage = computed(() => stages.value.find((stage) => stage.key === selectedStageKey.value))

const filteredStages = computed(() => {
  if (activeStatusFilter.value === 'all') {
    return stages.value
  }
  return stages.value.filter((stage) => stage.implementationStatus === activeStatusFilter.value)
})

const workstreamCount = computed(() =>
  stages.value.reduce((total, stage) => total + stage.workstreams.length, 0),
)

const implementedStageCount = computed(
  () => stages.value.filter((stage) => stage.implementationStatus === 'implemented').length,
)

const inProgressStageCount = computed(
  () => stages.value.filter((stage) => stage.implementationStatus === 'in-progress').length,
)

const backlogStageCount = computed(
  () => stages.value.filter((stage) => stage.implementationStatus === 'backlog').length,
)

const notImplementedStageCount = computed(
  () => stages.value.filter((stage) => stage.implementationStatus === 'not-implemented').length,
)

const hasStatusChange = computed(
  () => Boolean(selectedStage.value) && editableStatus.value !== selectedStage.value?.implementationStatus,
)

const statusUpdatedSummary = computed(() => {
  if (!statusUpdatedBy.value || !statusUpdatedAt.value) return ''
  return `Status updated by ${statusUpdatedBy.value} at ${formatTimestamp(statusUpdatedAt.value)}`
})

onMounted(() => {
  void loadStageStatuses()
})

watch(
  selectedStage,
  (stage) => {
    if (!stage) return
    editableStatus.value = stage.implementationStatus
    statusError.value = ''
    statusSaved.value = false
  },
  { immediate: true },
)

function selectStage(stageKey: string) {
  selectedStageKey.value = stageKey
}

function selectStatusFilter(status: StageImplementationStatus | 'all') {
  activeStatusFilter.value = status
  const selectedIsVisible = filteredStages.value.some((stage) => stage.key === selectedStageKey.value)
  if (!selectedIsVisible) {
    selectedStageKey.value = filteredStages.value[0]?.key ?? ''
  }
}

function contributionItemLabel(count: number) {
  return `${count} contribution item${count === 1 ? '' : 's'}`
}

function stageProcessOwners(stage: SdlcStage) {
  return uniqueValues(stage.workstreams.map((workstream) => workstream.processOwner)).join(', ')
}

function stagePrimaryProcessOwner(stage: SdlcStage) {
  return uniqueValues(stage.workstreams.map((workstream) => workstream.processOwner))[0] ?? 'Unassigned'
}

function stageTechnicalLeaders(stage: SdlcStage) {
  return uniqueValues(stage.workstreams.map((workstream) => workstream.technicalLeader)).join(', ')
}

function stagePrimaryTechnicalLeader(stage: SdlcStage) {
  return uniqueValues(stage.workstreams.map((workstream) => workstream.technicalLeader))[0] ?? 'Unassigned'
}

function stageCoBuildSummary(stage: SdlcStage) {
  const count = uniqueValues(stage.workstreams.flatMap((workstream) => workstream.coBuild)).length
  return `${count} partner${count === 1 ? '' : 's'}`
}

function uniqueValues(values: string[]) {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))]
}

async function loadStageStatuses() {
  loadingStatuses.value = true
  statusError.value = ''
  try {
    const response = await getAgentContributionDashboardStatuses()
    statusOverrides.value = normalizeStatuses(response.statuses)
    statusUpdatedBy.value = response.updatedBy ?? ''
    statusUpdatedAt.value = response.updatedAt ?? ''
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : 'Failed to load dashboard status overrides'
  } finally {
    loadingStatuses.value = false
  }
}

async function saveSelectedStageStatus() {
  const stage = selectedStage.value
  if (!stage || !canEdit.value) return

  savingStatus.value = true
  statusError.value = ''
  statusSaved.value = false
  try {
    const statuses = Object.fromEntries(
      stages.value.map((item) => [item.key, item.implementationStatus]),
    ) as Record<string, StageImplementationStatus>
    statuses[stage.key] = editableStatus.value
    const response = await updateAgentContributionDashboardStatuses(statuses)
    statusOverrides.value = normalizeStatuses(response.statuses)
    statusUpdatedBy.value = response.updatedBy ?? ''
    statusUpdatedAt.value = response.updatedAt ?? ''
    statusSaved.value = true
    setTimeout(() => { statusSaved.value = false }, 3000)
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : 'Failed to save dashboard status'
  } finally {
    savingStatus.value = false
  }
}

function normalizeStatuses(statuses: Record<string, StageImplementationStatus> | undefined) {
  const allowed = new Set(STATUS_OPTIONS.map((option) => option.value))
  return Object.fromEntries(
    Object.entries(statuses ?? {}).filter(([, status]) => allowed.has(status)),
  ) as Record<string, StageImplementationStatus>
}

function formatTimestamp(value: string) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return `${parsed.toISOString().slice(0, 16).replace('T', ' ')} UTC`
}
</script>

<template>
  <div class="agent-contribution-dashboard">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">Agent Contribute Dashboard</h1>
        <p class="view-subtitle">
          Qilianshan SDLC coverage, ownership, and contribution accountability.
        </p>
      </div>
      <div class="header-meta">
        <span>{{ dashboard.summary.evidenceSource }}</span>
        <strong>{{ dashboard.summary.updatedAt }}</strong>
        <small v-if="statusUpdatedSummary">{{ statusUpdatedSummary }}</small>
      </div>
    </div>

    <section class="wwa-intro-card compact-intro" aria-labelledby="agent-contribution-intro-title">
      <div class="wwa-intro-kicker">Overview</div>
      <h2 id="agent-contribution-intro-title" class="wwa-intro-title">
        SDLC coverage and ownership map
      </h2>
      <p class="wwa-intro-text">
        Track Qilianshan SDLC status, accountability, and contribution coverage in one view.
      </p>
    </section>

    <section class="status-summary card" aria-label="Qilianshan SDLC status summary">
      <article class="metric-card">
        <span class="metric-label">Implemented</span>
        <strong>{{ implementedStageCount }}</strong>
      </article>
      <article class="metric-card metric-progress">
        <span class="metric-label">In Progress</span>
        <strong>{{ inProgressStageCount }}</strong>
      </article>
      <article class="metric-card metric-backlog">
        <span class="metric-label">Backlog</span>
        <strong>{{ backlogStageCount }}</strong>
      </article>
      <article class="metric-card metric-warning">
        <span class="metric-label">Not Implemented</span>
        <strong>{{ notImplementedStageCount }}</strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">Contribution Items</span>
        <strong>{{ workstreamCount }}</strong>
      </article>
    </section>

    <section class="flow-map-card" aria-labelledby="coverage-map-title">
      <div class="section-header">
        <div>
          <h2 id="coverage-map-title" class="section-title">SDLC Coverage Map</h2>
          <p class="section-subtitle">
            End-to-end stage flow with status, owner, and contribution item count.
          </p>
        </div>
        <span>{{ stages.length }} stages</span>
      </div>
      <div class="flow-map-scroll">
        <div class="flow-map">
          <button
            v-for="(stage, index) in stages"
            :key="stage.key"
            class="flow-node"
            :class="[
              stage.implementationStatus,
              {
                selected: selectedStage?.key === stage.key,
                dimmed:
                  activeStatusFilter !== 'all' &&
                  activeStatusFilter !== stage.implementationStatus,
              },
            ]"
            type="button"
            @click="selectStage(stage.key)"
          >
            <span class="flow-index">{{ index + 1 }}</span>
            <span class="flow-copy">
              <strong>{{ stage.name }}</strong>
              <em>{{ stage.implementationLabel }}</em>
              <small>{{ stage.agentOwner }}</small>
            </span>
            <span class="flow-items">{{ stage.workstreams.length }} items</span>
          </button>
        </div>
      </div>
    </section>

    <div class="coverage-layout">
      <section class="matrix-card" aria-labelledby="coverage-matrix-title">
        <div class="section-header">
          <div>
            <h2 id="coverage-matrix-title" class="section-title">SDLC Coverage Matrix</h2>
            <p class="section-subtitle">
              Filter by status and select a stage for owner details.
            </p>
          </div>
          <div class="status-filter" aria-label="Filter SDLC stages by implementation status">
            <button
              type="button"
              :class="{ active: activeStatusFilter === 'all' }"
              @click="selectStatusFilter('all')"
            >
              All
            </button>
            <button
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              type="button"
              :class="{ active: activeStatusFilter === option.value }"
              @click="selectStatusFilter(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="table-scroll">
          <table class="data-table coverage-table">
            <thead>
              <tr>
                <th>Stage</th>
                <th>Implementation Status</th>
                <th>Agent Owner</th>
                <th>Items</th>
                <th>Ownership</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="stage in filteredStages"
                :key="stage.key"
                class="clickable"
                :class="{ selected: selectedStage?.key === stage.key }"
                tabindex="0"
                @click="selectStage(stage.key)"
                @keydown.enter.prevent="selectStage(stage.key)"
              >
                <td class="stage-cell">
                  <span class="stage-index">{{ stages.findIndex((item) => item.key === stage.key) + 1 }}</span>
                  <div>
                    <strong>{{ stage.name }}</strong>
                    <small>{{ stage.focus }}</small>
                  </div>
                </td>
                <td>
                  <span class="status-badge" :class="stage.implementationStatus">
                    {{ stage.implementationLabel }}
                  </span>
                </td>
                <td class="owner-cell">{{ stage.agentOwner }}</td>
                <td class="item-count">{{ stage.workstreams.length }}</td>
                <td class="ownership-cell">
                  <span>Process: {{ stagePrimaryProcessOwner(stage) }}</span>
                  <span>Tech: {{ stagePrimaryTechnicalLeader(stage) }}</span>
                </td>
              </tr>
              <tr v-if="filteredStages.length === 0">
                <td class="empty-row" colspan="5">
                  No stages match the selected status.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside v-if="selectedStage" class="stage-panel" aria-label="Selected stage details">
        <div class="panel-header">
          <div>
            <p class="panel-kicker">Selected Stage</p>
            <h2>{{ selectedStage.name }}</h2>
          </div>
          <span class="status-badge" :class="selectedStage.implementationStatus">
            {{ selectedStage.implementationLabel }}
          </span>
        </div>

        <p class="stage-description">{{ selectedStage.description }}</p>

        <section class="panel-section resource-section">
          <div class="section-header compact">
            <div>
              <h3 class="section-title">Confluence Links</h3>
              <p class="section-subtitle">Open guidelines or leave feedback for this stage.</p>
            </div>
          </div>
          <div class="resource-list">
            <a
              v-for="resource in selectedStage.resourceLinks"
              :key="resource.label"
              class="resource-link"
              :href="resource.href"
              target="_blank"
              rel="noopener noreferrer"
            >
              <span>{{ resource.label }}</span>
              <strong>{{ resource.description }}</strong>
            </a>
          </div>
        </section>

        <section class="panel-section">
          <div class="status-card" :class="selectedStage.implementationStatus">
            <span>Implementation Status</span>
            <p>{{ selectedStage.implementationNote }}</p>
          </div>

          <div class="admin-status-panel" :class="{ readonly: !canEdit }">
            <div>
              <span>{{ canEdit ? 'Admin Controls' : 'Read-only Status' }}</span>
              <strong>
                {{
                  canEdit
                    ? 'Update selected stage status'
                    : 'DEVOPS_ADMIN can edit stage status.'
                }}
              </strong>
            </div>
            <div v-if="canEdit" class="admin-status-controls">
              <select
                v-model="editableStatus"
                class="form-control"
                :disabled="loadingStatuses || savingStatus"
                aria-label="Selected stage implementation status"
              >
                <option
                  v-for="option in STATUS_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
              <button
                class="btn btn-primary btn-sm"
                type="button"
                :disabled="loadingStatuses || savingStatus || !hasStatusChange"
                @click="saveSelectedStageStatus"
              >
                {{ savingStatus ? 'Saving...' : 'Save Status' }}
              </button>
            </div>
            <p v-if="statusError" class="status-message error">{{ statusError }}</p>
            <p v-else-if="statusSaved" class="status-message success">Saved.</p>
          </div>
        </section>

        <section class="panel-section accountability-section">
          <div class="section-header compact">
            <div>
              <h3 class="section-title">Accountability</h3>
              <p class="section-subtitle">Owners and Co-Build coverage for this stage.</p>
            </div>
          </div>
          <div class="stage-facts">
            <div>
              <span>Agent Owner</span>
              <strong>{{ selectedStage.agentOwner }}</strong>
            </div>
            <div>
              <span>Process Owners</span>
              <strong>{{ stageProcessOwners(selectedStage) }}</strong>
            </div>
            <div>
              <span>Technical Leaders</span>
              <strong>{{ stageTechnicalLeaders(selectedStage) }}</strong>
            </div>
            <div>
              <span>Co-Build Partners</span>
              <strong>{{ stageCoBuildSummary(selectedStage) }}</strong>
            </div>
            <div class="wide">
              <span>I-E-O-V Gate</span>
              <strong>{{ selectedStage.gate }}</strong>
            </div>
          </div>
        </section>

        <section class="panel-section contribution-section">
          <div class="section-header compact">
            <div>
              <h3 class="section-title">Contribution Coverage</h3>
              <p class="section-subtitle">
                {{ contributionItemLabel(selectedStage.workstreams.length) }}
              </p>
            </div>
          </div>

          <div class="contribution-list">
            <article
              v-for="workstream in selectedStage.workstreams"
              :key="workstream.name"
              class="contribution-row"
            >
              <div class="contribution-row-main">
                <div>
                  <h4>{{ workstream.name }}</h4>
                  <span>Covered by {{ workstream.agentName }}</span>
                </div>
                <p>{{ workstream.contribution }}</p>
              </div>

              <dl class="role-list">
                <div>
                  <dt>Sub-agent Owner</dt>
                  <dd>{{ workstream.subAgentOwner }}</dd>
                </div>
                <div>
                  <dt>Process Owner</dt>
                  <dd>{{ workstream.processOwner }}</dd>
                </div>
                <div>
                  <dt>Technical Leader</dt>
                  <dd>{{ workstream.technicalLeader }}</dd>
                </div>
              </dl>

              <div class="co-build-row">
                <span>Co-Build Partners</span>
                <div>
                  <strong
                    v-for="member in workstream.coBuild"
                    :key="member"
                  >
                    {{ member }}
                  </strong>
                </div>
              </div>
            </article>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.agent-contribution-dashboard {
  max-width: 1180px;
  margin: 0 auto;
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

.view-eyebrow,
.panel-kicker,
.status-card span,
.metric-label,
.stage-facts span,
.admin-status-panel span,
.co-build-row > span,
.role-list dt {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.view-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.view-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.header-meta {
  min-width: 220px;
  text-align: right;
  color: var(--color-text-muted);
  font-size: 12px;
}

.header-meta strong {
  display: block;
  margin-top: 4px;
  color: #2563eb;
}

.header-meta small {
  display: block;
  margin-top: 8px;
  color: var(--color-text-muted);
}

.compact-intro {
  padding: 12px 16px;
}

.status-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.metric-card {
  padding: 14px 16px;
  border-right: 1px solid rgba(227, 234, 247, 0.88);
}

.metric-card:last-child {
  border-right: 0;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  line-height: 1;
  color: var(--color-text-primary);
}

.metric-progress strong {
  color: #2563eb;
}

.metric-backlog strong {
  color: #475569;
}

.metric-warning strong {
  color: #b45309;
}

.coverage-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
  gap: 16px;
  align-items: start;
}

.flow-map-card,
.matrix-card,
.stage-panel {
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.88);
}

.section-header.compact {
  padding: 0 0 10px;
  border-bottom: 0;
}

.section-title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
}

.section-subtitle {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.section-header > span {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.status-filter {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.status-filter button {
  min-height: 28px;
  padding: 4px 9px;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.status-filter button.active {
  background: #172235;
  border-color: #172235;
  color: #ffffff;
}

.flow-map-scroll {
  overflow-x: auto;
  padding: 14px 16px 16px;
}

.flow-map {
  display: grid;
  grid-template-columns: repeat(7, minmax(136px, 1fr));
  gap: 18px;
  min-width: 980px;
}

.flow-node {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  grid-template-rows: auto auto;
  gap: 8px 9px;
  min-height: 122px;
  padding: 12px;
  border: 1px solid rgba(227, 234, 247, 0.94);
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  color: var(--color-text-primary);
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
}

.flow-node::after {
  content: '';
  position: absolute;
  top: 50%;
  right: -18px;
  width: 18px;
  height: 1px;
  background: rgba(216, 227, 243, 0.58);
}

.flow-node:last-child::after {
  display: none;
}

.flow-node:hover,
.flow-node.selected {
  border-color: #93c5fd;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.1);
}

.flow-node.selected {
  background: #f8fbff;
}

.flow-node.dimmed {
  opacity: 0.42;
}

.flow-index {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #172235;
  color: #e5eefc;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
}

.flow-copy {
  min-width: 0;
}

.flow-copy strong,
.flow-copy em,
.flow-copy small {
  display: block;
}

.flow-copy strong {
  font-size: 14px;
}

.flow-copy em {
  width: fit-content;
  margin-top: 6px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-style: normal;
  font-weight: 850;
}

.flow-copy small {
  margin-top: 7px;
  color: var(--color-text-secondary);
  font-weight: 700;
  line-height: 1.25;
}

.flow-items {
  grid-column: 1 / -1;
  align-self: end;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.flow-node.implemented .flow-copy em {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.flow-node.in-progress .flow-copy em {
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.flow-node.backlog .flow-copy em {
  background: #f8fafc;
  color: #475569;
  border: 1px solid #cbd5e1;
}

.flow-node.not-implemented .flow-copy em {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}

.table-scroll {
  overflow-x: auto;
}

.coverage-table {
  border: 0;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
}

.coverage-table tbody tr.selected {
  background: rgba(236, 242, 255, 0.92);
  box-shadow: inset 3px 0 0 #2563eb;
}

.coverage-table tbody tr:focus {
  outline: 2px solid rgba(37, 99, 235, 0.25);
  outline-offset: -2px;
}

.empty-row {
  padding: 26px 16px;
  text-align: center;
  color: var(--color-text-muted);
  font-weight: 650;
}

.stage-cell {
  min-width: 190px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.stage-cell strong,
.stage-cell small {
  display: block;
}

.stage-cell small {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-weight: 700;
}

.stage-index {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #172235;
  color: #e5eefc;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
}

.owner-cell {
  min-width: 150px;
  font-weight: 650;
  color: var(--color-text-primary);
}

.item-count {
  text-align: center;
  font-family: var(--font-mono);
  font-weight: 800;
  color: #2563eb;
}

.ownership-cell {
  min-width: 220px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.ownership-cell span {
  display: block;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-style: normal;
  font-weight: 850;
  line-height: 1.2;
}

.status-badge.implemented,
.status-value.implemented {
  color: #047857;
}

.status-badge.implemented {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.status-badge.in-progress,
.status-value.in-progress {
  color: #1d4ed8;
}

.status-badge.in-progress {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.status-badge.backlog,
.status-value.backlog {
  color: #475569;
}

.status-badge.backlog {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
}

.status-badge.not-implemented,
.status-value.not-implemented {
  color: #b45309;
}

.status-badge.not-implemented {
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.stage-panel {
  position: sticky;
  top: 16px;
  padding: 16px;
  max-height: calc(100vh - 104px);
  overflow-y: auto;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-header h2,
.contribution-row h4 {
  margin: 0;
  color: var(--color-text-primary);
}

.stage-description {
  margin: 10px 0 0;
  color: var(--color-text-secondary);
}

.status-value {
  font-size: 14px;
}

.panel-section {
  margin-top: 14px;
}

.resource-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.resource-link {
  display: block;
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid rgba(191, 219, 254, 0.9);
  border-radius: 8px;
  background: #ffffff;
  color: var(--color-text-primary);
  text-decoration: none;
  transition: background-color 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.resource-link:hover,
.resource-link:focus {
  background: #f8fbff;
  border-color: #93c5fd;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.08);
  outline: none;
}

.resource-link span {
  display: block;
  color: #2563eb;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.resource-link strong {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.35;
}

.status-card {
  padding: 12px 14px;
  border-radius: 8px;
}

.status-card.implemented {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.status-card.in-progress {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.status-card.backlog {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
}

.status-card.not-implemented {
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.status-card p {
  margin: 5px 0 0;
  color: var(--color-text-secondary);
  font-weight: 650;
}

.stage-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid rgba(227, 234, 247, 0.88);
  border-radius: 8px;
  background: #ffffff;
}

.stage-facts .wide {
  grid-column: 1 / -1;
}

.stage-facts strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text-primary);
  font-size: 12px;
}

.admin-status-panel {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid rgba(191, 219, 254, 0.9);
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.admin-status-panel.readonly {
  background: #f9fafb;
  border-color: rgba(227, 234, 247, 0.92);
}

.admin-status-panel strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text-primary);
  font-size: 12px;
}

.admin-status-controls {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.admin-status-controls select {
  min-width: 0;
  flex: 1;
  font-weight: 700;
}

.admin-status-controls button {
  white-space: nowrap;
}

.status-message {
  margin: 8px 0 0;
  font-size: 12px;
  font-weight: 800;
}

.status-message.error {
  color: #b91c1c;
}

.status-message.success {
  color: #047857;
}

.contribution-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.contribution-row {
  padding: 12px 0;
  border-top: 1px solid rgba(227, 234, 247, 0.88);
}

.contribution-row:first-child {
  border-top: 0;
}

.contribution-row-main span {
  display: block;
  margin-top: 3px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.contribution-row-main p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
}

.role-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.role-list dd {
  margin: 3px 0 0;
  color: var(--color-text-primary);
  font-size: 12px;
  font-weight: 750;
}

.co-build-row {
  margin-top: 12px;
}

.co-build-row div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 7px;
}

.co-build-row strong {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f0fdfa;
  border: 1px solid #99f6e4;
  color: #0f766e;
  font-size: 12px;
}

@media (max-width: 1120px) {
  .coverage-layout {
    grid-template-columns: 1fr;
  }

  .stage-panel {
    position: static;
    max-height: none;
  }
}

@media (max-width: 920px) {
  .status-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-card:nth-child(3) {
    border-right: 0;
  }

  .metric-card:nth-child(n + 4) {
    border-top: 1px solid rgba(227, 234, 247, 0.88);
  }
}

@media (max-width: 760px) {
  .view-header,
  .section-header,
  .admin-status-panel {
    flex-direction: column;
  }

  .header-meta {
    text-align: left;
  }

  .admin-status-controls {
    width: 100%;
    flex-wrap: wrap;
  }

  .admin-status-controls select,
  .admin-status-controls button {
    width: 100%;
  }

  .status-summary,
  .stage-facts,
  .role-list,
  .resource-list {
    grid-template-columns: 1fr;
  }

  .metric-card {
    border-right: 0;
    border-top: 1px solid rgba(227, 234, 247, 0.88);
  }

  .metric-card:first-child {
    border-top: 0;
  }
}
</style>
