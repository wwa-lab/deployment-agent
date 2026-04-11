<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAuditLogs } from '../api/audit'
import type { AuditLogEntry, Task, TaskExecutionHistory } from '../types'

type ActivityTone = 'success' | 'fail' | 'neutral'

type ActivityRow = {
  id: string
  action: string
  actor: string
  actorMeta?: string
  time?: string
  input: string
  inputTitle: string
  output: string
  outputTitle: string
  resultLabel: string
  resultTone: ActivityTone
  source: 'Task' | 'Audit' | 'Execution'
  rawType: string
}

const props = defineProps<{
  task: Task
  listTaskExecutionsFn: (taskId: string) => Promise<TaskExecutionHistory[]>
}>()
const emit = defineEmits<{ close: [] }>()

const loading = ref(false)
const warnings = ref<string[]>([])
const auditLogs = ref<AuditLogEntry[]>([])
const executions = ref<TaskExecutionHistory[]>([])

onMounted(async () => {
  loading.value = true
  warnings.value = []

  const [auditResult, executionResult] = await Promise.allSettled([
    listAuditLogs({ taskId: props.task.id, page: 0, size: 100 }),
    props.listTaskExecutionsFn(props.task.id),
  ])

  if (auditResult.status === 'fulfilled') {
    auditLogs.value = auditResult.value.data
  } else {
    warnings.value.push(
      auditResult.reason instanceof Error
        ? `Audit activity could not be loaded: ${auditResult.reason.message}`
        : 'Audit activity could not be loaded.',
    )
  }

  if (executionResult.status === 'fulfilled') {
    executions.value = executionResult.value
  } else {
    warnings.value.push(
      executionResult.reason instanceof Error
        ? `Execution history could not be loaded: ${executionResult.reason.message}`
        : 'Execution history could not be loaded.',
    )
  }

  loading.value = false
})

function compactJson(value: unknown, fallback = '—', maxLength = 180): string {
  if (value === undefined || value === null) {
    return fallback
  }

  try {
    const raw = JSON.stringify(value)
    if (!raw) return fallback
    return raw.length > maxLength ? `${raw.slice(0, maxLength - 3)}...` : raw
  } catch {
    const text = String(value)
    return text.length > maxLength ? `${text.slice(0, maxLength - 3)}...` : text
  }
}

function activityLabel(log: AuditLogEntry): string {
  if (log.actionType === 'view_result' && log.contextPayload?.action === 'record_result') {
    return 'Record Result'
  }

  const labels: Record<string, string> = {
    upload: 'Upload',
    edit: 'Edit',
    view_result: 'View Result',
    approve: 'Approve',
    reject: 'Reject',
    rerun: 'Rerun',
    skip: 'Skip',
    config_update: 'Config Update',
    auto_submit: 'Auto Submit',
    request_start: 'Start Workflow',
    request_fail: 'Mark as Failed',
  }

  return labels[log.actionType] ?? log.actionType
}

function deriveAuditResult(log: AuditLogEntry): { label: string; tone: ActivityTone } {
  if (log.actionType === 'request_fail' || log.actionType === 'reject') {
    return { label: 'Fail', tone: 'fail' }
  }

  if (log.actionType === 'skip') {
    return { label: 'Skipped', tone: 'neutral' }
  }

  if (log.actionType === 'auto_submit') {
    const status = String(log.contextPayload?.submissionStatus ?? '').toUpperCase()
    if (status === 'FAILED') {
      return { label: 'Fail', tone: 'fail' }
    }
  }

  return { label: 'Success', tone: 'success' }
}

function auditOutput(log: AuditLogEntry): string {
  const payload = log.contextPayload ?? {}

  if (payload.externalJobUrl) {
    return compactJson({ externalJobUrl: payload.externalJobUrl, submissionStatus: payload.submissionStatus })
  }

  if (log.actionType === 'edit' && (payload.newValue || payload.transitionTo)) {
    return compactJson({
      newValue: payload.newValue,
      transitionTo: payload.transitionTo,
      configKey: payload.configKey,
    })
  }

  if (payload.comment) {
    return compactJson({ comment: payload.comment })
  }

  if (log.requestId || log.taskId || log.releaseFlowId) {
    return compactJson({
      releaseFlowId: log.releaseFlowId,
      requestId: log.requestId,
      taskId: log.taskId,
    })
  }

  return '—'
}

