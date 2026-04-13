<script setup lang="ts">
import ReleaseFlowSummaryView from '../../platform/components/ReleaseFlowSummaryView.vue'
import type { ReleaseFlowSummaryCopy } from '../../platform/components/ReleaseFlowSummaryView.vue'
import { useBuildAgentStore } from './index'
import { uploadFile, downloadTemplate } from './api'
import type { Stage } from '../../types'

const store = useBuildAgentStore()

const stages: Stage[] = ['DEV']

const copy: ReleaseFlowSummaryCopy = {
  agentTitle: 'Build Agent',
  agentSubtitle:
    'Review DEV-stage build workflows, upload packaging rundowns, and drive task execution.',
  introTitleId: 'wwa-build-intro-title',
  introHeading: 'Build and packaging with human checkpoints',
  introBody:
    'Prepare release packages, run build tasks, and review execution evidence before downstream deployment.',
  loadingLabel: 'Loading build workflows...',
  emptyHeading: 'No build workflows found.',
  emptyHint: 'Upload a workflow file to get started.',
  releaseIdColumnLabel: 'Workflow ID',
}
</script>

<template>
  <ReleaseFlowSummaryView
    agent-key="build-agent"
    :store="store"
    :stages="stages"
    :allowed-upload-stages="stages"
    :upload-fn="uploadFile"
    :download-template-fn="downloadTemplate"
    :copy="copy"
  />
</template>
