<script setup lang="ts">
import ReleaseFlowSummaryView from '../../platform/components/ReleaseFlowSummaryView.vue'
import type { ReleaseFlowSummaryCopy } from '../../platform/components/ReleaseFlowSummaryView.vue'
import { useProjectAgentStore } from './index'
import { uploadFile, downloadTemplate } from './api'
import type { Stage } from '../../types'

const store = useProjectAgentStore()

const stages: Stage[] = [
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
]

const copy: ReleaseFlowSummaryCopy = {
  agentTitle: 'Project Agent',
  agentSubtitle:
    'Track project lifecycle work from bulletin intake through deployment and post-implementation closure.',
  introTitleId: 'wwa-project-intro-title',
  introHeading: 'Project lifecycle orchestration with human checkpoints',
  introBody:
    'Coordinate requirement intake, design, build, testing, governance, deployment, and post-implementation follow-up in one workspace.',
  loadingLabel: 'Loading project workflows...',
  emptyHeading: 'No project workflows found.',
  emptyHint: 'Upload a lifecycle workbook to get started.',
  releaseIdColumnLabel: 'Lifecycle ID',
  lifecycleMode: true,
  currentStageColumnLabel: 'Current Lifecycle Stage',
  stageFilterLabel: 'Current Lifecycle Stage',
  orderedStages: stages,
  showApplicationFilter: false,
  showAgentFilter: false,
  showAttemptViewFilter: false,
}
</script>

<template>
  <ReleaseFlowSummaryView
    agent-key="project-agent"
    :store="store"
    :stages="stages"
    :allowed-upload-stages="stages"
    :upload-fn="uploadFile"
    :download-template-fn="downloadTemplate"
    :copy="copy"
  />
</template>
