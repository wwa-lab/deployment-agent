<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { searchAccessGrantDirectory } from '../api/accessGrants'
import type {
  AccessGrant,
  AccessGrantDirectoryCandidate,
  AccessGrantStatus,
  AccessScope,
  UserRole,
} from '../types'

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
    displayName?: string
    grantStatus: AccessGrantStatus
    assignedRoles: UserRole[]
    scopeGrants: AccessScope[]
    note?: string
  }]
}>()

const form = reactive<{
  employeeId: string
  displayName: string
  grantStatus: AccessGrantStatus
  assignedRoles: UserRole[]
  scopeGrants: AccessScope[]
  note: string
}>({
  employeeId: '',
  displayName: '',
  grantStatus: 'ACTIVE',
  assignedRoles: [],
  scopeGrants: [],
  note: '',
})

const localError = ref('')
const directoryQuery = ref('')
const directoryLoading = ref(false)
const directoryError = ref('')
const directoryResults = ref<AccessGrantDirectoryCandidate[]>([])
const directorySearchPerformed = ref(false)
const selectedCandidate = ref<AccessGrantDirectoryCandidate | null>(null)

const dialogTitle = computed(() => {
  if (props.mode === 'create') return 'Add User Access'
  if (props.mode === 'reactivate') return 'Reactivate Access Grant'
  return 'Edit Access Grant'
})

const submitLabel = computed(() => {
  if (props.mode === 'create') return 'Add User'
  if (props.mode === 'reactivate') return 'Reactivate'
  return 'Save Changes'
})

const employeeIdDisabled = computed(() => props.mode !== 'create')
const statusDisabled = computed(() => props.mode !== 'create')
const requiresRoles = computed(() => form.grantStatus === 'ACTIVE' || props.mode === 'reactivate')
const allowsGlobalAccess = computed(() => form.assignedRoles.includes('DEVOPS_ADMIN'))

watch(
  () => [props.mode, props.grant] as const,
  () => {
    form.employeeId = props.grant?.employeeId ?? ''
    form.displayName = props.grant?.displayName ?? ''
    form.grantStatus = props.mode === 'reactivate'
      ? 'ACTIVE'
      : props.grant?.grantStatus ?? 'ACTIVE'
    form.assignedRoles = (props.grant?.assignedRoles ?? []).slice() as UserRole[]
    form.scopeGrants = (props.grant?.scopeGrants ?? []).map((scope) => ({
      application: scope.application,
      snowGroup: scope.snowGroup,
    }))
    form.note = props.grant?.note ?? ''
    directoryQuery.value = ''
    directoryLoading.value = false
    directoryError.value = ''
    directoryResults.value = []
    directorySearchPerformed.value = false
    selectedCandidate.value = null
    localError.value = ''
  },
  { immediate: true },
)

watch(
  () => form.employeeId,
  (employeeId) => {
    const trimmedEmployeeId = employeeId.trim()
    if (!trimmedEmployeeId) {
      selectedCandidate.value = null
      return
    }

    const normalizedEmployeeId = trimmedEmployeeId.toLowerCase()
    const matchedCandidate = directoryResults.value.find(
      (candidate) => candidate.employeeId.trim().toLowerCase() === normalizedEmployeeId,
    )
    if (matchedCandidate) {
      selectedCandidate.value = matchedCandidate
      return
    }

    if (selectedCandidate.value?.employeeId.trim().toLowerCase() !== normalizedEmployeeId) {
      selectedCandidate.value = null
    }
  },
)

function toggleRole(role: UserRole) {
  if (form.assignedRoles.includes(role)) {
    form.assignedRoles = form.assignedRoles.filter((value) => value !== role)
  } else {
    form.assignedRoles = [...form.assignedRoles, role]
  }
}

function addScope() {
  form.scopeGrants = [
    ...form.scopeGrants,
    { application: '', snowGroup: '' },
  ]
}

