<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useAuditStore } from '../stores/audit'
import { useUserStore } from '../stores/user'

const store = useAuditStore()
const userStore = useUserStore()

const hasAccess = computed(() => userStore.isAuditMgmt)

onMounted(() => {
  if (hasAccess.value) {
    store.fetchLogs()
  }
})

function formatTimestamp(ts: string): string {
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ts
  }
}

const totalPages = computed(() => Math.ceil(store.total / store.size))

function onPageChange(newPage: number) {
  store.setPage(newPage)
  store.fetchLogs()
}
</script>

<template>
  <div class="audit-view">
    <div class="view-header">
      <h1 class="view-title">Audit Log</h1>
    </div>

    <div v-if="!hasAccess" class="alert alert-error">
      Access denied. This page requires AUDIT_MGMT role.
    </div>

    <template v-else>
      <div v-if="store.loading && store.logs.length === 0" class="loading-state">
        <span class="spinner"></span>
        <span>Loading audit logs...</span>
      </div>

      <div v-else-if="!store.loading && store.logs.length === 0" class="empty-state">
        No audit logs found.
      </div>

      <div v-else class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Operator</th>
              <th>Role</th>
              <th>Action Type</th>
              <th>Release Flow</th>
              <th>Task</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in store.logs" :key="log.id">
              <td class="timestamp">{{ formatTimestamp(log.timestamp) }}</td>
              <td>{{ log.operatorId }}</td>
              <td>
                <span class="badge badge-role">{{ log.operatorRole }}</span>
              </td>
              <td>
                <span class="badge badge-pending">{{ log.actionType }}</span>
              </td>
              <td class="mono small">{{ log.releaseFlowId ?? '—' }}</td>
              <td class="mono small">{{ log.taskId ?? '—' }}</td>
            </tr>
          </tbody>
        </table>

        <div class="pagination">
          <span class="pagination-info">
            {{ store.total }} total | Page {{ store.page + 1 }} of {{ totalPages }}
          </span>
          <div class="pagination-controls">
            <button
              class="btn btn-secondary btn-sm"
              :disabled="store.page === 0"
              @click="onPageChange(store.page - 1)"
            >
              Prev
            </button>
            <button
              class="btn btn-secondary btn-sm"
              :disabled="store.page >= totalPages - 1"
              @click="onPageChange(store.page + 1)"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.audit-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.view-title {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  overflow: hidden;
}

.timestamp {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.mono { font-family: monospace; }
.small { font-size: 12px; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
}

.pagination-info {
  font-size: 13px;
  color: #64748b;
}

.pagination-controls {
  display: flex;
  gap: 8px;
}
</style>
