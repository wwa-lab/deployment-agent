import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  AtlasApiError,
  downloadArtifactContent,
  getCapabilityUsage,
  getIntegrationTask,
  getReviewDecision,
  listArtifacts,
  listAllIntegrationTasks,
  listExecutions,
  rerunIntegrationTask,
  submitReviewDecision,
} from '../api/atlasIntegration'
import type {
  CapabilityUsage,
  CapabilityUsageFilters,
  IntegrationArtifact,
  IntegrationExecution,
  IntegrationReview,
  IntegrationReviewDecision,
  IntegrationTask,
  TaskListFilters,
} from '../platform/integration/types'

export const useAtlasIntegrationStore = defineStore('atlasIntegration', () => {
  const tasks = ref<IntegrationTask[]>([])
  const selectedTaskId = ref('')
  const taskDetail = ref<IntegrationTask | null>(null)
  const executions = ref<IntegrationExecution[]>([])
  const artifacts = ref<IntegrationArtifact[]>([])
  const review = ref<IntegrationReview | null>(null)
  const capabilityUsage = ref<CapabilityUsage | null>(null)

  const taskFilters = reactive<TaskListFilters>({ limit: 100 })
  const usageFilters = reactive<CapabilityUsageFilters>({})

  const loadingTasks = ref(false)
  const loadingWorkspace = ref(false)
  const loadingUsage = ref(false)
  const submittingReview = ref(false)
  const rerunningTask = ref(false)
  const downloadingArtifactId = ref('')

  const taskError = ref('')
  const workspaceError = ref('')
  const usageError = ref('')
  const mutationError = ref('')

  let workspaceRequestNumber = 0
  let refreshInFlight: Promise<void> | null = null

  const selectedTask = computed(() => {
    if (taskDetail.value?.taskId === selectedTaskId.value) return taskDetail.value
    return tasks.value.find((task) => task.taskId === selectedTaskId.value) ?? null
  })
  const latestExecution = computed(() => executions.value[0] ?? null)
  const awaitingReviewCount = computed(
    () => tasks.value.filter((task) => task.status === 'AWAITING_REVIEW').length,
  )
  const pendingSyncCount = computed(
    () => tasks.value.filter((task) => Boolean(task.activeExecutionId)).length,
  )
  const latestFailureReason = computed(
    () => executions.value.find((execution) => execution.failureReason)?.failureReason ?? null,
  )

  async function performTaskRefresh(silent: boolean) {
    if (!silent) loadingTasks.value = true
    taskError.value = ''
    try {
      const result = await listAllIntegrationTasks(taskFilters)
      tasks.value = result

      const currentStillVisible = result.some((task) => task.taskId === selectedTaskId.value)
      const nextTaskId = currentStillVisible ? selectedTaskId.value : (result[0]?.taskId ?? '')
      if (nextTaskId !== selectedTaskId.value) clearWorkspace()
      selectedTaskId.value = nextTaskId

      if (nextTaskId) {
        await fetchTaskWorkspace(nextTaskId, silent)
      } else {
        clearWorkspace()
      }
    } catch (error) {
      if (isAuthorizationLoss(error)) clearOperationalData()
      taskError.value = errorMessage(error, 'Failed to load Atlas tasks')
    } finally {
      if (!silent) loadingTasks.value = false
    }
  }

  async function fetchTasks(silent = false) {
    while (refreshInFlight) await refreshInFlight
    const operation = performTaskRefresh(silent)
    refreshInFlight = operation
    try {
      await operation
    } finally {
      if (refreshInFlight === operation) refreshInFlight = null
    }
  }

  async function fetchTaskWorkspace(taskId = selectedTaskId.value, silent = false) {
    if (!taskId) {
      clearWorkspace()
      return
    }

    const requestNumber = ++workspaceRequestNumber
    if (!silent) loadingWorkspace.value = true
    workspaceError.value = ''

    try {
      const [detail, history] = await Promise.all([
        getIntegrationTask(taskId),
        listExecutions(taskId),
      ])
      if (requestNumber !== workspaceRequestNumber || selectedTaskId.value !== taskId) return

      const latest = history[0]
      const [artifactResult, reviewResult] = await Promise.all([
        latest ? listArtifacts(latest.executionId) : Promise.resolve([]),
        latest && detail.status !== 'AWAITING_REVIEW'
          ? getReviewDecision(latest.executionId).catch((error) => {
              if (error instanceof AtlasApiError && error.code === 'REVIEW_DECISION_NOT_FOUND') return null
              throw error
            })
          : Promise.resolve(null),
      ])
      if (requestNumber !== workspaceRequestNumber || selectedTaskId.value !== taskId) return

      taskDetail.value = detail
      executions.value = history
      artifacts.value = artifactResult
      review.value = reviewResult
      tasks.value = tasks.value.map((task) => task.taskId === detail.taskId ? detail : task)
    } catch (error) {
      if (requestNumber === workspaceRequestNumber) {
        if (isAuthorizationLoss(error)) clearOperationalData()
        workspaceError.value = errorMessage(error, 'Failed to load execution evidence')
      }
    } finally {
      if (!silent && requestNumber === workspaceRequestNumber) loadingWorkspace.value = false
    }
  }

  async function selectTask(taskId: string) {
    if (taskId === selectedTaskId.value && taskDetail.value) return
    selectedTaskId.value = taskId
    clearWorkspace(false)
    await fetchTaskWorkspace(taskId)
  }

  async function fetchCapabilityUsage() {
    loadingUsage.value = true
    usageError.value = ''
    try {
      capabilityUsage.value = await getCapabilityUsage(usageFilters)
    } catch (error) {
      if (isAuthorizationLoss(error)) capabilityUsage.value = null
      if (error instanceof AtlasApiError && error.status === 401) clearOperationalData()
      usageError.value = errorMessage(error, 'Failed to load capability usage')
    } finally {
      loadingUsage.value = false
    }
  }

  function refreshOperationalData(): Promise<void> {
    if (refreshInFlight) return refreshInFlight
    return fetchTasks(true)
  }

  async function submitReview(decision: IntegrationReviewDecision, comment: string) {
    const task = selectedTask.value
    const execution = latestExecution.value
    if (!task || !execution || !task.actions.review) {
      mutationError.value = 'This execution is not available for review.'
      return false
    }

    submittingReview.value = true
    mutationError.value = ''
    try {
      review.value = await submitReviewDecision(execution.executionId, decision, comment)
      await Promise.all([fetchTasks(true), fetchCapabilityUsage()])
      return true
    } catch (error) {
      if (isAuthorizationLoss(error)) clearOperationalData()
      mutationError.value = errorMessage(error, 'Failed to submit review decision')
      return false
    } finally {
      submittingReview.value = false
    }
  }

  async function rerunSelectedTask() {
    const task = selectedTask.value
    const execution = latestExecution.value
    if (!task || !execution || !task.actions.rerun) {
      mutationError.value = 'This task is not available for rerun.'
      return false
    }
    rerunningTask.value = true
    mutationError.value = ''
    try {
      await rerunIntegrationTask(task.taskId, execution.executionId)
      await Promise.all([fetchTasks(true), fetchCapabilityUsage()])
      return true
    } catch (error) {
      if (isAuthorizationLoss(error)) clearOperationalData()
      mutationError.value = errorMessage(error, 'Failed to rerun task')
      return false
    } finally {
      rerunningTask.value = false
    }
  }

  async function downloadArtifact(artifact: IntegrationArtifact): Promise<Blob | null> {
    if (!artifact.executionId) {
      mutationError.value = 'This artifact is not attached to an execution.'
      return null
    }
    downloadingArtifactId.value = artifact.artifactId
    mutationError.value = ''
    try {
      return await downloadArtifactContent(artifact.executionId, artifact.artifactId)
    } catch (error) {
      if (isAuthorizationLoss(error)) clearOperationalData()
      mutationError.value = errorMessage(error, 'Failed to download artifact')
      return null
    } finally {
      downloadingArtifactId.value = ''
    }
  }

  function clearErrors() {
    taskError.value = ''
    workspaceError.value = ''
    usageError.value = ''
    mutationError.value = ''
  }

  function clearWorkspace(invalidateRequests = true) {
    if (invalidateRequests) workspaceRequestNumber += 1
    taskDetail.value = null
    executions.value = []
    artifacts.value = []
    review.value = null
  }

  function clearOperationalData() {
    tasks.value = []
    selectedTaskId.value = ''
    capabilityUsage.value = null
    loadingWorkspace.value = false
    loadingUsage.value = false
    clearWorkspace()
  }

  return {
    tasks,
    selectedTaskId,
    selectedTask,
    taskDetail,
    executions,
    latestExecution,
    artifacts,
    review,
    capabilityUsage,
    taskFilters,
    usageFilters,
    loadingTasks,
    loadingWorkspace,
    loadingUsage,
    submittingReview,
    rerunningTask,
    downloadingArtifactId,
    taskError,
    workspaceError,
    usageError,
    mutationError,
    awaitingReviewCount,
    pendingSyncCount,
    latestFailureReason,
    fetchTasks,
    fetchTaskWorkspace,
    selectTask,
    fetchCapabilityUsage,
    refreshOperationalData,
    submitReview,
    rerunSelectedTask,
    downloadArtifact,
    clearErrors,
  }
})

function errorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback
  if (error instanceof AtlasApiError && error.requestId) {
    return `${error.message} Reference: ${error.requestId}`
  }
  return error.message || fallback
}

function isAuthorizationLoss(error: unknown): boolean {
  return error instanceof AtlasApiError
    && (error.status === 401 || error.status === 403 || error.status === 404)
}
