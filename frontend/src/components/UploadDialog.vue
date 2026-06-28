<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { agentRegistry } from '../config/agentRegistry'
import { useScopeDirectoryStore } from '../stores/scopeDirectory'
import { useUserStore } from '../stores/user'
import type { Stage, UploadResponse } from '../types'

interface UploadOptions {
  releaseId?: string
  application?: string
  snowGroup?: string
  agent?: string
}

interface WorkspaceAgentContext {
  key: string
  name: string
}

const props = defineProps<{
  initialScope?: {
    application?: string
    snowGroup?: string
    agent?: string
  }
  workspaceAgent?: WorkspaceAgentContext
  allowedStages?: Stage[]
  uploadFn: (file: File, stage: Stage, options?: UploadOptions) => Promise<UploadResponse>
  downloadTemplateFn: () => Promise<Blob>
  onUploadSuccess: () => Promise<void>
}>()

const emit = defineEmits<{ close: [] }>()

const userStore = useUserStore()
const scopeDirectoryStore = useScopeDirectoryStore()

const ALL_STAGES: Stage[] = ['SIT', 'UAT', 'PROD']
const availableStages = computed(() => props.allowedStages ?? ALL_STAGES)
const availableAgents = computed(() => agentRegistry.filter((agent) => agent.enabled))
const workspaceAgent = computed(() => props.workspaceAgent)
const stage = ref<Stage | ''>(availableStages.value.length === 1 ? availableStages.value[0] : '')
const file = ref<File | null>(null)
const modalBodyRef = ref<HTMLElement | null>(null)
const stageSelectRef = ref<HTMLSelectElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const releaseIdentifier = ref('')
const uploading = ref(false)
const downloadingTemplate = ref(false)
const error = ref('')
const successResult = ref<UploadResponse | null>(null)
const scopeForm = reactive({
  application: props.initialScope?.application ?? '',
  snowGroup: props.initialScope?.snowGroup ?? '',
  agent: props.workspaceAgent?.key ?? props.initialScope?.agent ?? '',
})
const activeDirectoryAgent = computed(() => props.workspaceAgent?.key ?? (scopeForm.agent.trim() || undefined))
function matchesScopeDirectoryAgent(entryAgent?: string) {
  const normalizedEntryAgent = entryAgent?.trim()
  const normalizedActiveAgent = activeDirectoryAgent.value?.trim()
  return !normalizedEntryAgent || !normalizedActiveAgent || normalizedEntryAgent === normalizedActiveAgent
}
const applicationOptions = computed(() => {
  const values = new Set(
    scopeDirectoryStore.entries
      .filter((entry) => matchesScopeDirectoryAgent(entry.agent))
      .map((entry) => entry.application)
      .concat(scopeForm.application ? [scopeForm.application] : []),
  )

  return [...values].filter(Boolean).sort((left, right) => left.localeCompare(right))
})
const snowGroupOptions = computed(() => {
  if (!scopeForm.application.trim()) {
    return scopeForm.snowGroup ? [scopeForm.snowGroup] : []
  }

  const values = new Set(
    scopeDirectoryStore.entries
      .filter((entry) => matchesScopeDirectoryAgent(entry.agent))
      .filter((entry) => entry.application === scopeForm.application)
      .map((entry) => entry.snowGroup)
      .filter((value): value is string => Boolean(value))
      .concat(scopeForm.snowGroup ? [scopeForm.snowGroup] : []),
  )

  return [...values].sort((left, right) => left.localeCompare(right))
})

const canUseUpload = computed(() => userStore.canUploadRelease)
const canSubmit = computed(() => canUseUpload.value && !uploading.value)

onMounted(() => {
  void scopeDirectoryStore.fetchEntries().catch(() => undefined)
})

