<script setup lang="ts">
import ReleaseFlowSummaryView from '../../platform/components/ReleaseFlowSummaryView.vue'
import type { ReleaseFlowSummaryCopy } from '../../platform/components/ReleaseFlowSummaryView.vue'
import { useTestingAgentReleaseFlowStore } from './index'
import { uploadFile, downloadTemplate } from './api'
import type { Stage } from '../../types'

const store = useTestingAgentReleaseFlowStore()

const stages: Stage[] = ['UAT']

const copy: ReleaseFlowSummaryCopy = {
  agentTitle: 'Testing Agent',
  agentSubtitle:
    'Track iSeries A/B testing rundowns by program level with human-in-the-loop controls.',
  introTitleId: 'wwa-testing-intro-title',
  introHeading: 'iSeries A/B testing with human control',
  introBody:
    'WWA currently supports a Testing Agent workspace. Testing Agent is the A/B testing workspace for iSeries programs, enabling controlled rundowns by program level with human-in-the-loop sign-off at every step.',
  loadingLabel: 'Loading workflows...',
  emptyHeading: 'No workflows found.',
  emptyHint: 'Upload a workflow file to get started.',
  releaseIdColumnLabel: 'Workflow ID',
}
</script>

<template>
  <ReleaseFlowSummaryView
    agent-key="testing-agent"
    :store="store"
    :stages="stages"
    :allowed-upload-stages="stages"
    :upload-fn="uploadFile"
    :download-template-fn="downloadTemplate"
    :copy="copy"
  />
</template>
