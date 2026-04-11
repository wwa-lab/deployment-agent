<script setup lang="ts">
import { reactive, ref } from 'vue'
import { updateRequestRundown } from '../agents/deployment/api'
import { useUserStore } from '../stores/user'
import type { Request } from '../types'

const props = withDefaults(defineProps<{
  request: Request
  updateRequestRundownFn?: (
    flowId: string,
    requestId: string,
    input: {
      snowGroup?: string
      application?: string
      agent?: string
      owner?: string
      site?: string
      estimatedRemainingMinutes?: number
    }
  ) => Promise<Request>
}>(), {
  updateRequestRundownFn: updateRequestRundown,
})
const emit = defineEmits<{ saved: []; close: [] }>()
const userStore = useUserStore()

const form = reactive({
  snowGroup: props.request.snowGroup ?? '',
  application: props.request.application ?? '',
  agent: props.request.agent ?? '',
  owner: props.request.owner ?? '',
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
    await props.updateRequestRundownFn(props.request.releaseFlowId, props.request.id, {
      snowGroup: form.snowGroup || undefined,
      application: form.application || undefined,
      agent: form.agent || undefined,
      owner: userStore.isDevOpsAdmin ? form.owner || undefined : undefined,
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
          <label class="form-label">Agent</label>
          <input
            v-model="form.agent"
            type="text"
            class="form-control"
            placeholder="e.g. Testing Agent or Deployment Agent"
          />
        </div>

        <div class="form-group">
          <label class="form-label">Rundown Owner</label>
          <input
            v-model="form.owner"
            type="text"
            class="form-control"
            :disabled="!userStore.isDevOpsAdmin"
            placeholder="e.g. alice or emp-001"
          />
          <small class="form-hint">
            {{
              userStore.isDevOpsAdmin
                ? 'DEVOPS_ADMIN controls who can start or fail this rundown.'
                : 'Only DEVOPS_ADMIN can change the rundown owner.'
            }}
          </small>
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
