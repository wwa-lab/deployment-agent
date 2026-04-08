<script setup lang="ts">
import { computed, ref } from 'vue'
import { submitDecision as submitDecisionApi } from '../api/tasks'
import type { Task } from '../types'

const props = withDefaults(defineProps<{
  task: Task
  initialDecision?: Decision | null
  allowedDecisions?: Decision[]
  submitDecisionFn?: (taskId: string, decision: string) => Promise<Task>
}>(), {
  initialDecision: null,
  allowedDecisions: () => ['Approve', 'Reject', 'Rerun', 'Skip'],
  submitDecisionFn: submitDecisionApi,
})
const emit = defineEmits<{ decided: []; close: [] }>()

type Decision = 'Approve' | 'Reject' | 'Rerun' | 'Skip'

const decisionCatalog: { value: Decision; label: string; description: string }[] = [
  { value: 'Approve', label: 'Approve', description: 'Approve this task and advance the flow.' },
  { value: 'Reject', label: 'Reject', description: 'Reject this task and halt the flow.' },
  { value: 'Rerun', label: 'Rerun', description: 'Queue this task for re-execution.' },
  { value: 'Skip', label: 'Skip', description: 'Skip this task and continue.' },
]

const selected = ref<Decision | null>(props.initialDecision ?? null)
const submitting = ref(false)
const error = ref('')
const successMsg = ref('')

const decisions = computed(() =>
  decisionCatalog.filter((decision) => props.allowedDecisions.includes(decision.value)),
)

const isSingleAction = computed(() => decisions.value.length === 1)

const dialogTitle = computed(() => {
  const actionLabel = decisions.value[0]?.label ?? 'Decision'
  return isSingleAction.value
    ? `${actionLabel} Task — ${props.task.taskName}`
    : `Decision — ${props.task.taskName}`
})

const confirmLabel = computed(() => {
  if (submitting.value) {
    return isSingleAction.value ? 'Submitting...' : 'Confirming...'
  }
  return isSingleAction.value
    ? `${decisions.value[0]?.label ?? 'Submit'}`
    : 'Confirm Decision'
})

async function submit() {
  if (!selected.value) return
  submitting.value = true
  error.value = ''
  successMsg.value = ''
  try {
    await props.submitDecisionFn(props.task.id, selected.value)
    successMsg.value = `Decision "${selected.value}" submitted successfully.`
    setTimeout(() => {
      emit('decided')
    }, 800)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to submit decision'
  } finally {
    submitting.value = false
  }
}

function decisionBtnClass(d: Decision): string {
  const map: Record<Decision, string> = {
    Approve: 'decision-approve',
    Reject: 'decision-reject',
    Rerun: 'decision-rerun',
    Skip: 'decision-skip',
  }
  return map[d]
}

function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    Pending: 'badge-pending',
    Running: 'badge-running',
    Executing: 'badge-executing',
    Completed: 'badge-completed',
    Failed: 'badge-failed',
    Rejected: 'badge-rejected',
    Approved: 'badge-approved',
    Awaiting_Review: 'badge-awaiting-review',
    Skipped: 'badge-skipped',
    Ready_For_Execution: 'badge-ready-for-execution',
    Pending_Review: 'badge-pending-review',
  }
  return map[status] ?? 'badge-pending'
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
        <div v-if="successMsg" class="alert alert-success">{{ successMsg }}</div>

        <div class="task-info">
          <span class="info-label">Task Group:</span> {{ task.taskGroupName }}
          &nbsp;|&nbsp;
          <span class="info-label">Status:</span>
          <span class="badge" :class="statusBadgeClass(task.taskStatus)">{{ task.taskStatus }}</span>
        </div>

        <div class="decision-list">
          <label
            v-for="d in decisions"
            :key="d.value"
            class="decision-option"
            :class="[{ selected: selected === d.value }, decisionBtnClass(d.value)]"
          >
            <input
              v-model="selected"
              type="radio"
              :value="d.value"
              class="radio-input"
            />
            <div class="decision-content">
              <span class="decision-label">{{ d.label }}</span>
              <span class="decision-desc">{{ d.description }}</span>
            </div>
          </label>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button
          class="btn btn-primary"
          :disabled="!selected || submitting"
          @click="submit"
        >
          <span v-if="submitting" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-info {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.info-label {
  font-weight: 600;
  color: #475569;
}

.decision-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.decision-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.decision-option:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.decision-option.selected.decision-approve {
  border-color: #16a34a;
  background: #f0fdf4;
}

.decision-option.selected.decision-reject {
  border-color: #dc2626;
  background: #fef2f2;
}

.decision-option.selected.decision-rerun {
  border-color: #2563eb;
  background: #eff6ff;
}

.decision-option.selected.decision-skip {
  border-color: #94a3b8;
  background: #f8fafc;
}

.radio-input {
  margin-top: 2px;
  flex-shrink: 0;
}

.decision-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.decision-label {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.decision-desc {
  font-size: 12px;
  color: #64748b;
}
</style>
