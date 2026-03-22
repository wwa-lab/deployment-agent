<script setup lang="ts">
import { onMounted, computed, ref, reactive } from 'vue'
import { useConfigStore } from '../stores/config'
import { useUserStore } from '../stores/user'
import type { ConfigItem } from '../types'

const store = useConfigStore()
const userStore = useUserStore()

const hasAccess = computed(() => userStore.isDevOpsAdmin)

// Track which row is being edited
const editingKey = ref<string | null>(null)
const editForm = reactive<{ value: string; description: string }>({ value: '', description: '' })
const savingKey = ref<string | null>(null)
const rowError = ref<Record<string, string>>({})
const rowSuccess = ref<Record<string, boolean>>({})

onMounted(() => {
  if (hasAccess.value) {
    store.fetchConfig()
  }
})

function startEdit(item: ConfigItem) {
  editingKey.value = item.key
  editForm.value = item.value
  editForm.description = item.description ?? ''
  rowError.value = { ...rowError.value, [item.key]: '' }
  rowSuccess.value = { ...rowSuccess.value, [item.key]: false }
}

function cancelEdit() {
  editingKey.value = null
}

async function saveEdit(item: ConfigItem) {
  savingKey.value = item.key
  rowError.value = { ...rowError.value, [item.key]: '' }
  try {
    await store.saveConfig({
      key: item.key,
      value: editForm.value,
      description: editForm.description,
    })
    rowSuccess.value = { ...rowSuccess.value, [item.key]: true }
    editingKey.value = null
    setTimeout(() => {
      rowSuccess.value = { ...rowSuccess.value, [item.key]: false }
    }, 2000)
  } catch (e: unknown) {
    rowError.value = {
      ...rowError.value,
      [item.key]: e instanceof Error ? e.message : 'Save failed',
    }
  } finally {
    savingKey.value = null
  }
}

function formatDate(d?: string): string {
  if (!d) return '—'
  try {
    return new Date(d).toLocaleString()
  } catch {
    return d
  }
}
</script>

<template>
  <div class="config-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">Configuration Management</h1>
      </div>
    </div>

    <div v-if="!hasAccess" class="alert alert-error">
      Access denied. This page requires DEVOPS_ADMIN role.
    </div>

    <template v-else>
      <div v-if="store.loading && store.items.length === 0" class="loading-state">
        <span class="spinner"></span>
        <span>Loading configuration...</span>
      </div>

      <div v-else-if="!store.loading && store.items.length === 0" class="empty-state">
        No configuration items found.
      </div>

      <div v-else class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>Key</th>
              <th>Value</th>
              <th>Description</th>
              <th>Updated By</th>
              <th>Updated At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in store.items" :key="item.key">
              <tr>
                <td class="key-cell mono">{{ item.key }}</td>
                <td>
                  <template v-if="editingKey === item.key">
                    <input
                      v-model="editForm.value"
                      class="form-control inline-input"
                      type="text"
                    />
                  </template>
                  <template v-else>{{ item.value }}</template>
                </td>
                <td>
                  <template v-if="editingKey === item.key">
                    <input
                      v-model="editForm.description"
                      class="form-control inline-input"
                      type="text"
                      placeholder="Description..."
                    />
                  </template>
                  <template v-else>{{ item.description ?? '—' }}</template>
                </td>
                <td>{{ item.updatedBy ?? '—' }}</td>
                <td class="timestamp">{{ formatDate(item.updatedAt) }}</td>
                <td>
                  <div class="action-btns">
                    <template v-if="editingKey === item.key">
                      <button
                        class="btn btn-primary btn-sm"
                        :disabled="savingKey === item.key"
                        @click="saveEdit(item)"
                      >
                        <span v-if="savingKey === item.key" class="spinner" style="width:12px;height:12px;border-width:1px;"></span>
                        Save
                      </button>
                      <button class="btn btn-secondary btn-sm" @click="cancelEdit">
                        Cancel
                      </button>
                    </template>
                    <template v-else>
                      <button class="btn btn-secondary btn-sm" @click="startEdit(item)">
                        Edit
                      </button>
                    </template>
                  </div>
                </td>
              </tr>
              <!-- Inline feedback row -->
              <tr v-if="rowError[item.key] || rowSuccess[item.key]">
                <td colspan="6" style="padding:4px 14px 8px">
                  <span v-if="rowError[item.key]" class="feedback-error">{{ rowError[item.key] }}</span>
                  <span v-if="rowSuccess[item.key]" class="feedback-success">Saved successfully.</span>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.config-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.view-title {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  overflow: hidden;
}

.key-cell {
  font-size: 13px;
  color: #2563eb;
  white-space: nowrap;
}

.mono { font-family: monospace; }

.timestamp {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.inline-input {
  padding: 4px 8px;
  min-width: 160px;
}

.action-btns {
  display: flex;
  gap: 6px;
}

.feedback-error {
  font-size: 12px;
  color: #dc2626;
}

.feedback-success {
  font-size: 12px;
  color: #16a34a;
}
</style>