function auditInput(log: AuditLogEntry): string {
  const payload = log.contextPayload ?? {}

  if (log.actionType === 'edit') {
    if (payload.oldValue !== undefined || payload.transitionFrom !== undefined) {
      return compactJson({
        oldValue: payload.oldValue,
        transitionFrom: payload.transitionFrom,
        fieldChanged: payload.fieldChanged,
      })
    }
  }

  if (log.actionType === 'auto_submit') {
    return compactJson({
      systemType: payload.systemType,
      attemptNumber: payload.attemptNumber,
    })
  }

  if (log.actionType === 'approve' || log.actionType === 'reject' || log.actionType === 'rerun' || log.actionType === 'skip') {
    return compactJson({
      decisionType: payload.decisionType,
      comment: payload.comment,
      previousStatus: payload.previousStatus,
    })
  }

  return compactJson(payload)
}

function deriveExecutionResult(history: TaskExecutionHistory): { label: string; tone: ActivityTone } {
  const submissionStatus = String(history.submissionStatus ?? '').toUpperCase()
  const executionStatus = String(history.executionStatus ?? '').toUpperCase()

  if (submissionStatus === 'FAILED' || executionStatus === 'FAILED') {
    return { label: 'Fail', tone: 'fail' }
  }

  if (executionStatus === 'RUNNING') {
    return { label: 'Running', tone: 'neutral' }
  }

  return { label: 'Success', tone: 'success' }
}

function executionOutput(history: TaskExecutionHistory): string {
  if (history.resultSummary && Object.keys(history.resultSummary).length > 0) {
    return compactJson(history.resultSummary)
  }

  if (history.externalJobUrl || history.submissionMessage) {
    return compactJson({
      externalJobUrl: history.externalJobUrl,
      submissionMessage: history.submissionMessage,
      submissionStatus: history.submissionStatus,
    })
  }

  if (history.resultLogs) {
    return compactJson(history.resultLogs)
  }

  return '—'
}