watch(
  () => scopeForm.application,
  (nextApplication, previousApplication) => {
    if (nextApplication === previousApplication) {
      return
    }

    if (!nextApplication.trim()) {
      scopeForm.snowGroup = ''
      return
    }

    if (!snowGroupOptions.value.includes(scopeForm.snowGroup)) {
      scopeForm.snowGroup = ''
    }
  },
)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const selectedFile = input.files?.[0] ?? null
  if (selectedFile && !selectedFile.name.toLowerCase().endsWith('.xlsx')) {
    file.value = null
    input.value = ''
    error.value = 'Choose a valid .xlsx workflow file before uploading.'
    fileInputRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    fileInputRef.value?.focus()
    return
  }

  file.value = selectedFile
  error.value = ''
}

watch(stage, (nextStage) => {
  if (nextStage) {
    error.value = ''
  }
})

function focusRequiredField(target: HTMLSelectElement | HTMLInputElement | null) {
  target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  target?.focus()
}

function showError(message: string) {
  error.value = message
  void nextTick(() => {
    modalBodyRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
  })
}

function validateBeforeSubmit() {
  if (!canUseUpload.value) {
    showError('Upload is available to DEVELOPER, TL, and DEVOPS_ADMIN users.')
    return false
  }

  if (!stage.value) {
    showError('Select a stage before uploading.')
    focusRequiredField(stageSelectRef.value)
    return false
  }

  if (!file.value) {
    showError('Choose a valid .xlsx workflow file before uploading.')
    focusRequiredField(fileInputRef.value)
    return false
  }

  return true
}

async function submit() {
  if (!validateBeforeSubmit() || !file.value || !stage.value) return
  uploading.value = true
  error.value = ''
  try {
    const selectedAgent = props.workspaceAgent?.key ?? (scopeForm.agent.trim() || undefined)
    successResult.value = await props.uploadFn(file.value, stage.value as Stage, {
      releaseId: releaseIdentifier.value.trim() || undefined,
      application: scopeForm.application.trim() || undefined,
      snowGroup: scopeForm.snowGroup.trim() || undefined,
      agent: selectedAgent,
    })
    await props.onUploadSuccess()
  } catch (e: unknown) {
    showError(e instanceof Error ? e.message : 'Upload failed')
  } finally {
    uploading.value = false
  }
}

