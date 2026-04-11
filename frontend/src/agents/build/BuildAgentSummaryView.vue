<script setup lang="ts">
import AgentSummaryView from '../../platform/components/AgentSummaryView.vue'
import { buildAgent, useBuildAgentStore } from './index'

const store = useBuildAgentStore()
</script>

<template>
  <AgentSummaryView
    :agent-key="buildAgent.config.agentKey"
    :agent-name="buildAgent.config.agentName"
    :stages="buildAgent.config.stages"
    :supports-stitching="buildAgent.config.supportsStitching"
    :store="store"
  />
</template>
