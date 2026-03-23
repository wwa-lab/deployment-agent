<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TemplateRecord } from '../types'

const props = defineProps<{ template: TemplateRecord }>()
const emit = defineEmits<{ close: []; confirm: [] }>()

const submitting = ref(false)

const impactSummary = computed(() => {
  const taskCount = props.template.tasks.length
  return taskCount === 1 ? '1 task definition' : `${taskCount} task definitions`
})

async function confirmDelete() {
  submitting.value = true
  try {
    emit('confirm')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Delete Template</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="alert alert-error">
          You are about to remove <strong>{{ template.name }}</strong> from the local template list.
        </div>

        <div class="impact-list">
          <div><span class="impact-label">Template:</span> {{ template.name }}</div>
          <div><span class="impact-label">Version:</span> {{ template.version }}</div>
          <div><span class="impact-label">Scope:</span> {{ template.agent }} / {{ template.application }} / {{ template.site }}</div>
          <div><span class="impact-label">Included:</span> {{ impactSummary }}</div>
        </div>

        <p class="impact-note">
          This only removes the current frontend draft or mock template entry. It does not call a backend delete API.
        </p>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button class="btn btn-danger" type="button" :disabled="submitting" @click="confirmDelete">
          {{ submitting ? 'Deleting...' : 'Delete Template' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.impact-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  color: #334155;
}

.impact-label {
  font-weight: 700;
  color: #475569;
}

.impact-note {
  margin: 16px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.btn-danger {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
  border-color: #b91c1c;
}
</style>
