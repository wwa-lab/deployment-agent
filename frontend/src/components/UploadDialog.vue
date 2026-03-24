<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { downloadTemplate, uploadFile } from '../api/upload'
import { useReleaseFlowStore } from '../stores/releaseFlow'
import { useUserStore } from '../stores/user'
import type { Stage, UploadResponse } from '../types'

const props = defineProps<{
  initialScope?: {
    application?: string
    snowGroup?: string
    agent?: string
  }
}>()

const emit = defineEmits<{ close: [] }>()

const store = useReleaseFlowStore()
const userStore = useUserStore()

const stage = ref<Stage | ''>('')
const file = ref<File | null>(null)
const uploading = ref(false)
const downloadingTemplate = ref(false)
const error = ref('')
const successResult = ref<UploadResponse | null>(null)
const scopeForm = reactive({
  application: props.initialScope?.application ?? '',
  snowGroup: props.initialScope?.snowGroup ?? '',
  agent: props.initialScope?.agent ?? '',
})

const canUseUpload = computed(() => userStore.canUploadRelease)
const canSubmit = computed(() =>
  canUseUpload.value && stage.value !== '' && file.value !== null && !uploading.value,
)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  error.value = ''
}

async function submit() {
  if (!canSubmit.value || !file.value || !stage.value) return
  uploading.value = true
  error.value = ''
  try {
    successResult.value = await uploadFile(file.value, stage.value as Stage, {
      application: scopeForm.application.trim() || undefined,
      snowGroup: scopeForm.snowGroup.trim() || undefined,
      agent: scopeForm.agent.trim() || undefined,
    })
    await store.fetchList()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Upload failed'
  } finally {
    uploading.value = false
  }
}

async function handleTemplateDownload() {
  downloadingTemplate.value = true
  error.value = ''
  try {
    const blob = await downloadTemplate()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'deployment-request-template.xlsx'
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
        <span class="modal-title">Upload Release File</span>
        <button class="modal-close" @click="close">✕</button>
      </div>

      <div class="modal-body">
        <!-- Success state -->
        <div v-if="successResult" class="alert alert-success">
          <strong>Upload successful!</strong><br />
          Release ID: <code>{{ successResult.releaseId }}</code><br />
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
            <select v-model="stage" class="form-control" :disabled="!canUseUpload">
              <option value="">Select stage...</option>
              <option value="SIT">SIT</option>
              <option value="UAT">UAT</option>
              <option value="PROD">PROD</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">Application</label>
            <input
              v-model="scopeForm.application"
              type="text"
              class="form-control"
              placeholder="e.g. AMH HCC"
              :disabled="!canUseUpload"
            />
          </div>

          <div class="form-group">
            <label class="form-label">SNOW Group</label>
            <input
              v-model="scopeForm.snowGroup"
              type="text"
              class="form-control"
              placeholder="e.g. HTSA-CSI-HCC-AMH-PRJ"
              :disabled="!canUseUpload"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Agent</label>
            <input
              v-model="scopeForm.agent"
              type="text"
              class="form-control"
              placeholder="e.g. Deployment Agent"
              :disabled="!canUseUpload"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Release File (XLSX) <span class="required">*</span></label>
            <input
              type="file"
              accept=".xlsx,.xls"
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

.file-name {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.template-link {
  margin-top: 4px;
}
</style>
