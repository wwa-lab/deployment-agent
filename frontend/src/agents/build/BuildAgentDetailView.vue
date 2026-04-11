<script setup lang="ts">
import AgentDetailView from '../../platform/components/AgentDetailView.vue'
import { buildAgent, useBuildAgentStore } from './index'

const store = useBuildAgentStore()
</script>

<template>
  <AgentDetailView
    :agent-key="buildAgent.config.agentKey"
    :agent-name="buildAgent.config.agentName"
    :supports-stitching="buildAgent.config.supportsStitching"
    :store="store"
  />
</template>
