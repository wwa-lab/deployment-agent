<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { ScopeDirectoryEntry } from '../types'

const props = defineProps<{
  entry?: ScopeDirectoryEntry | null
  mode: 'create' | 'edit'
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [draft: { application: string; snowGroup?: string }]
}>()

const isCreate = computed(() => props.mode === 'create')
const form = reactive({
  application: props.entry?.application ?? '',
  snowGroup: props.entry?.snowGroup ?? '',
})
const localError = ref('')

function submit() {
  localError.value = ''

  if (!form.application.trim()) {
    localError.value = 'Application is required.'
    return
  }

  emit('save', {
    application: form.application.trim(),
    snowGroup: form.snowGroup.trim() || undefined,
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

        <p class="field-hint">
          Upload dialogs use this directory to offer curated Application and SNOW Group choices
          instead of manual typing.
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
  color: #64748b;
  font-weight: 400;
}

.field-hint {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
</style>