function removeScope(index: number) {
  form.scopeGrants = form.scopeGrants.filter((_, currentIndex) => currentIndex !== index)
}

async function runDirectorySearch() {
  directoryError.value = ''
  directorySearchPerformed.value = true

  const trimmedQuery = directoryQuery.value.trim()
  if (trimmedQuery.length < 2) {
    directoryResults.value = []
    directoryError.value = 'Enter at least 2 characters to search Team Book.'
    return
  }

  directoryLoading.value = true
  try {
    directoryResults.value = await searchAccessGrantDirectory(trimmedQuery)
  } catch (error: unknown) {
    directoryResults.value = []
    directoryError.value = error instanceof Error ? error.message : 'Failed to search Team Book'
  } finally {
    directoryLoading.value = false
  }
}

function selectCandidateFromDirectory(candidate: AccessGrantDirectoryCandidate) {
  if (candidate.hasAccessGrant) {
    return
  }

  form.employeeId = candidate.employeeId
  form.displayName = candidate.displayName
  selectedCandidate.value = candidate
  localError.value = ''
}

function submit() {
  localError.value = ''

  const normalizedEmployeeId = form.employeeId.trim()
  if (!normalizedEmployeeId) {
    localError.value = 'Employee ID is required.'
    return
  }

  if (props.mode === 'create') {
    const normalizedDisplayName = form.displayName.trim()
    if (!normalizedDisplayName) {
      localError.value = 'Employee Name is required for manual access grant creation.'
      return
    }
  }

  if (requiresRoles.value && form.assignedRoles.length === 0) {
    localError.value = 'At least one role is required for an active access grant.'
    return
  }

  const normalizedScopes = form.scopeGrants
    .map((scope) => ({
      application: scope.application.trim(),
      snowGroup: scope.snowGroup.trim(),
    }))
    .filter((scope) => scope.application.length > 0 || scope.snowGroup.length > 0)

  const hasIncompleteScope = normalizedScopes.some(
    (scope) => scope.application.length === 0 || scope.snowGroup.length === 0,
  )
  if (hasIncompleteScope) {
    localError.value = 'Each scope needs both Application and SNOW Group.'
    return
  }

  if (requiresRoles.value && normalizedScopes.length === 0 && !allowsGlobalAccess.value) {
    localError.value = 'Active non-admin access grants need at least one scope.'
    return
  }

  emit('save', {
    employeeId: normalizedEmployeeId,
    displayName: props.mode === 'create' ? form.displayName.trim() : undefined,
    grantStatus: props.mode === 'reactivate' ? 'ACTIVE' : form.grantStatus,
    assignedRoles: form.assignedRoles,
    scopeGrants: normalizedScopes,
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

        <div v-if="props.mode === 'create'" class="form-group">
          <div class="scopes-header">
            <label class="form-label">Find Team Book Employee</label>
            <button
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="directoryLoading"
              @click="runDirectorySearch"
            >
              {{ directoryLoading ? 'Searching...' : 'Search' }}
            </button>
          </div>
          <input
            v-model="directoryQuery"
            class="form-control"
            type="text"
            placeholder="Search by employee ID or display name"
            @keyup.enter="runDirectorySearch"
          />
          <p class="field-hint">
            Search Team Book to prefill staff ID and name, or skip search and enter `Staff ID + Name`
            manually for self-maintained access records.
          </p>

          <div v-if="directoryError" class="directory-feedback directory-feedback-error">
            {{ directoryError }}
          </div>

          <div v-else-if="directoryLoading" class="directory-feedback">
            <span class="spinner" style="width: 14px; height: 14px; border-width: 2px"></span>
            Searching Team Book...
          </div>

          <div v-else-if="directoryResults.length > 0" class="directory-results">
            <button
              v-for="candidate in directoryResults"
              :key="candidate.employeeId"
              class="directory-option"
              :class="candidate.hasAccessGrant ? 'directory-option-disabled' : 'directory-option-ready'"
              type="button"
              :disabled="candidate.hasAccessGrant"
              @click="selectCandidateFromDirectory(candidate)"
            >
              <span class="directory-option-main">
                <span class="directory-option-name">{{ candidate.displayName }}</span>
                <span class="directory-option-id mono">{{ candidate.employeeId }}</span>
              </span>
              <span
                class="directory-chip"
                :class="candidate.hasAccessGrant ? 'directory-chip-muted' : 'directory-chip-ready'"
              >
                {{ candidate.hasAccessGrant ? `Existing ${candidate.grantStatus ?? 'grant'}` : 'Ready to add' }}
              </span>
            </button>
          </div>

          <div v-else-if="directorySearchPerformed" class="directory-feedback">
            No Team Book matches found for that search.
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Employee ID <span class="required">*</span></label>
          <input
            v-model="form.employeeId"
            class="form-control"
            type="text"
            :placeholder="props.mode === 'create' ? 'e.g. 43910156 or emp-006' : 'e.g. emp-006'"
            :disabled="employeeIdDisabled"
          />
          <p class="field-hint">
            This dialog creates an Atlas Hub access grant by Staff ID. Team Book search is optional in create mode.
          </p>
          <div v-if="selectedCandidate" class="directory-selected">
            Selected employee: <strong>{{ selectedCandidate.displayName }}</strong>
          </div>
        </div>

        <div v-if="props.mode === 'create'" class="form-group">
          <label class="form-label">Employee Name <span class="required">*</span></label>
          <input
            v-model="form.displayName"
            class="form-control"
            type="text"
            placeholder="e.g. Leo L Zhang"
          />
          <p class="field-hint">
            If Team Book is unavailable, enter the display name manually for access records.
          </p>
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
          <div class="scopes-header">
            <label class="form-label">Scoped Visibility</label>
            <button class="btn btn-secondary btn-sm" type="button" @click="addScope">Add Scope</button>
          </div>
          <p class="field-hint">
            Use `Application + SNOW Group` to limit what this employee can see and manage. Leave
            scopes empty only for global `DEVOPS_ADMIN`.
          </p>

          <div v-if="form.scopeGrants.length === 0" class="empty-scope-state">
            No scopes configured yet.
          </div>

          <div v-else class="scope-grid">
            <div v-for="(scope, index) in form.scopeGrants" :key="`${index}-${scope.application}-${scope.snowGroup}`" class="scope-row">
              <input
                v-model="scope.application"
                class="form-control"
                type="text"
                placeholder="Application"
              />
              <input
                v-model="scope.snowGroup"
                class="form-control"
                type="text"
                placeholder="SNOW Group"
              />
              <button class="btn btn-secondary btn-sm" type="button" @click="removeScope(index)">
                Remove
              </button>
            </div>
          </div>
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
  background: var(--color-surface-secondary);
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.field-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.directory-feedback {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.directory-feedback-error {
  color: #b91c1c;
}

.directory-results {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.directory-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
  text-align: left;
}

.directory-option-ready:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.directory-option-disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.directory-option-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.directory-option-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.directory-option-id {
  font-size: 12px;
  color: var(--color-text-muted);
}

.directory-chip {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.directory-chip-ready {
  background: #dcfce7;
  color: #166534;
}

.directory-chip-muted {
  background: #e2e8f0;
  color: #475569;
}

.directory-selected {
  margin-top: 8px;
  font-size: 13px;
  color: #1d4ed8;
}

.scopes-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.empty-scope-state {
  margin-top: 10px;
  padding: 12px;
  border-radius: 10px;
  background: var(--color-surface-secondary);
  color: var(--color-text-muted);
  font-size: 13px;
}

.scope-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.scope-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

@media (max-width: 720px) {
  .role-grid {
    grid-template-columns: 1fr;
  }

  .directory-option {
    flex-direction: column;
    align-items: flex-start;
  }

  .scope-row {
    grid-template-columns: 1fr;
  }

  .scopes-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
