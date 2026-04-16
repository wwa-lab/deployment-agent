<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { Task, ExecutionType } from '../types'
import AiSuggestionPanel from './AiSuggestionPanel.vue'
import { AI_ASSIST_PREVIEW_ENABLED } from '../config/platformConfig'
import type { TaskDocSpec } from '../platform/composables/releaseFlowTypes'
import TaskDocsPanel from './TaskDocsPanel.vue'

const props = withDefaults(defineProps<{
  task: Task
  editTaskFn: (taskId: string, inputParameters: Record<string, unknown>) => Promise<Task>
  editNamesFn: (taskId: string, names: { taskName?: string; taskGroupName?: string }) => Promise<Task>
  editExecutionTypeFn: (taskId: string, executionType: 'MANUAL' | 'AUTO') => Promise<Task>
  recordResultFn: (
    taskId: string,
    body: { resultSummary: Record<string, unknown>; resultLogs?: string }
  ) => Promise<Task>
  startManualExecutionFn: (taskId: string) => Promise<Task>
  taskDocs?: TaskDocSpec | null
  mode?: 'edit' | 'run'
}>(), {
  mode: 'edit',
})
const emit = defineEmits<{ saved: []; close: [] }>()

const getInputParams = () => ({
  script: props.task.inputParameters?.script ?? '',
  parameters: props.task.inputParameters?.parameters ?? '',
})

const form = reactive({
  taskName: props.task.taskName ?? '',
  taskGroupName: props.task.taskGroupName ?? '',
  script: getInputParams().script,
  parameters: getInputParams().parameters,
  resultSummary: '',
  resultLogs: '',
  executionType: props.task.executionType as ExecutionType,
})

let originalTaskName = props.task.taskName ?? ''
let originalTaskGroupName = props.task.taskGroupName ?? ''
let originalScript = getInputParams().script
let originalParameters = getInputParams().parameters

watch(() => props.task, (newTask) => {
  form.taskName = newTask.taskName ?? ''
  form.taskGroupName = newTask.taskGroupName ?? ''
  form.script = newTask.inputParameters?.script ?? ''
  form.parameters = newTask.inputParameters?.parameters ?? ''
  form.resultSummary = ''
  form.resultLogs = ''
  form.executionType = newTask.executionType
  originalTaskName = newTask.taskName ?? ''
  originalTaskGroupName = newTask.taskGroupName ?? ''
  originalScript = newTask.inputParameters?.script ?? ''
  originalParameters = newTask.inputParameters?.parameters ?? ''
}, { immediate: false })

const saving = ref(false)
const error = ref('')
const fieldErrors = reactive<{ script?: string; parameters?: string; resultSummary?: string }>({})

const canChangeExecutionType = computed(
  () =>
    props.mode === 'edit' &&
    (props.task.taskStatus === 'Pending' || props.task.taskStatus === 'Ready_For_Execution'),
)

const hasExecutionTypeChange = computed(
  () => form.executionType !== props.task.executionType,
)

const canSubmitManualResult = computed(
  () =>
    props.task.executionType === 'MANUAL' &&
    (props.task.taskStatus === 'Ready_For_Execution' || props.task.taskStatus === 'Executing'),
)

const hasNameChanges = computed(
  () => form.taskName !== originalTaskName || form.taskGroupName !== originalTaskGroupName,
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
      ? 'Start Manual Execution'
    : requiresResultForRunningManualTask.value
      ? 'Submit Result'
    : isSubmittingResult.value
      ? 'Save & Submit Result'
      : 'Save',
)

