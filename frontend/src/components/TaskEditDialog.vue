<script setup lang="ts">
import { ref, reactive } from 'vue'
import { editTask } from '../api/tasks'
import type { Task } from '../types'

const props = defineProps<{ task: Task }>()
const emit = defineEmits<{ saved: []; close: [] }>()

const form = reactive({
  script: props.task.inputParameters.script ?? '',
  parameters: props.task.inputParameters.parameters ?? '',
})

const saving = ref(false)
const error = ref('')
const fieldErrors = reactive<{ script?: string; parameters?: string }>({})

function validate(): boolean {
  fieldErrors.script = undefined
  fieldErrors.parameters = undefined
  return true
}

async function submit() {
  if (!validate()) return
  saving.value = true
  error.value = ''
  try {
    await editTask(props.task.id, {
      script: form.script,
      parameters: form.parameters,
    })
    emit('saved')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to save task'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Edit Task — {{ task.taskName }}</span>
        <button class="modal-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="form-group">
          <label class="form-label">Script</label>
          <textarea
            v-model="form.script"
            class="form-control"
            rows="5"
            placeholder="Enter script..."
            :class="{ 'input-error': fieldErrors.script }"
          ></textarea>
          <span v-if="fieldErrors.script" class="field-error">{{ fieldErrors.script }}</span>
        </div>

        <div class="form-group">
          <label class="form-label">Parameters</label>
          <textarea
            v-model="form.parameters"
            class="form-control"
            rows="4"
            placeholder="Enter parameters (JSON or text)..."
            :class="{ 'input-error': fieldErrors.parameters }"
          ></textarea>
          <span v-if="fieldErrors.parameters" class="field-error">{{ fieldErrors.parameters }}</span>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <span v-if="saving" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.input-error {
  border-color: #ef4444 !important;
}

.field-error {
  font-size: 12px;
  color: #dc2626;
  margin-top: 2px;
}
</style>