async function handleTemplateDownload() {
  downloadingTemplate.value = true
  error.value = ''
  try {
    const blob = await props.downloadTemplateFn()
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

function close() {
  emit('close')
}
</script>

<template>
  <div class="modal-overlay" @click.self="close">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Upload Workflow File</span>
        <button class="modal-close" @click="close">✕</button>
      </div>

      <div ref="modalBodyRef" class="modal-body">
        <!-- Success state -->
        <div v-if="successResult" class="alert alert-success">
          <strong>Upload successful!</strong><br />
          Workflow ID: <code>{{ successResult.releaseId }}</code><br />
          Stage: {{ successResult.stage }}<br />
          Tasks created: {{ successResult.taskCount }}
          <template v-if="successResult.application || successResult.snowGroup || successResult.agent">
            <br />
            Scope:
            {{ successResult.application || '—' }}
            /
            {{ successResult.snowGroup || '—' }}
            /
            {{ successResult.agent || '—' }}
          </template>
        </div>

        <template v-else>
          <div v-if="error" class="alert alert-error">{{ error }}</div>
          <div v-if="!canUseUpload" class="alert alert-info">
            Upload is available to `DEVELOPER`, `TL`, and `DEVOPS_ADMIN` users.
          </div>

          <div class="form-group">
            <label class="form-label">Stage <span class="required">*</span></label>
            <select
              ref="stageSelectRef"
              v-model="stage"
              class="form-control"
              :disabled="!canUseUpload || availableStages.length === 1"
            >
              <option v-if="availableStages.length > 1" value="">Select stage...</option>
              <option v-for="s in availableStages" :key="s" :value="s">{{ s }}</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">Workflow Identifier</label>
            <input
              v-model="releaseIdentifier"
              type="text"
              class="form-control"
              placeholder="e.g. WFPROJ-20260327-01"
              :disabled="!canUseUpload"
            />
            <div class="field-hint">
              Recommended for repeated uploads. Reuse the same identifier when later uploads should
              stay grouped under one workflow summary across SIT, UAT, and PROD. Re-uploading the
              same stage under the same identifier creates a new attempt. If left blank, WWA-Atlas Hub creates
              a new rollout instead of implicitly merging stages.
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Application <span class="optional">(Optional)</span></label>
            <select
              v-model="scopeForm.application"
              class="form-control"
              :disabled="!canUseUpload || scopeDirectoryStore.loading || applicationOptions.length === 0"
            >
              <option value="">
                {{ scopeDirectoryStore.loading ? 'Loading applications...' : 'Not specified' }}
              </option>
              <option
                v-for="application in applicationOptions"
                :key="application"
                :value="application"
              >
                {{ application }}
              </option>
            </select>
            <div class="field-hint">
              Choose from the maintained scope directory in Configuration Management.
            </div>
            <div v-if="scopeDirectoryStore.error" class="field-hint field-error">
              {{ scopeDirectoryStore.error }}
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">SNOW Group <span class="optional">(Optional)</span></label>
            <select
              v-model="scopeForm.snowGroup"
              class="form-control"
              :disabled="!canUseUpload || !scopeForm.application || scopeDirectoryStore.loading"
            >
              <option value="">
                {{
                  !scopeForm.application
                    ? 'Select an application first'
                    : snowGroupOptions.length === 0
                      ? 'Not specified'
                      : 'Not specified'
                }}
              </option>
              <option
                v-for="snowGroup in snowGroupOptions"
                :key="snowGroup"
                :value="snowGroup"
              >
                {{ snowGroup }}
              </option>
            </select>
            <div class="field-hint">
              {{
                scopeForm.application
                  ? 'Only SNOW Groups maintained under the selected application are shown here.'
                  : 'Choose an application first to narrow SNOW Group choices.'
              }}
            </div>
            <div
              v-if="scopeForm.application && snowGroupOptions.length === 0 && !scopeDirectoryStore.loading"
              class="field-hint"
            >
              No maintained SNOW Group values were found for this application yet.
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Agent</label>
            <template v-if="workspaceAgent">
              <div class="readonly-field">{{ workspaceAgent.name }}</div>
              <div class="field-hint">
                Automatically assigned from the current workspace, so uploaders do not need to
                maintain this field by hand.
              </div>
            </template>
            <template v-else>
              <select v-model="scopeForm.agent" class="form-control" :disabled="!canUseUpload">
                <option value="">Not specified</option>
                <option v-for="agent in availableAgents" :key="agent.key" :value="agent.key">
                  {{ agent.name }}
                </option>
              </select>
              <div class="field-hint">
                Optional. Leave blank when the upload should not stamp a specific agent.
              </div>
            </template>
          </div>

          <div class="form-group">
            <label class="form-label">Workflow File (XLSX) <span class="required">*</span></label>
            <input
              ref="fileInputRef"
              type="file"
              accept=".xlsx"
              class="form-control"
              :disabled="!canUseUpload"
              @change="onFileChange"
            />
            <span v-if="file" class="file-name">{{ file.name }}</span>
          </div>

          <div class="template-link">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :disabled="downloadingTemplate || !canUseUpload"
              @click="handleTemplateDownload"
            >
              {{ downloadingTemplate ? 'Downloading...' : 'Download Template' }}
            </button>
          </div>
        </template>
      </div>

      <div class="modal-footer">
        <div v-if="error && !successResult" class="footer-error">{{ error }}</div>
        <button class="btn btn-secondary" @click="close">
          {{ successResult ? 'Close' : 'Cancel' }}
        </button>
        <button
          v-if="!successResult"
          class="btn btn-primary"
          :disabled="!canSubmit"
          @click="submit"
        >
          <span v-if="uploading" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ uploading ? 'Uploading...' : 'Upload' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.required {
  color: #ef4444;
}

.optional {
  color: var(--color-text-muted);
  font-weight: 400;
}

.file-name {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.template-link {
  margin-top: 4px;
}

.footer-error {
  margin-right: auto;
  max-width: min(60%, 420px);
  color: #991b1b;
  font-size: 12px;
  line-height: 1.4;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

.field-error {
  color: #b91c1c;
}

.readonly-field {
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: var(--color-surface-secondary);
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
}
</style>