const dialogTitle = computed(() => {
  if (props.mode === 'run' && props.task.executionType === 'MANUAL' && props.task.taskStatus === 'Ready_For_Execution') {
    return `Start Manual Task — ${props.task.taskName}`
  }
  if (props.mode === 'run' && props.task.executionType === 'MANUAL' && props.task.taskStatus === 'Executing') {
    return `Record Result — ${props.task.taskName}`
  }
  return props.mode === 'run' ? `Run Task — ${props.task.taskName}` : `Edit Task — ${props.task.taskName}`
})

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

  if (!hasNameChanges.value && !hasInputChanges.value && !hasExecutionTypeChange.value && !isSubmittingResult.value && !isStartingManualExecution.value) {
    error.value = 'No changes to save.'
    return
  }

  saving.value = true
  error.value = ''
  try {
    if (hasNameChanges.value) {
      const names: { taskName?: string; taskGroupName?: string } = {}
      if (form.taskName !== originalTaskName) names.taskName = form.taskName.trim()
      if (form.taskGroupName !== originalTaskGroupName) names.taskGroupName = form.taskGroupName.trim()
      await props.editNamesFn(props.task.id, names)
    }

    if (hasExecutionTypeChange.value) {
      await props.editExecutionTypeFn(props.task.id, form.executionType)
    }

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

        <div v-if="taskDocs" class="task-docs-section">
          <div class="task-docs-title">Task Docs</div>
          <TaskDocsPanel :task-docs="taskDocs" />
        </div>

        <div v-if="isStartingManualExecution" class="manual-steps-hint">
          <div class="manual-steps-title">Manual task workflow</div>
          <ol class="manual-steps-list">
            <li><strong>Start</strong> — click the button below to mark this task as in-progress</li>
            <li><strong>Execute</strong> — perform the manual step outside this system</li>
            <li><strong>Record Result</strong> — come back and click "Record Result" to submit the outcome</li>
            <li><strong>Review</strong> — a reviewer will approve or reject the result</li>
          </ol>
        </div>

        <div v-if="canChangeExecutionType" class="form-group">
          <label class="form-label">Step Name</label>
          <input
            v-model="form.taskName"
            class="form-control"
            placeholder="Step name..."
          />
        </div>

        <div v-if="canChangeExecutionType" class="form-group">
          <label class="form-label">Task Name</label>
          <input
            v-model="form.taskGroupName"
            class="form-control"
            placeholder="Task group name..."
          />
        </div>

        <div v-if="canChangeExecutionType" class="form-group">
          <label class="form-label">Execution Type</label>
          <div class="execution-type-toggle">
            <button
              type="button"
              class="toggle-btn"
              :class="{ active: form.executionType === 'AUTO' }"
              @click="form.executionType = 'AUTO'"
            >
              AUTO
            </button>
            <button
              type="button"
              class="toggle-btn"
              :class="{ active: form.executionType === 'MANUAL' }"
              @click="form.executionType = 'MANUAL'"
            >
              MANUAL
            </button>
          </div>
          <p class="execution-type-hint">
            {{ form.executionType === 'AUTO'
              ? 'Submitted to Jenkins/Ansible for automated execution.'
              : 'Performed manually outside the system; you record the result afterwards.' }}
          </p>
        </div>

        <AiSuggestionPanel
          v-if="AI_ASSIST_PREVIEW_ENABLED"
          context="task-edit"
          :task-name="task.taskName"
        />

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
.task-docs-section {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
}

.task-docs-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.input-error {
  border-color: #ef4444 !important;
}

.field-error {
  font-size: 12px;
  color: #dc2626;
  margin-top: 2px;
}

.execution-type-toggle {
  display: flex;
  gap: 0;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  overflow: hidden;
  width: fit-content;
}

.toggle-btn {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  background: #f9fafb;
  color: #6b7280;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.toggle-btn:not(:last-child) {
  border-right: 1px solid #d1d5db;
}

.toggle-btn.active {
  background: #3b82f6;
  color: white;
}

.toggle-btn:hover:not(.active) {
  background: #f3f4f6;
}

.execution-type-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.manual-steps-hint {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
}

.manual-steps-title {
  font-size: 13px;
  font-weight: 600;
  color: #92400e;
  margin-bottom: 6px;
}

.manual-steps-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #78350f;
  line-height: 1.7;
}

.manual-steps-list strong {
  color: #92400e;
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
