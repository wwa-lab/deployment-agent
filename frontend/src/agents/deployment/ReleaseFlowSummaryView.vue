<script setup lang="ts">
import ReleaseFlowSummaryView from '../../platform/components/ReleaseFlowSummaryView.vue'
import type { ReleaseFlowSummaryCopy } from '../../platform/components/ReleaseFlowSummaryView.vue'
import { useReleaseFlowStore } from './index'
import { uploadFile, downloadTemplate } from './api'
import type { Stage } from '../../types'

const store = useReleaseFlowStore()

const stages: Stage[] = ['SIT', 'UAT', 'PROD']

const copy: ReleaseFlowSummaryCopy = {
  agentTitle: 'Deployment Agent',
  agentSubtitle:
    'Track release flows, upload deployment files, and monitor stage progress across SIT, UAT, and PROD.',
  introTitleId: 'wwa-deployment-intro-title',
  introHeading: 'DevOps automation with human control',
  introBody:
    'Track and progress release rundowns across SIT, UAT, and PROD with human-in-the-loop approval at every stage.',
  loadingLabel: 'Loading release flows...',
  emptyHeading: 'No release flows found.',
  emptyHint: 'Upload a release file to get started.',
  releaseIdColumnLabel: 'Release ID',
}
</script>

<template>
  <ReleaseFlowSummaryView
    agent-key="deployment-agent"
    :store="store"
    :stages="stages"
    :allowed-upload-stages="stages"
    :upload-fn="uploadFile"
    :download-template-fn="downloadTemplate"
    :copy="copy"
    :supports-stitching="true"
  />
</template>
