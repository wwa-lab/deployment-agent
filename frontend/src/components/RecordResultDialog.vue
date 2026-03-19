<script setup lang="ts">
import { ref } from 'vue'
import { recordResult } from '../api/tasks'
import type { Task } from '../types'

const props = defineProps<{ task: Task }>()
const emit = defineEmits<{ saved: []; close: [] }>()

const resultSummary = ref('')
const resultLogs = ref('')
const saving = ref(false)
const error = ref('')

async function submit() {
  if (!resultSummary.value.trim()) {
    error.value = 'Result summary is required.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await recordResult(props.task.id, {
      resultSummary: resultSummary.value,
      resultLogs: resultLogs.value || undefined,
    })
    emit('saved')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to record result'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Record Result — {{ task.taskName }}</span>
        <button class="modal-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <!-- Reference panel -->
        <div class="ref-panel">
          <div class="ref-title">Task Reference</div>
          <div class="ref-row">
            <span class="ref-label">Script</span>
            <pre class="ref-value">{{ task.inputParameters.script ?? '—' }}</pre>
          </div>
          <div class="ref-row">
            <span class="ref-label">Parameters</span>
            <pre class="ref-value">{{ task.inputParameters.parameters ?? '—' }}</pre>
          </div>
          <div class="ref-row">
            <span class="ref-label">Expected Output</span>
            <pre class="ref-value">{{ task.expectedOutput ?? '—' }}</pre>
          </div>
        </div>

        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="form-group">
          <label class="form-label">
            Result Summary <span style="color:#ef4444">*</span>
          </label>
          <textarea
            v-model="resultSummary"
            class="form-control"
            rows="4"
            placeholder="Enter the actual result summary..."
          ></textarea>
        </div>

        <div class="form-group">
          <label class="form-label">Result Logs (optional)</label>
          <textarea
            v-model="resultLogs"
            class="form-control"
            rows="4"
            placeholder="Enter logs if any..."
          ></textarea>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <span v-if="saving" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ saving ? 'Saving...' : 'Save Result' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ref-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
}

.ref-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.ref-row {
  margin-bottom: 8px;
}

.ref-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 2px;
}

.ref-value {
  font-size: 12px;
  font-family: monospace;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  background: white;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}
</style>
