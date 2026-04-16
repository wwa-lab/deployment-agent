import type {
  Request,
  RequestArchiveResult,
  RequestPurgeResult,
  Task,
  TaskExecutionHistory,
  TaskResult,
} from '../../types'

export interface TaskDocLink {
  label: string
  url: string
  note?: string
  required?: boolean
}

export interface TaskSkillRef {
  key: string
  label: string
  role: 'primary' | 'review' | 'related'
}

export interface TaskDocSpec {
  primarySkill: TaskSkillRef
  relatedSkills?: TaskSkillRef[]
  inputs: TaskDocLink[]
  outputs: TaskDocLink[]
  suggestedInputs?: TaskDocLink[]
  suggestedOutputs?: TaskDocLink[]
  hasOverrides?: boolean
}

/**
 * Full Release Flow API bundle consumed by the shared
 * `ReleaseFlowDetailView.vue`. Each agent's detail wrapper builds this from
 * its own `agents/<name>/api.ts` module and passes it as a single prop, so
 * the shared view never imports from any specific agent.
 */
export interface ReleaseFlowDetailApi {
  archiveRequestRundown: (flowId: string, requestId: string) => Promise<RequestArchiveResult>
  restoreRequestRundown: (flowId: string, requestId: string) => Promise<RequestArchiveResult>
  purgeRequestRundown: (flowId: string, requestId: string) => Promise<RequestPurgeResult>
  startRequestDeployment: (flowId: string, requestId: string) => Promise<Request>
  markRequestFailed: (flowId: string, requestId: string) => Promise<Request>
  updateRequestRundown: (
    flowId: string,
    requestId: string,
    input: {
      snowGroup?: string
      application?: string
      agent?: string
      owner?: string
      site?: string
      estimatedRemainingMinutes?: number
    },
  ) => Promise<Request>
  editTask: (taskId: string, inputParameters: Record<string, unknown>) => Promise<Task>
  editNames: (taskId: string, names: { taskName?: string; taskGroupName?: string }) => Promise<Task>
  editExecutionType: (taskId: string, executionType: 'MANUAL' | 'AUTO') => Promise<Task>
  saveTaskDocs?: (
    taskId: string,
    docs: { inputs: TaskDocLink[]; outputs: TaskDocLink[] },
  ) => Promise<Task>
  recordResult: (
    taskId: string,
    body: { resultSummary: Record<string, unknown>; resultLogs?: string },
  ) => Promise<Task>
  submitDecision: (taskId: string, decision: string) => Promise<Task>
  submitAutoExecution: (taskId: string) => Promise<Task>
  startManualExecution: (taskId: string) => Promise<Task>
  cloneTask: (taskId: string) => Promise<Task>
  reorderTasks: (requestId: string, taskIds: string[]) => Promise<Task[]>
  listTaskExecutions: (taskId: string) => Promise<TaskExecutionHistory[]>
  getTaskResult: (taskId: string, executionId?: string) => Promise<TaskResult>
}

/**
 * Terminology overrides that differ between Deployment Agent (release flow
 * terminology) and Testing Agent (workflow terminology). Every string used
 * by the shared detail view lives here so that copy changes never require
 * touching the shared component.
 */
export interface ReleaseFlowDetailCopy {
  loadingLabel: string
  releaseIdLabel: string
  stitchedPrefix: string
  flowStatusLabel: string
  currentStageLabel?: string
  stageFieldLabel?: string
  hideInitialAttempt?: boolean
  lifecycleMode?: boolean
  orderedStages?: string[]
  startButtonLabel: string
  emptyRequestsLabel: string
  archiveLastStageMessage: (stage: string) => string
  archiveNonLastStageMessage: (stage: string) => string
  purgeLastStageMessage: (stage: string) => string
  purgeNonLastStageMessage: (stage: string) => string
}

