<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Store } from 'pinia'

interface AgentDetailProps {
  agentKey: string
  agentName: string
  supportsStitching: boolean
  store: Store<string, any>
}

const props = defineProps<AgentDetailProps>()
const route = useRoute()

function loadFromRoute() {
  const id = route.params.id as string
  const includeArchived = route.query.archived === '1'
  const linked = props.supportsStitching
    ? (route.query.linked as string | undefined)
    : undefined
  props.store.selectFlowWithArchived(id, includeArchived, linked)
}

onMounted(() => {
  loadFromRoute()
})

watch(
  () => route.params.id,
  () => loadFromRoute(),
)
</script>

<template>
  <div class="agent-detail">
    <div class="view-header">
      <h1 class="view-title">{{ agentName }}</h1>
      <p v-if="store.detail">Release: {{ store.detail.releaseId }}</p>
    </div>
    <section v-if="store.detail">
      <h2>{{ store.detail.projectName }}</h2>
      <p>Current stage: {{ store.detail.currentStage }}</p>
      <p>Flow status: {{ store.detail.flowStatus }}</p>
      <p>Review status: {{ store.detail.reviewStatus }}</p>

      <div v-for="req in store.detail.requests" :key="req.id" class="request-card">
        <h3>{{ req.stage }} — attempt {{ req.attemptNumber }}</h3>
        <ul>
          <li v-for="task in req.tasks" :key="task.id">
            {{ task.taskName }} — {{ task.taskStatus }}
          </li>
        </ul>
      </div>
    </section>
    <p v-else>Loading…</p>
  </div>
</template>
