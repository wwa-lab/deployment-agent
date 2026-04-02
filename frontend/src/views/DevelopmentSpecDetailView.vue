<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDevelopmentSpecStore } from '../stores/developmentSpec'
import { useUserStore } from '../stores/user'
import type {
  DevelopmentSpec,
  DevelopmentSpecCodeStyle,
  DevelopmentSpecProgramType,
  DevelopmentSpecUpsertRequest,
} from '../types'

const route = useRoute()
const router = useRouter()
const store = useDevelopmentSpecStore()
const userStore = useUserStore()

const programTypes: DevelopmentSpecProgramType[] = ['RPGLE', 'SQLRPGLE', 'CLLE', 'DSPF', 'PRTF']
const codeStyles: DevelopmentSpecCodeStyle[] = ['FREE_FORMAT', 'FIXED_FORMAT', 'BOTH']

const form = reactive({
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

const specId = computed(() => route.params.id as string)
const canManageSpecs = computed(
  () => userStore.isDeveloper || userStore.isTL || userStore.isDevOpsAdmin,
)
const generatedPayloadJson = computed(() => {
  if (!store.detail?.generatedPayload) return ''
  return JSON.stringify(store.detail.generatedPayload, null, 2)
})

watch(
  specId,
  async (id) => {
    await store.selectSpec(id)
    syncFormFromDetail(store.detail)
  },
  { immediate: true },
)

function syncFormFromDetail(detail: DevelopmentSpec | null) {
  form.title = detail?.title ?? ''
  form.moduleName = detail?.moduleName ?? ''
  form.programType = detail?.programType ?? 'RPGLE'
  form.codeStyle = detail?.codeStyle ?? 'FREE_FORMAT'
  form.application = detail?.application ?? ''
  form.snowGroup = detail?.snowGroup ?? ''
  form.businessObjective = detail?.sourcePayload?.businessObjective ?? ''
  form.implementationObjective = (detail?.sourcePayload?.implementationObjective ?? []).join('\n')
  form.inputs = (detail?.sourcePayload?.inputs ?? []).join('\n')
  form.outputs = (detail?.sourcePayload?.outputs ?? []).join('\n')
}

function normalizeMultilineList(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function buildPayload(): DevelopmentSpecUpsertRequest {
  return {
    title: form.title.trim(),
    moduleName: form.moduleName.trim() || undefined,
    programType: form.programType,
    codeStyle: form.codeStyle,
    application: form.application.trim(),
    snowGroup: form.snowGroup.trim(),
    sourcePayload: {
      businessObjective: form.businessObjective.trim() || undefined,
      implementationObjective: normalizeMultilineList(form.implementationObjective),
      inputs: normalizeMultilineList(form.inputs),
      outputs: normalizeMultilineList(form.outputs),
    },
    version: store.detail?.version,
  }
}

async function saveSpec() {
  if (!canManageSpecs.value) return
  const saved = await store.saveSpec(buildPayload())
  syncFormFromDetail(saved ?? store.detail)
}

async function generateSpec() {
  if (!canManageSpecs.value) return
  const generated = await store.runGenerate()
  syncFormFromDetail(generated ?? store.detail)
}

async function exportSpec(format: 'markdown' | 'json') {
  const result = await store.downloadExport(format)
  const url = window.URL.createObjectURL(result.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = result.filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

async function reloadSpec() {
  await store.selectSpec(specId.value)
  syncFormFromDetail(store.detail)
}

function formatDate(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div class="detail-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Workspace</p>
        <h1 class="view-title">{{ store.detail?.title || 'Development Spec' }}</h1>
        <p class="view-subtitle">
          Review source inputs, generate structured output, and export the spec for downstream delivery.
        </p>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary" type="button" @click="router.push('/wwa/development-specs')">Back to List</button>
        <button class="btn btn-secondary" type="button" @click="reloadSpec" :disabled="store.loading">Refresh</button>
      </div>
    </div>

    <div v-if="store.error" class="alert alert-error">{{ store.error }}</div>

    <div v-if="store.loading && !store.detail" class="loading-state">
      <span class="spinner"></span>
      <span>Loading development spec...</span>
    </div>

    <template v-else-if="store.detail">
      <section class="card summary-card">
        <div class="summary-grid">
          <div>
            <div class="summary-label">Status</div>
            <div class="summary-value">
              <span class="badge" :class="{
                'badge-pending': store.detail.status === 'DRAFT',
                'badge-running': store.detail.status === 'GENERATED',
                'badge-approved': store.detail.status === 'REVIEWED',
              }">
                {{ store.detail.status }}
              </span>
            </div>
          </div>
          <div>
            <div class="summary-label">Generated At</div>
            <div class="summary-value">{{ formatDate(store.detail.generatedAt) }}</div>
          </div>
          <div>
            <div class="summary-label">Generated By</div>
            <div class="summary-value">{{ store.detail.generatedBy || '—' }}</div>
          </div>
          <div>
            <div class="summary-label">Updated At</div>
            <div class="summary-value">{{ formatDate(store.detail.updatedAt ?? store.detail.createdAt) }}</div>
          </div>
        </div>
      </section>

      <section class="card editor-card">
        <div class="section-header">
          <div>
            <h2 class="section-title">Source Draft</h2>
            <p class="section-subtitle">Update the backend-backed draft fields and save a new version.</p>
          </div>
          <div class="section-actions">
            <button class="btn btn-primary" type="button" @click="saveSpec" :disabled="!canManageSpecs || store.saving">
              {{ store.saving ? 'Saving...' : 'Save Changes' }}
            </button>
            <button class="btn btn-secondary" type="button" @click="generateSpec" :disabled="!canManageSpecs || store.generating">
              {{ store.generating ? 'Generating...' : 'Generate' }}
            </button>
            <button class="btn btn-secondary" type="button" @click="exportSpec('markdown')" :disabled="store.exporting">
              Export Markdown
            </button>
            <button class="btn btn-secondary" type="button" @click="exportSpec('json')" :disabled="store.exporting">
              Export JSON
            </button>
          </div>
        </div>

        <div v-if="!canManageSpecs" class="alert alert-info">
          Edit and generate actions are available to DEVELOPER, TL, and DEVOPS_ADMIN.
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">Title</label>
            <input v-model="form.title" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving || store.generating" />
          </div>
          <div class="form-group">
            <label class="form-label">Module Name</label>
            <input v-model="form.moduleName" class="form-control" type="text" :disabled="!canManageSpecs || store.saving || store.generating" />
          </div>
          <div class="form-group">
            <label class="form-label">Program Type</label>
            <select v-model="form.programType" class="form-control" :disabled="!canManageSpecs || store.saving || store.generating">
              <option v-for="programType in programTypes" :key="programType" :value="programType">{{ programType }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Code Style</label>
            <select v-model="form.codeStyle" class="form-control" :disabled="!canManageSpecs || store.saving || store.generating">
              <option v-for="codeStyle in codeStyles" :key="codeStyle" :value="codeStyle">{{ codeStyle }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Application</label>
            <input v-model="form.application" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving || store.generating" />
          </div>
          <div class="form-group">
            <label class="form-label">SNOW Group</label>
            <input v-model="form.snowGroup" class="form-control" type="text" required :disabled="!canManageSpecs || store.saving || store.generating" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Business Objective</label>
          <textarea v-model="form.businessObjective" class="form-control multiline-input" rows="4" :disabled="!canManageSpecs || store.saving || store.generating" />
        </div>

        <div class="form-grid form-grid-longtext">
          <div class="form-group">
            <label class="form-label">Implementation Objectives</label>
            <textarea
              v-model="form.implementationObjective"
              class="form-control multiline-input"
              rows="6"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving || store.generating"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Inputs</label>
            <textarea
              v-model="form.inputs"
              class="form-control multiline-input"
              rows="6"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving || store.generating"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Outputs</label>
            <textarea
              v-model="form.outputs"
              class="form-control multiline-input"
              rows="6"
              placeholder="One item per line"
              :disabled="!canManageSpecs || store.saving || store.generating"
            />
          </div>
        </div>
      </section>

      <section class="card result-card">
        <div class="section-header">
          <div>
            <h2 class="section-title">Generated Output</h2>
            <p class="section-subtitle">Rendered content and structured payload returned by the backend generator.</p>
          </div>
        </div>

        <div class="result-grid">
          <div class="result-panel">
            <div class="result-panel-title">Generated Content</div>
            <pre class="result-block">{{ store.detail.generatedContent || 'No generated content yet.' }}</pre>
          </div>
          <div class="result-panel">
            <div class="result-panel-title">Generated Payload</div>
            <pre class="result-block">{{ generatedPayloadJson || 'No generated payload yet.' }}</pre>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-view {
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

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.view-title {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.view-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: #475569;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-card,
.editor-card,
.result-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #64748b;
}

.summary-value {
  margin-top: 6px;
  color: #0f172a;
  font-size: 14px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
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
  min-height: 112px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-panel-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.result-block {
  margin: 0;
  padding: 14px;
  min-height: 280px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
  color: #0f172a;
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

@media (max-width: 1200px) {
  .summary-grid,
  .result-grid,
  .form-grid,
  .form-grid-longtext {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .view-header,
  .section-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