export const deploymentCopy: ReleaseFlowDetailCopy = {
  loadingLabel: 'Loading release flow...',
  releaseIdLabel: 'Release ID',
  stitchedPrefix: 'Stitched from',
  flowStatusLabel: 'Flow Status',
  currentStageLabel: 'Current Stage',
  stageFieldLabel: 'Environment',
  hideInitialAttempt: false,
  lifecycleMode: false,
  startButtonLabel: 'Start Deployment',
  emptyRequestsLabel: 'No requests found.',
  archiveLastStageMessage: (stage) =>
    `Archive the ${stage} rundown? This is the last active stage, so the entire release flow will move into Archived and disappear from the default list.`,
  archiveNonLastStageMessage: (stage) =>
    `Archive the ${stage} rundown and hide it from the default workflow view?`,
  purgeLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and will permanently remove the entire release flow because no other rundowns remain.`,
  purgeNonLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and removes its archived task history from the system.`,
}

export const testingCopy: ReleaseFlowDetailCopy = {
  loadingLabel: 'Loading workflow...',
  releaseIdLabel: 'Workflow ID',
  stitchedPrefix: 'Grouped from',
  flowStatusLabel: 'Workflow Status',
  currentStageLabel: 'Current Stage',
  stageFieldLabel: 'Environment',
  hideInitialAttempt: false,
  lifecycleMode: false,
  startButtonLabel: 'Start Rundown',
  emptyRequestsLabel: 'No rundowns found.',
  archiveLastStageMessage: (stage) =>
    `Archive the ${stage} rundown? This is the last active stage, so the entire workflow will move into Archived and disappear from the default list.`,
  archiveNonLastStageMessage: (stage) =>
    `Archive the ${stage} rundown and hide it from the default workflow view?`,
  purgeLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and will permanently remove the entire workflow because no other rundowns remain.`,
  purgeNonLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and removes its archived task history from the system.`,
}

export const buildCopy: ReleaseFlowDetailCopy = {
  loadingLabel: 'Loading build workflow...',
  releaseIdLabel: 'Workflow ID',
  stitchedPrefix: 'Grouped from',
  flowStatusLabel: 'Workflow Status',
  currentStageLabel: 'Current Stage',
  stageFieldLabel: 'Environment',
  hideInitialAttempt: false,
  lifecycleMode: false,
  startButtonLabel: 'Start Rundown',
  emptyRequestsLabel: 'No rundowns found.',
  archiveLastStageMessage: (stage) =>
    `Archive the ${stage} rundown? This is the last active stage, so the entire workflow will move into Archived and disappear from the default list.`,
  archiveNonLastStageMessage: (stage) =>
    `Archive the ${stage} rundown and hide it from the default workflow view?`,
  purgeLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and will permanently remove the entire workflow because no other rundowns remain.`,
  purgeNonLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and removes its archived task history from the system.`,
}

export const projectCopy: ReleaseFlowDetailCopy = {
  loadingLabel: 'Loading project workflow...',
  releaseIdLabel: 'Lifecycle ID',
  stitchedPrefix: 'Grouped from',
  flowStatusLabel: 'Workflow Status',
  currentStageLabel: 'Current Lifecycle Stage',
  stageFieldLabel: 'Stage',
  hideInitialAttempt: true,
  lifecycleMode: true,
  orderedStages: [
    'REQUIREMENT',
    'FUNCTIONAL_DESIGN',
    'TECHNICAL_DESIGN',
    'DEVELOPMENT',
    'TESTING',
    'PERFORMANCE_TEST',
    'RESULT_SIGNOFF',
    'BUSINESS_ENDORSEMENT',
    'CAB',
    'DEPLOYMENT',
    'POST_IMPLEMENTATION',
  ],
  startButtonLabel: 'Start Rundown',
  emptyRequestsLabel: 'No lifecycle rundowns found.',
  archiveLastStageMessage: (stage) =>
    `Archive the ${stage} rundown? This is the last active stage, so the entire project workflow will move into Archived and disappear from the default list.`,
  archiveNonLastStageMessage: (stage) =>
    `Archive the ${stage} rundown and hide it from the default project workflow view?`,
  purgeLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and will permanently remove the entire project workflow because no other rundowns remain.`,
  purgeNonLastStageMessage: (stage) =>
    `Delete the ${stage} rundown permanently? This is irreversible and removes its archived task history from the system.`,
}
