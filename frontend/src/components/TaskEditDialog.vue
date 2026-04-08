<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  editTask as editTaskApi,
  recordResult as recordResultApi,
  startManualExecution as startManualExecutionApi,
} from '../api/tasks'
import type { Task } from '../types'

const props = withDefaults(defineProps<{
  task: Task
  mode?: 'edit' | 'run'
  editTaskFn?: (taskId: string, inputParameters: Record<string, unknown>) => Promise<Task>
  recordResultFn?: (
    taskId: string,
    body: { resultSummary: Record<string, unknown>; resultLogs?: string }
  ) => Promise<Task>
  startManualExecutionFn?: (taskId: string) => Promise<Task>
}>(), {
  mode: 'edit',
  editTaskFn: editTaskApi,
  recordResultFn: recordResultApi,
  startManualExecutionFn: startManualExecutionApi,
})
const emit = defineEmits<{ saved: []; close: [] }>()

const getInputParams = () => ({
  script: props.task.inputParameters?.script ?? '',
  parameters: props.task.inputParameters?.parameters ?? '',
})

const form = reactive({
  script: getInputParams().script,
  parameters: getInputParams().parameters,
  resultSummary: '',
  resultLogs: '',
})

let originalScript = getInputParams().script
let originalParameters = getInputParams().parameters

watch(() => props.task, (newTask) => {
  form.script = newTask.inputParameters?.script ?? ''
  form.parameters = newTask.inputParameters?.parameters ?? ''
  form.resultSummary = ''
  form.resultLogs = ''
  originalScript = newTask.inputParameters?.script ?? ''
  originalParameters = newTask.inputParameters?.parameters ?? ''
}, { immediate: false })

const saving = ref(false)
const error = ref('')
const fieldErrors = reactive<{ script?: string; parameters?: string; resultSummary?: string }>({})

const canSubmitManualResult = computed(
  () =>
    props.task.executionType === 'MANUAL' &&
    (props.task.taskStatus === 'Ready_For_Execution' || props.task.taskStatus === 'Executing'),
)

const hasInputChanges = computed(
  () => form.script !== originalScript || form.parameters !== originalParameters,
)

const isSubmittingResult = computed(
  () =>
    canSubmitManualResult.value &&
    (form.resultSummary.trim().length > 0 || form.resultLogs.trim().length > 0),
)

const isStartingManualExecution = computed(
  () =>
    props.mode === 'run' &&
    props.task.executionType === 'MANUAL' &&
    props.task.taskStatus === 'Ready_For_Execution' &&
    !isSubmittingResult.value,
)

const requiresResultForRunningManualTask = computed(
  () =>
    props.mode === 'run' &&
    props.task.executionType === 'MANUAL' &&
    props.task.taskStatus === 'Executing' &&
    !isSubmittingResult.value,
)

const canEditInputFields = computed(
  () =>
    !(props.mode === 'run' && props.task.executionType === 'MANUAL' && props.task.taskStatus === 'Executing'),
)

const submitLabel = computed(() =>
  saving.value
    ? 'Saving...'
    : isStartingManualExecution.value
      ? 'Run'
    : requiresResultForRunningManualTask.value
      ? 'Submit Result'
    : isSubmittingResult.value
      ? 'Save & Submit Result'
      : 'Save',
)

const dialogTitle = computed(() =>
  props.mode === 'run' ? `Run Task — ${props.task.taskName}` : `Edit Task — ${props.task.taskName}`,
)

const manualResultHelp = computed(() =>
  props.mode === 'run' && props.task.executionType === 'MANUAL' && props.task.taskStatus === 'Executing'
    ? 'Manual execution is in progress. Submit a result summary to move this task to review.'
    : props.mode === 'run'
      ? 'Capture the outcome here after you complete the manual step. Submitting a result will move the task to review.'
      : 'Add the execution outcome here if this manual step has been completed. Saving with a result summary will move the task to review.',
)

function validate(): boolean {
  fieldErrors.script = undefined
  fieldErrors.parameters = undefined
  fieldErrors.resultSummary = undefined

  if (isSubmittingResult.value && !form.resultSummary.trim()) {
    fieldErrors.resultSummary = 'Result summary is required when submitting a result.'
  }

  return !fieldErrors.resultSummary
}

async function submit() {
  if (!validate()) return
  if (requiresResultForRunningManualTask.value) {
    error.value = 'Provide a result summary to complete this running task.'
    return
  }

  if (!hasInputChanges.value && !isSubmittingResult.value && !isStartingManualExecution.value) {
    error.value = 'No changes to save.'
    return
  }

  saving.value = true
  error.value = ''
  try {
    if (hasInputChanges.value) {
      await props.editTaskFn(props.task.id, {
        script: form.script,
        parameters: form.parameters,
      })
    }

    if (isSubmittingResult.value) {
      await props.recordResultFn(props.task.id, {
        resultSummary: {
          summary: form.resultSummary.trim(),
        },
        resultLogs: form.resultLogs.trim() || undefined,
      })
    } else if (isStartingManualExecution.value) {
      await props.startManualExecutionFn(props.task.id)
    }

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
        <span class="modal-title">{{ dialogTitle }}</span>
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
            :disabled="!canEditInputFields"
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
            :disabled="!canEditInputFields"
          ></textarea>
          <span v-if="fieldErrors.parameters" class="field-error">{{ fieldErrors.parameters }}</span>
        </div>

        <div v-if="canSubmitManualResult" class="manual-result-panel">
          <div class="manual-result-title">Submit Manual Result</div>
          <p class="manual-result-help">
            {{ manualResultHelp }}
          </p>

          <div class="form-group">
            <label class="form-label">
              Result Summary
            </label>
            <textarea
              v-model="form.resultSummary"
              class="form-control"
              rows="4"
              placeholder="Summarize what happened during manual execution..."
              :class="{ 'input-error': fieldErrors.resultSummary }"
            ></textarea>
            <span v-if="fieldErrors.resultSummary" class="field-error">{{ fieldErrors.resultSummary }}</span>
          </div>

          <div class="form-group" style="margin-bottom: 0;">
            <label class="form-label">Result Logs</label>
            <textarea
              v-model="form.resultLogs"
              class="form-control"
              rows="4"
              placeholder="Paste any relevant output, notes, or evidence..."
            ></textarea>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <span v-if="saving" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ submitLabel }}
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

.manual-result-panel {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.manual-result-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 4px;
}

.manual-result-help {
  margin: 0 0 12px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}
</style>
