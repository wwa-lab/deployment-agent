<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { ConfigComponentDraft, ConfigComponentRow } from '../types'

const props = defineProps<{
  component: ConfigComponentRow
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [draft: ConfigComponentDraft]
}>()

const form = reactive({
  endpoint: props.component.endpoint,
  serviceUser: props.component.serviceUser ?? '',
  secretValue: props.component.secretValue ?? '',
  description: props.component.description ?? '',
})

const localError = ref('')

const requiresUser = computed(() => Boolean(props.component.userKey))
const requiresSecret = computed(() => Boolean(props.component.secretKey))
const endpointLabel = computed(() =>
  props.component.id === 'callback' ? 'Callback Endpoint' : 'Service Endpoint',
)

function submit() {
  localError.value = ''

  if (!form.endpoint.trim()) {
    localError.value = `${endpointLabel.value} is required.`
    return
  }

  if (requiresUser.value && !form.serviceUser.trim()) {
    localError.value = 'Service user is required.'
    return
  }

  if (requiresSecret.value && !form.secretValue.trim()) {
    localError.value = 'Credential is required.'
    return
  }

  emit('save', {
    endpoint: form.endpoint.trim(),
    serviceUser: requiresUser.value ? form.serviceUser.trim() : undefined,
    secretValue: requiresSecret.value ? form.secretValue.trim() : undefined,
    description: form.description.trim() || undefined,
  })
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Edit Component Configuration — {{ component.label }}</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="localError || error" class="alert alert-error">
          {{ localError || error }}
        </div>

        <div class="component-summary">
          <div class="summary-item">
            <span class="summary-label">Component</span>
            <span class="summary-value">{{ component.label }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Area</span>
            <span class="summary-value">{{ component.category }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Status</span>
            <span class="summary-value">{{ component.status }}</span>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">{{ endpointLabel }} <span class="required">*</span></label>
          <input
            v-model="form.endpoint"
            class="form-control"
            type="text"
            :placeholder="component.id === 'callback' ? 'https://callback.example.com/status' : 'https://service.example.com'"
          />
        </div>

        <div v-if="requiresUser" class="form-group">
          <label class="form-label">Service User <span class="required">*</span></label>
          <input
            v-model="form.serviceUser"
            class="form-control"
            type="text"
            placeholder="Enter service user"
          />
        </div>

        <div v-if="requiresSecret" class="form-group">
          <label class="form-label">Credential <span class="required">*</span></label>
          <input
            v-model="form.secretValue"
            class="form-control"
            type="password"
            placeholder="Enter token or password"
          />
          <p class="field-hint">
            Leave the current value in place if you are only updating the endpoint or service user.
          </p>
        </div>

        <div class="form-group">
          <label class="form-label">Description</label>
          <textarea
            v-model="form.description"
            class="form-control"
            rows="3"
            placeholder="Describe how this integration is used"
          />
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submit">
          <span
            v-if="saving"
            class="spinner"
            style="width: 14px; height: 14px; border-width: 2px"
          ></span>
          {{ saving ? 'Saving...' : 'Save Component' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.component-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.summary-item {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.summary-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #64748b;
  margin-bottom: 6px;
}

.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 720px) {
  .component-summary {
    grid-template-columns: 1fr;
  }
}
</style>
