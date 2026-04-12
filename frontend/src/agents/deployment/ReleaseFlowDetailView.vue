<script setup lang="ts">
import ReleaseFlowDetailView from '../../platform/components/ReleaseFlowDetailView.vue'
import { deploymentCopy } from '../../platform/composables/releaseFlowTypes'
import type { ReleaseFlowDetailApi } from '../../platform/composables/releaseFlowTypes'
import { useReleaseFlowStore } from './index'
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
  startManualExecution,
  startRequestDeployment,
  submitAutoExecution,
  submitDecision,
  updateRequestRundown,
} from './api'

const store = useReleaseFlowStore()

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
    agent-key="deployment-agent"
    :store="store"
    :api="api"
    :copy="deploymentCopy"
    :auto-refresh="true"
  />
</template>
