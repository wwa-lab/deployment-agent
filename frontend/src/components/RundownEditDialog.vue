<script setup lang="ts">
import { reactive, ref } from 'vue'
import { updateRequestRundown } from '../api/releaseFlows'
import type { Request } from '../types'

const props = defineProps<{ request: Request }>()
const emit = defineEmits<{ saved: []; close: [] }>()

const form = reactive({
  snowGroup: props.request.snowGroup ?? '',
  application: props.request.application ?? '',
  site: props.request.site ?? '',
  estimatedRemainingMinutes:
    props.request.estimatedRemainingMinutes !== undefined &&
    props.request.estimatedRemainingMinutes !== null
      ? String(props.request.estimatedRemainingMinutes)
      : '',
})

const saving = ref(false)
const error = ref('')

async function submit() {
  saving.value = true
  error.value = ''

  const estimated = form.estimatedRemainingMinutes.trim()
  if (estimated && Number.isNaN(Number(estimated))) {
    error.value = 'Estimated remaining time must be a number.'
    saving.value = false
    return
  }

  try {
    await updateRequestRundown(props.request.releaseFlowId, props.request.id, {
      snowGroup: form.snowGroup || undefined,
      application: form.application || undefined,
      site: form.site || undefined,
      estimatedRemainingMinutes: estimated ? Number(estimated) : undefined,
    })
    emit('saved')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to save rundown information'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Edit Rundown Information — {{ request.stage }}</span>
        <button class="modal-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="form-group">
          <label class="form-label">SNOW Group</label>
          <input v-model="form.snowGroup" type="text" class="form-control" placeholder="e.g. HTSA-CSI-HCC-AMH-PRJ" />
        </div>

        <div class="form-group">
          <label class="form-label">Application</label>
          <input v-model="form.application" type="text" class="form-control" placeholder="e.g. AMH HCC" />
        </div>

        <div class="form-group">
          <label class="form-label">Site</label>
          <input v-model="form.site" type="text" class="form-control" placeholder="e.g. HK" />
        </div>

        <div class="form-group">
          <label class="form-label">Estimated Remaining Time (minutes)</label>
          <input
            v-model="form.estimatedRemainingMinutes"
            type="text"
            class="form-control"
            placeholder="e.g. 120"
          />
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <span v-if="saving" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ saving ? 'Saving...' : 'Save Rundown' }}
        </button>
      </div>
    </div>
  </div>
</template>
