<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { downloadTemplate } from '../api/template'
import type { CreateTemplateDraft } from '../types'

const props = defineProps<{
  agents: string[]
  categories: string[]
  snowGroups: string[]
  applications: string[]
  sites: string[]
  mode?: 'create' | 'edit'
  initialDraft?: Partial<CreateTemplateDraft>
}>()

const emit = defineEmits<{
  close: []
  submit: [draft: CreateTemplateDraft]
}>()

const userStore = useUserStore()

const isEditMode = computed(() => props.mode === 'edit')
const supportsUpload = computed(() => !isEditMode.value)
const activeTab = ref<'manual' | 'upload'>('manual')
const creating = ref(false)
const downloadingTemplate = ref(false)
const error = ref('')
const uploadFile = ref<File | null>(null)

function resolveSelectValue(options: string[], preferred?: string, fallback = ''): string {
  if (preferred && options.includes(preferred)) return preferred
  if (options.length > 0) return options[0]
  return preferred ?? fallback
}

const manualForm = ref({
  name: props.initialDraft?.name ?? '',
  version: props.initialDraft?.version ?? '1.0',
  agent: resolveSelectValue(props.agents, props.initialDraft?.agent, 'Testing Agent'),
  category: resolveSelectValue(props.categories, props.initialDraft?.category, 'development'),
  snowGroup: resolveSelectValue(props.snowGroups, props.initialDraft?.snowGroup, ''),
  application: resolveSelectValue(props.applications, props.initialDraft?.application, ''),
  site: resolveSelectValue(props.sites, props.initialDraft?.site, ''),
  estDurationMinutes: props.initialDraft?.estDurationMinutes ?? 60,
  description: props.initialDraft?.description ?? '',
})

function syncManualSelectDefaults() {
  manualForm.value.category = resolveSelectValue(
    props.categories,
    manualForm.value.category || props.initialDraft?.category,
    'development',
  )
  manualForm.value.agent = resolveSelectValue(
    props.agents,
    manualForm.value.agent || props.initialDraft?.agent,
    'Testing Agent',
  )
  manualForm.value.snowGroup = resolveSelectValue(
    props.snowGroups,
    manualForm.value.snowGroup || props.initialDraft?.snowGroup,
    '',
  )
  manualForm.value.application = resolveSelectValue(
    props.applications,
    manualForm.value.application || props.initialDraft?.application,
    '',
  )
  manualForm.value.site = resolveSelectValue(
    props.sites,
    manualForm.value.site || props.initialDraft?.site,
    '',
  )
}

watch(
  () => ({
    categories: props.categories,
    agents: props.agents,
    snowGroups: props.snowGroups,
    applications: props.applications,
    sites: props.sites,
  }),
  () => {
    syncManualSelectDefaults()
  },
  { deep: true, immediate: true },
)

const canCreateManual = computed(() =>
  manualForm.value.name.trim().length > 0 &&
  manualForm.value.version.trim().length > 0 &&
  manualForm.value.agent.trim().length > 0 &&
  manualForm.value.category.trim().length > 0 &&
  manualForm.value.snowGroup.trim().length > 0 &&
  manualForm.value.application.trim().length > 0 &&
  manualForm.value.site.trim().length > 0 &&
  manualForm.value.estDurationMinutes > 0 &&
  manualForm.value.description.trim().length > 0,
)

const canCreateUpload = computed(() => uploadFile.value !== null)

const durationLabel = computed(() => {
  const totalMinutes = manualForm.value.estDurationMinutes
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (hours === 0) return `${minutes}m`
  if (minutes === 0) return `${hours}h`
  return `${hours}h ${minutes}m`
})

const helperOwner = computed(() => userStore.displayName || userStore.userId || 'Current User')
const dialogTitle = computed(() => (isEditMode.value ? 'Edit Template' : 'Create New Template'))
const submitLabel = computed(() => {
  if (creating.value) return isEditMode.value ? 'Saving...' : 'Creating...'
  return isEditMode.value ? 'Save Template' : 'Create Template'
})

function close() {
  emit('close')
}

function selectTab(tab: 'manual' | 'upload') {
  if (isEditMode.value && tab === 'upload') return
  activeTab.value = tab
  error.value = ''
}

function onUploadFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
  error.value = ''
}

