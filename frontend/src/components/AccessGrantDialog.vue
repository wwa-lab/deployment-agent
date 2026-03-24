<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { AccessGrant, AccessGrantStatus, UserRole } from '../types'

const ROLE_OPTIONS: UserRole[] = ['DEVELOPER', 'TL', 'DEVOPS_ADMIN', 'AUDIT', 'MANAGEMENT']

const props = defineProps<{
  mode: 'create' | 'edit' | 'reactivate'
  grant?: AccessGrant | null
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [payload: {
    employeeId: string
    grantStatus: AccessGrantStatus
    assignedRoles: UserRole[]
    note?: string
  }]
}>()

const form = reactive<{
  employeeId: string
  grantStatus: AccessGrantStatus
  assignedRoles: UserRole[]
  note: string
}>({
  employeeId: '',
  grantStatus: 'ACTIVE',
  assignedRoles: [],
  note: '',
})

const localError = ref('')

const dialogTitle = computed(() => {
  if (props.mode === 'create') return 'Grant Product Access'
  if (props.mode === 'reactivate') return 'Reactivate Access Grant'
  return 'Edit Access Grant'
})

const submitLabel = computed(() => {
  if (props.mode === 'create') return 'Grant Access'
  if (props.mode === 'reactivate') return 'Reactivate'
  return 'Save Changes'
})

const employeeIdDisabled = computed(() => props.mode !== 'create')
const statusDisabled = computed(() => props.mode !== 'create')
const requiresRoles = computed(() => form.grantStatus === 'ACTIVE' || props.mode === 'reactivate')

watch(
  () => [props.mode, props.grant] as const,
  () => {
    form.employeeId = props.grant?.employeeId ?? ''
    form.grantStatus = props.mode === 'reactivate'
      ? 'ACTIVE'
      : props.grant?.grantStatus ?? 'ACTIVE'
    form.assignedRoles = (props.grant?.assignedRoles ?? []).slice() as UserRole[]
    form.note = props.grant?.note ?? ''
    localError.value = ''
  },
  { immediate: true },
)

function toggleRole(role: UserRole) {
  if (form.assignedRoles.includes(role)) {
    form.assignedRoles = form.assignedRoles.filter((value) => value !== role)
  } else {
    form.assignedRoles = [...form.assignedRoles, role]
  }
}

function submit() {
  localError.value = ''

  if (!form.employeeId.trim()) {
    localError.value = 'Employee ID is required.'
    return
  }

  if (requiresRoles.value && form.assignedRoles.length === 0) {
    localError.value = 'At least one role is required for an active access grant.'
    return
  }

  emit('save', {
    employeeId: form.employeeId.trim(),
    grantStatus: props.mode === 'reactivate' ? 'ACTIVE' : form.grantStatus,
    assignedRoles: form.assignedRoles,
    note: form.note.trim() || undefined,
  })
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">{{ dialogTitle }}</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="localError || error" class="alert alert-error">
          {{ localError || error }}
        </div>

        <div class="form-group">
          <label class="form-label">Employee ID <span class="required">*</span></label>
          <input
            v-model="form.employeeId"
            class="form-control"
            type="text"
            placeholder="e.g. emp-006"
            :disabled="employeeIdDisabled"
          />
        </div>

        <div class="form-group">
          <label class="form-label">Grant Status</label>
          <select v-model="form.grantStatus" class="form-control" :disabled="statusDisabled">
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">
            Assigned Roles
            <span v-if="requiresRoles" class="required">*</span>
          </label>
          <div class="role-grid">
            <label v-for="role in ROLE_OPTIONS" :key="role" class="role-option">
              <input
                type="checkbox"
                :checked="form.assignedRoles.includes(role)"
                @change="toggleRole(role)"
              />
              <span>{{ role }}</span>
            </label>
          </div>
          <p class="field-hint">
            Suspended access grants may keep roles assigned, but reactivation requires at least one role.
          </p>
        </div>

        <div class="form-group">
          <label class="form-label">Admin Note</label>
          <textarea
            v-model="form.note"
            class="form-control"
            rows="3"
            placeholder="Optional note for access governance history"
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
          {{ saving ? 'Saving...' : submitLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.required {
  color: #ef4444;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 8px;
}

.role-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.field-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 720px) {
  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
