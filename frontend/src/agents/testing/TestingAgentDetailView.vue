<script setup lang="ts">
import ReleaseFlowDetailView from '../../platform/components/ReleaseFlowDetailView.vue'
import { testingCopy } from '../../platform/composables/releaseFlowTypes'
import type { ReleaseFlowDetailApi } from '../../platform/composables/releaseFlowTypes'
import { useTestingAgentReleaseFlowStore } from './index'
import {
  archiveRequestRundown,
  cloneTask,
  editExecutionType,
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

const store = useTestingAgentReleaseFlowStore()

const api: ReleaseFlowDetailApi = {
  archiveRequestRundown,
  restoreRequestRundown,
  purgeRequestRundown,
  startRequestDeployment,
  markRequestFailed,
  updateRequestRundown,
  editTask,
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
    agent-key="testing-agent"
    :store="store"
    :api="api"
    :copy="testingCopy"
  />
</template>
