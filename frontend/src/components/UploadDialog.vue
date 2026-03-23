<script setup lang="ts">
import { ref, computed } from 'vue'
import { downloadTemplate, uploadFile } from '../api/upload'
import { useReleaseFlowStore } from '../stores/releaseFlow'
import type { Stage, UploadResponse } from '../types'

const emit = defineEmits<{ close: [] }>()

const store = useReleaseFlowStore()

const stage = ref<Stage | ''>('')
const file = ref<File | null>(null)
const uploading = ref(false)
const downloadingTemplate = ref(false)
const error = ref('')
const successResult = ref<UploadResponse | null>(null)

const canSubmit = computed(() => stage.value !== '' && file.value !== null && !uploading.value)

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
    successResult.value = await uploadFile(file.value, stage.value as Stage)
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
        </div>

        <template v-else>
          <div v-if="error" class="alert alert-error">{{ error }}</div>

          <div class="form-group">
            <label class="form-label">Stage <span class="required">*</span></label>
            <select v-model="stage" class="form-control">
              <option value="">Select stage...</option>
              <option value="SIT">SIT</option>
              <option value="UAT">UAT</option>
              <option value="PROD">PROD</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">Release File (XLSX) <span class="required">*</span></label>
            <input
              type="file"
              accept=".xlsx,.xls"
              class="form-control"
              @change="onFileChange"
            />
            <span v-if="file" class="file-name">{{ file.name }}</span>
          </div>

          <div class="template-link">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :disabled="downloadingTemplate"
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
