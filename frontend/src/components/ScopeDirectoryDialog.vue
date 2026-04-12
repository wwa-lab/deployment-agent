<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { agentRegistry } from '../config/agentRegistry'
import type { ScopeDirectoryEntry } from '../types'

const props = defineProps<{
  entry?: ScopeDirectoryEntry | null
  mode: 'create' | 'edit'
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [draft: { application: string; snowGroup?: string; agent?: string }]
}>()

const isCreate = computed(() => props.mode === 'create')
const agentOptions = computed(() => {
  const baseOptions = agentRegistry
    .filter((agent) => agent.enabled)
    .map((agent) => ({ value: agent.key, label: agent.name }))

  if (props.entry?.agent && !baseOptions.some((option) => option.value === props.entry?.agent)) {
    return [...baseOptions, { value: props.entry.agent, label: props.entry.agent }]
  }

  return baseOptions
})
const form = reactive({
  application: props.entry?.application ?? '',
  snowGroup: props.entry?.snowGroup ?? '',
  agent: props.entry?.agent ?? '',
})
const localError = ref('')

function submit() {
  localError.value = ''

  if (!form.application.trim()) {
    localError.value = 'Application is required.'
    return
  }

  if (form.agent.trim() && !form.snowGroup.trim()) {
    localError.value = 'Agent scope requires both Application and SNOW Group.'
    return
  }

  emit('save', {
    application: form.application.trim(),
    snowGroup: form.snowGroup.trim() || undefined,
    agent: form.agent.trim() || undefined,
  })
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">
          {{ isCreate ? 'Add Scope Directory Entry' : 'Edit Scope Directory Entry' }}
        </span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="localError || error" class="alert alert-error">
          {{ localError || error }}
        </div>

        <div class="form-group">
          <label class="form-label">Application <span class="required">*</span></label>
          <input
            v-model="form.application"
            class="form-control"
            type="text"
            placeholder="e.g. AMH HCC"
          />
        </div>

        <div class="form-group">
          <label class="form-label">SNOW Group <span class="optional">(Optional)</span></label>
          <input
            v-model="form.snowGroup"
            class="form-control"
            type="text"
            placeholder="e.g. HTSA-CSI-HCC-AMH-PRJ"
          />
          <p class="field-hint">
            Keep this blank if you only want to maintain an application-level choice for uploads.
          </p>
        </div>

        <div class="form-group">
          <label class="form-label">Agent <span class="optional">(Optional)</span></label>
          <select v-model="form.agent" class="form-control">
            <option value="">All Agents</option>
            <option v-for="option in agentOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <p class="field-hint">
            Optional agent-specific override. Agent scope requires both Application and SNOW Group.
          </p>
        </div>

        <p class="field-hint">
          Upload dialogs use this directory to offer curated Application, SNOW Group, and Agent
          choices instead of manual typing.
        </p>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submit">
          {{ saving ? 'Saving...' : isCreate ? 'Add Entry' : 'Save Changes' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.required {
  color: #ef4444;
}

.optional {
  color: var(--color-text-muted);
  font-weight: 400;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}
</style>
