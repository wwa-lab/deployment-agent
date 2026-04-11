<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import type { Store } from 'pinia'
import type { ReleaseFlowListItem, RequestStatus } from '../../types'

interface AgentSummaryProps {
  agentKey: string
  agentName: string
  stages: string[]
  supportsStitching: boolean
  store: Store<string, any>
}

const props = defineProps<AgentSummaryProps>()
const router = useRouter()

onMounted(() => {
  props.store.fetchList()
  props.store.startPolling?.()
})

onUnmounted(() => {
  props.store.stopPolling?.()
})

function goToDetail(flow: ReleaseFlowListItem) {
  const linked = props.supportsStitching && flow.linkedReleaseFlowIds.length > 1
    ? flow.linkedReleaseFlowIds.join(',')
    : undefined
  router.push({
    path: `/wwa/${props.agentKey}/release-flows/${flow.id}`,
    query: linked ? { linked } : {},
  })
}

function stageStatus(flow: ReleaseFlowListItem, stage: string): RequestStatus {
  return flow.stageStatuses?.[stage] ?? 'Pending'
}

function stagePresent(flow: ReleaseFlowListItem, stage: string): boolean {
  return flow.stagesPresent?.includes(stage) ?? false
}

const totalPages = computed(() => Math.max(1, Math.ceil((props.store.total ?? 0) / (props.store.size ?? 10))))
</script>

<template>
  <div class="agent-summary">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Workspace</p>
        <h1 class="view-title">{{ agentName }}</h1>
      </div>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>Release</th>
          <th>Current Stage</th>
          <th v-for="stage in stages" :key="stage">{{ stage }}</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="flow in (store.list as ReleaseFlowListItem[])" :key="flow.id" @click="goToDetail(flow)">
          <td>{{ flow.releaseId }}</td>
          <td>{{ flow.currentStage }}</td>
          <td v-for="stage in stages" :key="stage">
            <span v-if="stagePresent(flow, stage)">{{ stageStatus(flow, stage) }}</span>
            <span v-else>—</span>
          </td>
          <td>{{ flow.flowStatus }}</td>
        </tr>
      </tbody>
    </table>

    <div class="pagination">
      <button :disabled="store.page <= 0" @click="store.setPage(store.page - 1); store.fetchList()">Prev</button>
      <span>Page {{ store.page + 1 }} / {{ totalPages }}</span>
      <button :disabled="store.page + 1 >= totalPages" @click="store.setPage(store.page + 1); store.fetchList()">Next</button>
    </div>
  </div>
</template>
