<script setup lang="ts">
import ReleaseFlowDetailView from '../../platform/components/ReleaseFlowDetailView.vue'
import { buildCopy } from '../../platform/composables/releaseFlowTypes'
import type { ReleaseFlowDetailApi } from '../../platform/composables/releaseFlowTypes'
import { useBuildAgentStore } from './index'
import { resolveBuildTaskDocs } from './taskDocs'
import {
  archiveRequestRundown,
  cloneTask,
  editExecutionType,
  editNames,
  editTask,
  getTaskResult,
  listTaskExecutions,
  markRequestFailed,
  purgeRequestRundown,
  recordResult,
  reorderTasks,
  restoreRequestRundown,
  saveTaskDocs,
  startManualExecution,
  startRequestDeployment,
  submitAutoExecution,
  submitDecision,
  updateRequestRundown,
} from './api'

const store = useBuildAgentStore()

const api: ReleaseFlowDetailApi = {
  archiveRequestRundown,
  restoreRequestRundown,
  purgeRequestRundown,
  startRequestDeployment,
  markRequestFailed,
  updateRequestRundown,
  editTask,
  editNames,
  editExecutionType,
  saveTaskDocs,
  recordResult,
  submitDecision,
  submitAutoExecution,
  startManualExecution,
  cloneTask,
  reorderTasks,
  listTaskExecutions,
  getTaskResult,
}
</script>

<template>
  <ReleaseFlowDetailView
    agent-key="build-agent"
    :store="store"
    :api="api"
    :copy="buildCopy"
    :task-docs-resolver="resolveBuildTaskDocs"
    :auto-refresh="true"
  />
</template>
