<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { ConfigComponentDraft, ConfigComponentRow } from '../types'

const props = defineProps<{
  component?: ConfigComponentRow | null
  mode: 'create' | 'edit'
  componentOptions: Array<{
    componentId: ConfigComponentDraft['componentId']
    label: string
    area: string
    trackServiceUser: boolean
    trackCredential: boolean
    defaultDescription: string
  }>
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [draft: ConfigComponentDraft]
}>()

const isCreate = computed(() => props.mode === 'create')
const defaultOption = computed(() => props.componentOptions[0])

const form = reactive({
  componentId: props.component?.componentId ?? defaultOption.value.componentId,
  displayName: props.component?.label ?? defaultOption.value.label,
  area: props.component?.category ?? defaultOption.value.area,
  application: props.component?.application ?? '',
  snowGroup: props.component?.owningGroup ?? '',
  agent: props.component?.agent ?? '',
  endpoint: props.component?.endpoint ?? '',
  serviceUser: props.component?.serviceUser ?? '',
  credentialValue: '',
  description: props.component?.description ?? defaultOption.value.defaultDescription,
})

const localError = ref('')

const selectedOption = computed(() => {
  return props.componentOptions.find((option) => option.componentId === form.componentId) ?? defaultOption.value
})

const requiresUser = computed(() => selectedOption.value.trackServiceUser)
const requiresSecret = computed(() => selectedOption.value.trackCredential)
const credentialConfigured = computed(() => props.component?.credentialConfigured ?? false)
const endpointLabel = computed(() =>
  form.componentId === 'callback' ? 'Callback Endpoint' : 'Service Endpoint',
)
const scopeSourcePreview = computed(() => {
  if (form.agent.trim()) return 'Agent Override'
  if (form.snowGroup.trim()) return 'SNOW Group Default'
  if (form.application.trim()) return 'Application Default'
  return 'Platform Default'
})

watch(
  () => form.componentId,
  (next, previous) => {
    const previousOption = props.componentOptions.find((option) => option.componentId === previous)
    const nextOption = props.componentOptions.find((option) => option.componentId === next)
    if (!nextOption) return

    if (isCreate.value) {
      if (!form.displayName.trim() || form.displayName === previousOption?.label) {
        form.displayName = nextOption.label
      }
      if (!form.area.trim() || form.area === previousOption?.area) {
        form.area = nextOption.area
      }
      if (!form.description.trim() || form.description === previousOption?.defaultDescription) {
        form.description = nextOption.defaultDescription
      }
    }

    if (!nextOption.trackServiceUser) {
      form.serviceUser = ''
    }
  },
)

function submit() {
  localError.value = ''

  if (isCreate.value && !form.componentId) {
    localError.value = 'Component type is required.'
    return
  }

  if (!form.displayName.trim()) {
    localError.value = 'Component name is required.'
    return
  }

  if (!form.area.trim()) {
    localError.value = 'Area is required.'
    return
  }

  if (form.snowGroup.trim() && !form.application.trim()) {
    localError.value = 'SNOW Group scope requires Application.'
    return
  }

  if (form.agent.trim() && (!form.application.trim() || !form.snowGroup.trim())) {
    localError.value = 'Agent scope requires both Application and SNOW Group.'
    return
  }

  if (!form.endpoint.trim()) {
    localError.value = `${endpointLabel.value} is required.`
    return
  }

  if (requiresUser.value && !form.serviceUser.trim()) {
    localError.value = 'Service user is required.'
    return
  }

  if (requiresSecret.value && !credentialConfigured.value && !form.credentialValue.trim()) {
    localError.value = 'Credential is required.'
    return
  }

  emit('save', {
    componentId: form.componentId,
    displayName: form.displayName.trim(),
    area: form.area.trim(),
    application: form.application.trim() || undefined,
    snowGroup: form.snowGroup.trim() || undefined,
    agent: form.agent.trim() || undefined,
    endpoint: form.endpoint.trim(),
    serviceUser: requiresUser.value ? form.serviceUser.trim() : undefined,
    credentialValue:
      requiresSecret.value && form.credentialValue.trim().length > 0
        ? form.credentialValue.trim()
        : undefined,
    description: form.description.trim() || undefined,
  })
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">
          {{ isCreate ? 'Add Scoped Component Configuration' : `Edit Component Configuration — ${component?.label}` }}
        </span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="localError || error" class="alert alert-error">
          {{ localError || error }}
        </div>

        <div class="component-summary">
          <div class="summary-item">
            <span class="summary-label">{{ isCreate ? 'Component Type' : 'Component' }}</span>
            <span class="summary-value">{{ selectedOption.label }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Area</span>
            <span class="summary-value">{{ form.area || selectedOption.area }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Scope Source</span>
            <span class="summary-value">{{ scopeSourcePreview }}</span>
          </div>
        </div>

        <div v-if="isCreate" class="form-group">
          <label class="form-label">Component Type <span class="required">*</span></label>
          <select v-model="form.componentId" class="form-control">
            <option
              v-for="option in componentOptions"
              :key="option.componentId"
              :value="option.componentId"
            >
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">Component Name <span class="required">*</span></label>
          <input
            v-model="form.displayName"
            class="form-control"
            type="text"
            placeholder="Enter component name"
          />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Area <span class="required">*</span></label>
            <input
              v-model="form.area"
              class="form-control"
              type="text"
              placeholder="CI/CD"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Application</label>
            <input
              v-model="form.application"
              class="form-control"
              type="text"
              placeholder="Leave blank for platform default"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">SNOW Group</label>
            <input
              v-model="form.snowGroup"
              class="form-control"
              type="text"
              placeholder="Requires Application when set"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Agent</label>
            <input
              v-model="form.agent"
              class="form-control"
              type="text"
              placeholder="Requires Application and SNOW Group when set"
            />
          </div>
        </div>

        <p class="field-hint">
          Resolution order uses the most specific matching row first: Agent Override, then SNOW
          Group Default, then Application Default, then Platform Default.
        </p>

        <div class="form-group">
          <label class="form-label">{{ endpointLabel }} <span class="required">*</span></label>
          <input
            v-model="form.endpoint"
            class="form-control"
            type="text"
            :placeholder="form.componentId === 'callback' ? 'https://callback.example.com/status' : 'https://service.example.com'"
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
            v-model="form.credentialValue"
            class="form-control"
            type="password"
            :placeholder="credentialConfigured ? 'Enter a new token or password to replace the current one' : 'Enter token or password'"
          />
          <p class="field-hint">
            {{
              credentialConfigured
                ? 'Leave blank to keep the current stored credential.'
                : 'This credential will be encrypted before it is stored.'
            }}
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
          {{ saving ? 'Saving...' : isCreate ? 'Create Component' : 'Save Component' }}
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

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 720px) {
  .component-summary {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