function changeDuration(delta: number) {
  manualForm.value.estDurationMinutes = Math.max(5, manualForm.value.estDurationMinutes + delta)
}

async function handleTemplateDownload() {
  downloadingTemplate.value = true
  error.value = ''
  try {
    const blob = await downloadTemplate()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'request-template.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Template download failed'
  } finally {
    downloadingTemplate.value = false
  }
}

async function createManualTemplate() {
  if (!canCreateManual.value) return
  creating.value = true
  error.value = ''

  try {
    emit('submit', {
      name: manualForm.value.name.trim(),
      version: manualForm.value.version.trim(),
      agent: manualForm.value.agent,
      category: manualForm.value.category,
      snowGroup: manualForm.value.snowGroup,
      application: manualForm.value.application,
      site: manualForm.value.site,
      estDurationMinutes: manualForm.value.estDurationMinutes,
      description: manualForm.value.description.trim(),
      source: 'manual',
    })
    close()
  } finally {
    creating.value = false
  }
}

async function createUploadTemplate() {
  if (!uploadFile.value) return
  creating.value = true
  error.value = ''

  try {
    const inferredName = uploadFile.value.name.replace(/\.[^.]+$/, '')
    emit('submit', {
      name: inferredName,
      version: '1.0',
      agent: resolveSelectValue(props.agents, props.initialDraft?.agent, 'Testing Agent'),
      category: resolveSelectValue(props.categories, props.initialDraft?.category, 'development'),
      snowGroup: resolveSelectValue(props.snowGroups, props.initialDraft?.snowGroup, ''),
      application: resolveSelectValue(props.applications, props.initialDraft?.application, ''),
      site: resolveSelectValue(props.sites, props.initialDraft?.site, ''),
      estDurationMinutes: 60,
      description: `Imported locally from ${uploadFile.value.name}. Task parsing will be connected once template import API is ready.`,
      source: 'upload',
      sourceFileName: uploadFile.value.name,
    })
    close()
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="close">
    <div class="modal modal-wide">
      <div class="modal-header">
        <span class="modal-title">{{ dialogTitle }}</span>
        <button class="modal-close" type="button" @click="close">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="supportsUpload" class="template-tabs">
          <button
            class="template-tab"
            :class="{ active: activeTab === 'manual' }"
            type="button"
            @click="selectTab('manual')"
          >
            Manual Entry
          </button>
          <button
            class="template-tab"
            :class="{ active: activeTab === 'upload' }"
            type="button"
            @click="selectTab('upload')"
          >
            Upload Excel
          </button>
        </div>

        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div v-if="activeTab === 'manual'" class="template-form">
          <div class="template-form-grid">
            <div class="form-row form-row-wide">
              <label class="form-label">Template Name <span class="required">*</span></label>
              <input
                v-model="manualForm.name"
                class="form-control"
                type="text"
                placeholder="Enter template name"
              />
            </div>

            <div class="form-row">
              <label class="form-label">Version <span class="required">*</span></label>
              <input v-model="manualForm.version" class="form-control" type="text" />
            </div>

            <div class="form-row">
              <label class="form-label">Category <span class="required">*</span></label>
              <select v-model="manualForm.category" class="form-control">
                <option v-for="category in categories" :key="category" :value="category">
                  {{ category }}
                </option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label">Agent <span class="required">*</span></label>
              <select v-model="manualForm.agent" class="form-control">
                <option v-for="agent in agents" :key="agent" :value="agent">
                  {{ agent }}
                </option>
              </select>
            </div>
          </div>

          <div class="section-title">Application Metadata</div>

          <div class="template-form-grid">
            <div class="form-row">
              <label class="form-label">SNOW Group <span class="required">*</span></label>
              <select v-model="manualForm.snowGroup" class="form-control">
                <option v-for="group in snowGroups" :key="group" :value="group">
                  {{ group }}
                </option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label">Application <span class="required">*</span></label>
              <select v-model="manualForm.application" class="form-control">
                <option v-for="application in applications" :key="application" :value="application">
                  {{ application }}
                </option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label">Site (Market) <span class="required">*</span></label>
              <select v-model="manualForm.site" class="form-control">
                <option v-for="site in sites" :key="site" :value="site">
                  {{ site }}
                </option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label">Estimated Duration <span class="required">*</span></label>
              <div class="duration-input">
                <button class="stepper-btn" type="button" @click="changeDuration(-5)">−</button>
                <input
                  v-model.number="manualForm.estDurationMinutes"
                  class="form-control duration-control"
                  type="number"
                  min="5"
                  step="5"
                />
                <button class="stepper-btn" type="button" @click="changeDuration(5)">+</button>
              </div>
              <div class="field-hint">minutes ({{ durationLabel }})</div>
            </div>

            <div class="form-row form-row-wide">
              <label class="form-label">Description <span class="required">*</span></label>
              <textarea
                v-model="manualForm.description"
                class="form-control textarea-control"
                rows="4"
                maxlength="500"
                placeholder="Enter template description"
              />
              <div class="char-count">{{ manualForm.description.length }}/500</div>
            </div>
          </div>

          <div class="info-panel">
            <div class="info-title">{{ isEditMode ? 'Template Update' : 'Template Creation' }}</div>
            <p>
              <span v-if="isEditMode">
                Update the template defaults used when this blueprint is selected for a new
                rundown.
              </span>
              <span v-else>
                Create a new workflow template from scratch. Application metadata becomes the
                default context when creating a rundown from this template.
              </span>
            </p>
            <p>
              <span v-if="isEditMode">
                Updated by: <strong>{{ helperOwner }}</strong>. Existing tasks stay intact while
                basic template metadata is refreshed.
              </span>
              <span v-else>
                Created by: <strong>{{ helperOwner }}</strong>. Tasks can be added after creation in
                the template details workflow.
              </span>
            </p>
          </div>
        </div>

        <div v-else class="upload-tab">
          <div class="alert alert-info upload-intro">
            Upload an Excel file to create a template with tasks.
            <button
              class="text-link"
              type="button"
              :disabled="downloadingTemplate"
              @click="handleTemplateDownload"
            >
              {{ downloadingTemplate ? 'Downloading...' : 'Download Excel Template' }}
            </button>
          </div>

          <div class="upload-dropzone">
            <input
              id="template-upload-file"
              class="sr-only"
              type="file"
              accept=".xlsx,.xls"
              @change="onUploadFileChange"
            />
            <label class="btn btn-primary" for="template-upload-file">Choose Excel File</label>
            <div class="upload-file-name">
              {{ uploadFile ? uploadFile.name : 'No file selected yet' }}
            </div>
          </div>

          <div class="info-panel">
            <div class="info-title">Current Scope</div>
            <p>
              This upload tab already reuses the real Excel template download endpoint from the
              current codebase.
            </p>
            <p>
              Upload-based template parsing is not yet backed by a dedicated API, so this flow will
              create a local preview draft from the chosen file name for now.
            </p>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="close">Cancel</button>
        <button
          v-if="activeTab === 'manual'"
          class="btn btn-primary"
          type="button"
          :disabled="!canCreateManual || creating"
          @click="createManualTemplate"
        >
          {{ submitLabel }}
        </button>
        <button
          v-else
          class="btn btn-primary"
          type="button"
          :disabled="!canCreateUpload || creating"
          @click="createUploadTemplate"
        >
          {{ submitLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-wide {
  width: 920px;
}

.template-tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  border-bottom: 1px solid #e2e8f0;
}

.template-tab {
  padding: 0 0 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.template-tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
}

.template-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.template-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-row-wide {
  grid-column: 1 / -1;
}

.required {
  color: #dc2626;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.duration-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.duration-control {
  flex: 1;
  text-align: center;
}

.stepper-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: var(--color-surface-secondary);
  color: var(--color-text-secondary);
  font-size: 20px;
  line-height: 1;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

.textarea-control {
  resize: vertical;
  min-height: 120px;
}

.char-count {
  align-self: flex-end;
  font-size: 12px;
  color: #94a3b8;
}

.info-panel {
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.info-title {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.upload-tab {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.upload-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.text-link {
  border: 0;
  background: transparent;
  color: #2563eb;
  font-weight: 600;
  cursor: pointer;
}

.text-link:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.upload-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 40px 20px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: var(--color-surface-secondary);
}

.upload-file-name {
  font-size: 13px;
  color: var(--color-text-muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 840px) {
  .modal-wide {
    width: calc(100vw - 24px);
  }

  .template-form-grid {
    grid-template-columns: 1fr;
  }

  .upload-intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