function activityTime(value?: string): number {
  if (!value) return 0
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

const activityRows = computed<ActivityRow[]>(() => {
  const definitionOutput = props.task.expectedOutput
    ? compactJson(props.task.expectedOutput)
    : props.task.currentResultSummary
      ? compactJson(props.task.currentResultSummary)
      : 'No execution output yet'

  const definitionRow: ActivityRow = {
    id: `task:${props.task.id}`,
    action: 'Task Definition',
    actor: props.task.owner ?? 'Configured Task',
    actorMeta: props.task.executionType,
    time: props.task.lastUpdatedAt,
    input: compactJson(props.task.inputParameters, 'No input configured'),
    inputTitle: compactJson(props.task.inputParameters, 'No input configured', 4000),
    output: definitionOutput,
    outputTitle: definitionOutput,
    resultLabel: 'Defined',
    resultTone: 'neutral',
    source: 'Task',
    rawType: props.task.taskStatus,
  }

  const auditRows = auditLogs.value.map((log) => {
    const result = deriveAuditResult(log)
    const inputValue = auditInput(log)
    const inputTitle = auditInput(log)
    const outputTitle = auditOutput(log)

    return {
      id: `audit:${log.id}`,
      action: activityLabel(log),
      actor: log.operatorId,
      actorMeta: log.operatorRole,
      time: log.timestamp,
      input: inputValue,
      inputTitle,
      output: outputTitle,
      outputTitle,
      resultLabel: result.label,
      resultTone: result.tone,
      source: 'Audit',
      rawType: log.actionType,
    }
  })

  const executionRows = executions.value.map((history) => {
    const result = deriveExecutionResult(history)
    const time = history.submittedAt ?? history.endTime ?? history.startTime
    const inputTitle = compactJson(history.inputSnapshot, '{}', 4000)
    const output = executionOutput(history)

    return {
      id: `execution:${history.id}`,
      action: history.externalSystemType ? `${history.externalSystemType} Execution` : 'Execution Attempt',
      actor: history.externalSystemType ?? 'Execution Record',
      actorMeta: `Attempt #${history.attemptNumber}`,
      time,
      input: compactJson(history.inputSnapshot),
      inputTitle,
      output,
      outputTitle: output,
      resultLabel: result.label,
      resultTone: result.tone,
      source: 'Execution',
      rawType: String(history.executionStatus),
    }
  })

  return [definitionRow, ...auditRows, ...executionRows].sort(
    (left, right) => activityTime(right.time) - activityTime(left.time),
  )
})

function formatTimestamp(value?: string): string {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

const definitionInput = computed(() =>
  compactJson(props.task.inputParameters, 'No input configured', 4000),
)

const definitionOutput = computed(() => {
  if (props.task.expectedOutput) {
    return compactJson(props.task.expectedOutput, 'No expected output defined', 4000)
  }

  if (props.task.currentResultSummary) {
    return compactJson(props.task.currentResultSummary, 'No expected output defined', 4000)
  }

  return 'No expected output defined'
})
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal modal-wide activity-modal">
      <div class="modal-header">
        <span class="modal-title">Task Activity — {{ task.taskName }}</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="activity-summary">
          <div class="summary-item">
            <span class="summary-label">Task</span>
            <span class="summary-value">{{ task.taskGroupName }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Step</span>
            <span class="summary-value">{{ task.stepSeq }} · {{ task.taskName }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Owner</span>
            <span class="summary-value">{{ task.owner ?? '—' }}</span>
          </div>
        </div>

        <div class="definition-grid">
          <div class="definition-panel">
            <div class="definition-title">Current Input</div>
            <pre class="definition-pre">{{ definitionInput }}</pre>
          </div>
          <div class="definition-panel">
            <div class="definition-title">Expected Output</div>
            <pre class="definition-pre">{{ definitionOutput }}</pre>
          </div>
        </div>

        <div v-if="loading" class="loading-state">
          <span class="spinner"></span>
          <span>Loading task activity...</span>
        </div>

        <div v-if="warnings.length > 0" class="alert alert-error">
          <div v-for="warning in warnings" :key="warning">{{ warning }}</div>
        </div>

        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Action</th>
                <th>By</th>
                <th>Time</th>
                <th>Input</th>
                <th>Output</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in activityRows" :key="row.id">
                <td>
                  <div class="action-row">
                    <span class="action-label">{{ row.action }}</span>
                    <span class="result-badge" :class="`result-${row.resultTone}`">
                      {{ row.resultLabel }}
                    </span>
                  </div>
                  <div class="cell-meta">{{ row.source }} · {{ row.rawType }}</div>
                </td>
                <td>
                  <div>{{ row.actor }}</div>
                  <div v-if="row.actorMeta" class="cell-meta">{{ row.actorMeta }}</div>
                </td>
                <td class="timestamp">{{ formatTimestamp(row.time) }}</td>
                <td class="message-cell" :title="row.inputTitle">{{ row.input }}</td>
                <td class="message-cell" :title="row.outputTitle">{{ row.output }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Close</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.activity-modal {
  width: 1100px;
}

.activity-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.definition-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.definition-panel {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  padding: 14px;
}

.definition-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.definition-pre {
  margin: 0;
  font-family: monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #0f172a;
}

.summary-item {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.summary-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-label {
  font-weight: 600;
  color: #0f172a;
}

.result-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.result-success {
  color: #166534;
  background: #dcfce7;
}

.result-fail {
  color: #991b1b;
  background: #fee2e2;
}

.result-neutral {
  color: #475569;
  background: #e2e8f0;
}

.timestamp {
  white-space: nowrap;
  font-size: 12px;
  color: #64748b;
}

.message-cell {
  max-width: 260px;
  font-family: monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  word-break: break-word;
}

.cell-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

@media (max-width: 900px) {
  .activity-modal {
    width: min(96vw, 1100px);
  }

  .activity-summary {
    grid-template-columns: 1fr;
  }

  .definition-grid {
    grid-template-columns: 1fr;
  }
}
</style>
