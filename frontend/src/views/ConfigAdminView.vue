<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ConfigComponentDialog from '../components/ConfigComponentDialog.vue'
import ScopeDirectoryDialog from '../components/ScopeDirectoryDialog.vue'
import { useConfigStore } from '../stores/config'
import { useScopeDirectoryStore } from '../stores/scopeDirectory'
import { useUserStore } from '../stores/user'
import type {
  ConfigComponentDraft,
  ConfigComponentRow,
  ConfigIntegrationId,
  ConfigKey,
  ScopeDirectoryEntry,
} from '../types'

type ScopeSource = 'Platform Default' | 'Application Default' | 'SNOW Group Default' | 'Agent Override'

type ConfigCatalogRow = {
  rowId: string
  componentInstanceId?: string
  componentId: ConfigIntegrationId
  key: ConfigKey
  label: string
  application: string
  owningGroup: string
  agent: string
  scopeSource: ScopeSource
  integration: string
  area: string
  value: string
  configured: boolean
  sensitive: boolean
  description?: string
  updatedBy?: string
  updatedAt?: string
}

const COMPONENT_DEFINITIONS: Array<{
  id: ConfigIntegrationId
  label: string
  category: string
  endpointKey: ConfigKey
  userKey?: ConfigKey
  secretKey?: ConfigKey
  defaultDescription: string
}> = [
  {
    id: 'jenkins',
    label: 'Jenkins Pipeline',
    category: 'CI/CD',
    endpointKey: 'jenkins_url',
    userKey: 'jenkins_user',
    secretKey: 'jenkins_api_token',
    defaultDescription: 'Configuration used for Jenkins-triggered workflow jobs.',
  },
  {
    id: 'ansible',
    label: 'Ansible Automation',
    category: 'Execution',
    endpointKey: 'ansible_url',
    userKey: 'ansible_user',
    secretKey: 'ansible_api_token',
    defaultDescription: 'Configuration used for Ansible execution and result collection.',
  },
  {
    id: 'callback',
    label: 'Execution Callback',
    category: 'Integration',
    endpointKey: 'execution_callback_endpoint',
    defaultDescription: 'HTTPS callback endpoint used by external tools to post execution updates.',
  },
]

const SECRET_KEYS: ConfigKey[] = ['jenkins_api_token', 'ansible_api_token']

const store = useConfigStore()
const scopeDirectoryStore = useScopeDirectoryStore()
const userStore = useUserStore()

const canEdit = computed(() => userStore.isDevOpsAdmin)
const refreshing = computed(() => store.loading || scopeDirectoryStore.loading)
const componentDefinitionById = computed(
  () => new Map(COMPONENT_DEFINITIONS.map((definition) => [definition.id, definition])),
)

const activeView = ref<'component' | 'scope' | 'raw'>('component')
const searchTerm = ref('')
const statusFilter = ref<'All' | ConfigComponentRow['status']>('All')
const componentScopeFilters = reactive({
  application: 'All',
  owningGroup: 'All',
  agent: 'All',
})
const scopeSearch = ref('')
const scopeApplicationFilter = ref('All')
const filterForm = reactive({
  owningGroup: 'All',
  application: 'All',
  agent: 'All',
  configItem: 'All',
})
const appliedFilters = reactive({
  owningGroup: 'All',
  application: 'All',
  agent: 'All',
  configItem: 'All',
})

const editingComponent = ref<ConfigComponentRow | null>(null)
const creatingComponent = ref(false)
const componentSaving = ref(false)
const deletingComponentId = ref<string | null>(null)
const componentError = ref('')
const editingScopeEntry = ref<ScopeDirectoryEntry | null>(null)
const creatingScopeEntry = ref(false)
const scopeSaving = ref(false)
const deletingScopeEntryId = ref<string | null>(null)
const scopeError = ref('')

const editingRowId = ref<string | null>(null)
const editForm = reactive<{ value: string; description: string }>({ value: '', description: '' })
const savingRowId = ref<string | null>(null)
const rowError = ref<Record<string, string>>({})
const rowSuccess = ref<Record<string, boolean>>({})

onMounted(() => {
  void Promise.all([store.fetchConfig(), scopeDirectoryStore.fetchEntries()]).catch(() => undefined)
})

const componentRows = computed<ConfigComponentRow[]>(() => {
  return store.components.map((component) => {
    const definition = componentDefinitionById.value.get(component.componentId)
    const requiredCount = 1 + (component.trackServiceUser ? 1 : 0) + (component.trackCredential ? 1 : 0)
    const configuredRequiredCount =
      (component.serviceEndpoint?.trim() ? 1 : 0) +
      (component.trackServiceUser && component.serviceUser?.trim() ? 1 : 0) +
      (component.trackCredential && component.credentialConfigured ? 1 : 0)

    let status: ConfigComponentRow['status'] = 'Needs Setup'
    if (configuredRequiredCount === requiredCount && requiredCount > 0) {
      status = 'Ready'
    } else if (configuredRequiredCount > 0) {
      status = 'Partial'
    }

    return {
      id: component.componentInstanceId,
      componentId: component.componentId,
      label: component.displayName,
      category: component.area,
      application: component.application ?? '',
      owningGroup: component.snowGroup ?? '',
      agent: component.agent ?? '',
      scopeSource: component.scopeSource,
      endpointKey: definition?.endpointKey,
      userKey: definition?.userKey,
      secretKey: definition?.secretKey,
      trackServiceUser: component.trackServiceUser,
      trackCredential: component.trackCredential,
      endpoint: component.serviceEndpoint ?? '',
      serviceUser: component.serviceUser ?? '',
      credentialConfigured: component.credentialConfigured,
      secretState: component.trackCredential
        ? component.credentialConfigured
          ? 'Configured'
          : 'Missing'
        : 'Not required',
      description: component.description ?? definition?.defaultDescription,
      updatedBy: component.updatedBy,
      updatedAt: component.updatedAt,
      status,
    }
  })
})

const componentOwningGroupOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.owningGroup).filter(Boolean)),
])
const componentApplicationOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.application).filter(Boolean)),
])
const componentAgentOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.agent).filter(Boolean)),
])

const scopeApplicationOptions = computed(() => [
  'All',
  ...new Set(scopeDirectoryStore.entries.map((entry) => entry.application).filter(Boolean)),
])

const filteredScopeEntries = computed(() => {
  const query = scopeSearch.value.trim().toLowerCase()

  return scopeDirectoryStore.entries.filter((entry) => {
    const matchesApplication =
      scopeApplicationFilter.value === 'All' || entry.application === scopeApplicationFilter.value
    const matchesSearch =
      query.length === 0 ||
      entry.application.toLowerCase().includes(query) ||
      (entry.snowGroup ?? '').toLowerCase().includes(query)

    return matchesApplication && matchesSearch
  })
})

const filteredComponentRows = computed(() => {
  const query = searchTerm.value.trim().toLowerCase()

  return componentRows.value.filter((row) => {
    const matchesSearch =
      query.length === 0 ||
      row.label.toLowerCase().includes(query) ||
      row.category.toLowerCase().includes(query) ||
      row.endpoint.toLowerCase().includes(query) ||
      (row.serviceUser ?? '').toLowerCase().includes(query) ||
      (row.description ?? '').toLowerCase().includes(query)

    const matchesStatus = statusFilter.value === 'All' || row.status === statusFilter.value
    const matchesApplication =
      componentScopeFilters.application === 'All' || row.application === componentScopeFilters.application
    const matchesOwningGroup =
      componentScopeFilters.owningGroup === 'All' || row.owningGroup === componentScopeFilters.owningGroup
    const matchesAgent = componentScopeFilters.agent === 'All' || row.agent === componentScopeFilters.agent
    return matchesSearch && matchesStatus && matchesApplication && matchesOwningGroup && matchesAgent
  })
})

const rawRows = computed<ConfigCatalogRow[]>(() => {
  return store.items.map((item) => {
    const definition = item.componentId ? componentDefinitionById.value.get(item.componentId) : undefined

    return {
      rowId: `${item.componentInstanceId ?? 'legacy'}:${item.key}`,
      componentInstanceId: item.componentInstanceId,
      componentId: item.componentId ?? definition?.id ?? 'jenkins',
      key: item.key,
      label: item.key.toUpperCase() as Uppercase<ConfigKey>,
      application: item.application ?? '',
      owningGroup: item.snowGroup ?? '',
      agent: item.agent ?? '',
      scopeSource: (item.scopeSource as ScopeSource | undefined) ?? 'Platform Default',
      integration: item.integration ?? definition?.label ?? '',
      area: item.area ?? definition?.category ?? '',
      value: item.value ?? '',
      configured: item.configured ?? Boolean(item.value),
      sensitive: item.sensitive ?? SECRET_KEYS.includes(item.key),
      description: item.description,
      updatedBy: item.updatedBy,
      updatedAt: item.updatedAt,
    }
  })
})

const owningGroupOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.owningGroup).filter(Boolean))])
const applicationOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.application).filter(Boolean))])
const agentOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.agent).filter(Boolean))])
const configItemOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.label))])

const filteredRawRows = computed(() => {
  return rawRows.value.filter((row) => {
    const matchesOwningGroup =
      appliedFilters.owningGroup === 'All' || row.owningGroup === appliedFilters.owningGroup
    const matchesApplication =
      appliedFilters.application === 'All' || row.application === appliedFilters.application
    const matchesAgent = appliedFilters.agent === 'All' || row.agent === appliedFilters.agent
    const matchesConfigItem =
      appliedFilters.configItem === 'All' || row.label === appliedFilters.configItem

    return matchesOwningGroup && matchesApplication && matchesAgent && matchesConfigItem
  })
})

const componentDialogOptions = computed(() =>
  COMPONENT_DEFINITIONS.map((definition) => ({
    componentId: definition.id,
    label: definition.label,
    area: definition.category,
    trackServiceUser: Boolean(definition.userKey),
    trackCredential: Boolean(definition.secretKey),
    defaultDescription: definition.defaultDescription,
  })),
)

async function refreshConfig() {
  await Promise.all([store.fetchConfig(), scopeDirectoryStore.fetchEntries(true)])
}

function applyRawFilters() {
  appliedFilters.owningGroup = filterForm.owningGroup
  appliedFilters.application = filterForm.application
  appliedFilters.agent = filterForm.agent
  appliedFilters.configItem = filterForm.configItem
}

function resetRawFilters() {
  filterForm.owningGroup = 'All'
  filterForm.application = 'All'
  filterForm.agent = 'All'
  filterForm.configItem = 'All'
  applyRawFilters()
}

function selectView(view: 'component' | 'scope' | 'raw') {
  activeView.value = view
}

function openCreateComponent() {
  if (!canEdit.value) return
  creatingComponent.value = true
  editingComponent.value = null
  componentError.value = ''
}

function openComponentEditor(component: ConfigComponentRow) {
  if (!canEdit.value) return
  creatingComponent.value = false
  editingComponent.value = component
  componentError.value = ''
}

function closeComponentEditor() {
  editingComponent.value = null
  creatingComponent.value = false
  componentSaving.value = false
  componentError.value = ''
}

function openCreateScopeEntry() {
  if (!canEdit.value) return
  creatingScopeEntry.value = true
  editingScopeEntry.value = null
  scopeError.value = ''
}

function openScopeEditor(entry: ScopeDirectoryEntry) {
  if (!canEdit.value) return
  creatingScopeEntry.value = false
  editingScopeEntry.value = entry
  scopeError.value = ''
}

function closeScopeEditor() {
  creatingScopeEntry.value = false
  editingScopeEntry.value = null
  scopeSaving.value = false
  scopeError.value = ''
}

async function saveScopeEntry(draft: { application: string; snowGroup?: string }) {
  if (!canEdit.value) return

  scopeSaving.value = true
  scopeError.value = ''

  try {
    await scopeDirectoryStore.saveEntry({
      id: creatingScopeEntry.value ? undefined : editingScopeEntry.value?.id,
      application: draft.application,
      snowGroup: draft.snowGroup,
    })
    closeScopeEditor()
  } catch (error: unknown) {
    scopeError.value =
      error instanceof Error ? error.message : 'Failed to save scope directory entry'
  } finally {
    scopeSaving.value = false
  }
}

async function deleteScopeEntry(entry: ScopeDirectoryEntry) {
  if (!canEdit.value) return

  const confirmed = window.confirm(
    `Delete scope entry "${entry.application}${entry.snowGroup ? ` / ${entry.snowGroup}` : ''}"?`,
  )
  if (!confirmed) return

  deletingScopeEntryId.value = entry.id
  scopeError.value = ''

  try {
    await scopeDirectoryStore.removeEntry(entry.id)
    if (editingScopeEntry.value?.id === entry.id) {
      closeScopeEditor()
    }
  } catch (error: unknown) {
    scopeError.value =
      error instanceof Error ? error.message : 'Failed to delete scope directory entry'
  } finally {
    deletingScopeEntryId.value = null
  }
}

async function saveComponent(draft: ConfigComponentDraft) {
  if (!canEdit.value) return

  componentSaving.value = true
  componentError.value = ''

  try {
    await store.saveComponent({
      componentInstanceId: creatingComponent.value ? undefined : editingComponent.value?.id,
      componentId: draft.componentId,
      displayName: draft.displayName,
      area: draft.area,
      application: draft.application,
      snowGroup: draft.snowGroup,
      agent: draft.agent,
      serviceEndpoint: draft.endpoint,
      serviceUser: draft.serviceUser,
      credentialValue: draft.credentialValue,
      description: draft.description,
    })
    closeComponentEditor()
  } catch (error: unknown) {
    componentError.value =
      error instanceof Error ? error.message : 'Failed to save component configuration'
  } finally {
    componentSaving.value = false
  }
}

function describeComponentScope(component: ConfigComponentRow) {
  if (component.agent?.trim()) {
    return `Agent Override (${component.application} / ${component.owningGroup} / ${component.agent})`
  }
  if (component.owningGroup?.trim()) {
    return `SNOW Group Default (${component.application} / ${component.owningGroup})`
  }
  if (component.application?.trim()) {
    return `Application Default (${component.application})`
  }
  return 'Platform Default'
}

function deleteTitle(component: ConfigComponentRow) {
  if (!canEdit.value) {
    return 'DEVOPS_ADMIN can delete configuration.'
  }
  if (!component.id) {
    return 'This built-in platform default has not been saved yet, so there is nothing to delete.'
  }
  return ''
}

async function deleteComponent(component: ConfigComponentRow) {
  if (!canEdit.value || !component.id) return

  const confirmed = window.confirm(
    `Delete component "${component.label}" for ${describeComponentScope(component)}?`,
  )
  if (!confirmed) return

  deletingComponentId.value = component.id
  componentError.value = ''

  try {
    await store.removeComponent(component.id)
    if (editingComponent.value?.id === component.id) {
      closeComponentEditor()
    }
  } catch (error: unknown) {
    componentError.value =
      error instanceof Error ? error.message : 'Failed to delete component configuration'
  } finally {
    deletingComponentId.value = null
  }
}

function startEdit(row: ConfigCatalogRow) {
  editingRowId.value = row.rowId
  editForm.value = row.sensitive ? '' : row.value
  editForm.description = row.description ?? ''
  rowError.value = { ...rowError.value, [row.rowId]: '' }
  rowSuccess.value = { ...rowSuccess.value, [row.rowId]: false }
}

function cancelEdit() {
  editingRowId.value = null
}

async function saveEdit(row: ConfigCatalogRow) {
  if (!canEdit.value) return
  savingRowId.value = row.rowId
  rowError.value = { ...rowError.value, [row.rowId]: '' }

  if (!editForm.value.trim()) {
    rowError.value = {
      ...rowError.value,
      [row.rowId]: row.sensitive ? 'Enter a new credential value.' : 'Value must not be blank.',
    }
    savingRowId.value = null
    return
  }

  try {
    await store.saveConfig({
      componentInstanceId: row.componentInstanceId,
      componentId: row.componentId,
      key: row.key,
      value: editForm.value,
      description: editForm.description,
    })
    rowSuccess.value = { ...rowSuccess.value, [row.rowId]: true }
    editingRowId.value = null
    setTimeout(() => {
      rowSuccess.value = { ...rowSuccess.value, [row.rowId]: false }
    }, 2000)
  } catch (error: unknown) {
    rowError.value = {
      ...rowError.value,
      [row.rowId]: error instanceof Error ? error.message : 'Save failed',
    }
  } finally {
    savingRowId.value = null
  }
}

function isSecretKey(key: ConfigKey) {
  return SECRET_KEYS.includes(key)
}

function formatValue(item: Pick<ConfigCatalogRow, 'sensitive' | 'configured' | 'value'>) {
  if (item.sensitive) {
    return item.configured ? '••••••••' : '—'
  }
  if (!item.value) return '—'
  return item.value
}

function formatDate(value?: string) {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function displayValue(value?: string) {
  return value && value.trim().length > 0 ? value : '—'
}
</script>

<template>
  <div class="config-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">Configuration Management</h1>
        <p class="view-subtitle">
          Manage the shared system integrations used by agent execution and review workflows.
        </p>
      </div>

      <button class="btn btn-secondary" type="button" :disabled="refreshing" @click="refreshConfig">
        {{ refreshing ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <div v-if="!canEdit" class="helper-banner helper-banner-muted">
      Read-only mode. Only <strong>DEVOPS_ADMIN</strong> can edit configuration.
    </div>

    <div class="mode-tabs">
        <button
          class="mode-tab"
          :class="{ active: activeView === 'component' }"
          type="button"
          @click="selectView('component')"
        >
          Component
        </button>
        <button
          class="mode-tab"
          :class="{ active: activeView === 'scope' }"
          type="button"
          @click="selectView('scope')"
        >
          Scope Directory
        </button>
        <button
          class="mode-tab"
          :class="{ active: activeView === 'raw' }"
          type="button"
          @click="selectView('raw')"
        >
          Configuration
        </button>
    </div>

    <div v-if="activeView === 'component'" class="component-workspace">
        <div class="helper-banner">
          Resolution priority: <strong>Agent Override</strong> → <strong>SNOW Group Default</strong>
          → <strong>Application Default</strong> → <strong>Platform Default</strong>.
          Use the <strong>Configuration</strong> tab for key-level edits.
        </div>

        <div v-if="store.error" class="alert alert-error">
          {{ store.error }}
        </div>

        <div class="toolbar-card">
          <div class="toolbar-grid">
            <div class="toolbar-field toolbar-field-wide">
              <label class="toolbar-label">Component Name</label>
              <input
                v-model="searchTerm"
                class="form-control"
                type="text"
                placeholder="Search component, description, or endpoint"
              />
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Status</label>
              <select v-model="statusFilter" class="form-control">
                <option value="All">Any Status</option>
                <option value="Ready">Ready</option>
                <option value="Partial">Partial</option>
                <option value="Needs Setup">Needs Setup</option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Application</label>
              <select v-model="componentScopeFilters.application" class="form-control">
                <option v-for="application in componentApplicationOptions" :key="application" :value="application">
                  {{ application === 'All' ? 'All Applications' : application }}
                </option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">SNOW Group</label>
              <select v-model="componentScopeFilters.owningGroup" class="form-control">
                <option v-for="group in componentOwningGroupOptions" :key="group" :value="group">
                  {{ group === 'All' ? 'All SNOW Groups' : group }}
                </option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Agent</label>
              <select v-model="componentScopeFilters.agent" class="form-control">
                <option v-for="agent in componentAgentOptions" :key="agent" :value="agent">
                  {{ agent === 'All' ? 'All Agents' : agent }}
                </option>
              </select>
            </div>
          </div>
        </div>

        <div class="table-card">
          <div class="table-header">
            <div>
              <h2 class="section-title">Available Components ({{ filteredComponentRows.length }})</h2>
              <p class="section-subtitle">
                Scoped integration rows that can override shared defaults for a specific
                application, SNOW Group, or agent.
              </p>
            </div>
            <button
              v-if="canEdit"
              class="btn btn-primary"
              type="button"
              @click="openCreateComponent"
            >
              Add Scoped Component
            </button>
          </div>

          <div v-if="filteredComponentRows.length === 0" class="empty-state">
            No components matched the current filters.
          </div>

          <div v-else class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Component</th>
                  <th>Application</th>
                  <th>SNOW Group</th>
                  <th>Agent</th>
                  <th>Area</th>
                  <th>Endpoint</th>
                  <th>Service User</th>
                  <th>Scope Source</th>
                  <th>Secret</th>
                  <th>Status</th>
                  <th>Updated By</th>
                  <th>Updated On</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="component in filteredComponentRows" :key="component.id">
                  <td>
                    <div class="component-name">{{ component.label }}</div>
                    <div class="component-description">{{ component.description }}</div>
                  </td>
                  <td>{{ component.application ?? '—' }}</td>
                  <td>{{ component.owningGroup ?? '—' }}</td>
                  <td>{{ component.agent ?? '—' }}</td>
                  <td>{{ component.category }}</td>
                  <td class="endpoint-cell">{{ displayValue(component.endpoint) }}</td>
                  <td>{{ displayValue(component.serviceUser) }}</td>
                  <td>
                    <span class="scope-source-badge">
                      {{ component.scopeSource ?? 'Platform Default' }}
                    </span>
                  </td>
                  <td>
                    <span class="secret-badge" :class="`secret-${component.secretState.toLowerCase().replace(/ /g, '-')}`">
                      {{ component.secretState }}
                    </span>
                  </td>
                  <td>
                    <span class="status-badge" :class="`status-${component.status.toLowerCase().replace(/ /g, '-')}`">
                      {{ component.status }}
                    </span>
                  </td>
                  <td>{{ component.updatedBy ?? '—' }}</td>
                  <td class="timestamp">{{ formatDate(component.updatedAt) }}</td>
                  <td>
                    <div class="action-btns">
                      <button
                        class="btn btn-secondary btn-sm"
                        type="button"
                        :disabled="!canEdit"
                        :title="canEdit ? '' : 'DEVOPS_ADMIN can edit configuration.'"
                        @click="openComponentEditor(component)"
                      >
                        Edit
                      </button>
                      <button
                        class="btn btn-danger btn-sm"
                        type="button"
                        :disabled="!canEdit || !component.id || deletingComponentId === component.id"
                        :title="deleteTitle(component)"
                        @click="deleteComponent(component)"
                      >
                        {{ deletingComponentId === component.id ? 'Deleting...' : 'Delete' }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
    </div>

    <div v-else-if="activeView === 'scope'" class="scope-workspace">
        <div class="helper-banner">
          Maintain the curated <strong>Application + SNOW Group</strong> directory used by upload
          dialogs. Uploaders choose from this list instead of typing free-form values each time.
        </div>

        <div v-if="scopeDirectoryStore.error || scopeError" class="alert alert-error">
          {{ scopeError || scopeDirectoryStore.error }}
        </div>

        <div class="toolbar-card">
          <div class="toolbar-grid">
            <div class="toolbar-field toolbar-field-wide">
              <label class="toolbar-label">Search</label>
              <input
                v-model="scopeSearch"
                class="form-control"
                type="text"
                placeholder="Search application or SNOW Group"
              />
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Application</label>
              <select v-model="scopeApplicationFilter" class="form-control">
                <option
                  v-for="application in scopeApplicationOptions"
                  :key="application"
                  :value="application"
                >
                  {{ application === 'All' ? 'All Applications' : application }}
                </option>
              </select>
            </div>
          </div>
        </div>

        <div class="table-card">
          <div class="table-header">
            <div>
              <h2 class="section-title">Scope Directory ({{ filteredScopeEntries.length }})</h2>
              <p class="section-subtitle">
                Curated Application + SNOW Group choices for upload dialogs.
              </p>
            </div>
            <button
              v-if="canEdit"
              class="btn btn-primary"
              type="button"
              @click="openCreateScopeEntry"
            >
              Add Scope Entry
            </button>
          </div>

          <div
            v-if="scopeDirectoryStore.loading && !scopeDirectoryStore.loaded"
            class="loading-state"
          >
            <span class="spinner"></span>
            <span>Loading scope directory...</span>
          </div>

          <div
            v-else-if="!scopeDirectoryStore.loading && filteredScopeEntries.length === 0"
            class="empty-state"
          >
            No scope directory entries matched the current filters.
          </div>

          <div v-else class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Application</th>
                  <th>SNOW Group</th>
                  <th>Scope Source</th>
                  <th>Updated By</th>
                  <th>Updated On</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in filteredScopeEntries" :key="entry.id">
                  <td>{{ entry.application }}</td>
                  <td>{{ entry.snowGroup ?? '—' }}</td>
                  <td>
                    <span class="scope-source-badge">{{ entry.scopeSource }}</span>
                  </td>
                  <td>{{ entry.updatedBy ?? '—' }}</td>
                  <td class="timestamp">{{ formatDate(entry.updatedAt) }}</td>
                  <td>
                    <div class="action-btns">
                      <button
                        class="btn btn-secondary btn-sm"
                        type="button"
                        :disabled="!canEdit"
                        :title="canEdit ? '' : 'DEVOPS_ADMIN can edit scope directory entries.'"
                        @click="openScopeEditor(entry)"
                      >
                        Edit
                      </button>
                      <button
                        class="btn btn-danger btn-sm"
                        type="button"
                        :disabled="!canEdit || deletingScopeEntryId === entry.id"
                        @click="deleteScopeEntry(entry)"
                      >
                        {{ deletingScopeEntryId === entry.id ? 'Deleting...' : 'Delete' }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
    </div>

    <div v-else class="raw-workspace">
        <div class="helper-banner helper-banner-muted">
          Key-level configuration rows. Runtime resolves the most specific match by Application, SNOW Group, and Agent.
        </div>

        <div class="toolbar-card">
          <div class="toolbar-grid toolbar-grid-config">
            <div class="toolbar-field">
              <label class="toolbar-label">SNOW Group</label>
              <select v-model="filterForm.owningGroup" class="form-control">
                <option v-for="group in owningGroupOptions" :key="group" :value="group">
                  {{ group === 'All' ? 'All SNOW Groups' : group }}
                </option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Application</label>
              <select v-model="filterForm.application" class="form-control">
                <option v-for="application in applicationOptions" :key="application" :value="application">
                  {{ application === 'All' ? 'All Applications' : application }}
                </option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Agent</label>
              <select v-model="filterForm.agent" class="form-control">
                <option v-for="agent in agentOptions" :key="agent" :value="agent">
                  {{ agent === 'All' ? 'All Agents' : agent }}
                </option>
              </select>
            </div>

            <div class="toolbar-field">
              <label class="toolbar-label">Config Item</label>
              <select v-model="filterForm.configItem" class="form-control">
                <option v-for="item in configItemOptions" :key="item" :value="item">
                  {{ item === 'All' ? 'All Config Items' : item }}
                </option>
              </select>
            </div>

            <div class="toolbar-actions">
              <button class="btn btn-primary" type="button" @click="applyRawFilters">Search</button>
              <button class="btn btn-secondary" type="button" @click="resetRawFilters">Reset</button>
            </div>
          </div>
        </div>

        <div v-if="store.error" class="alert alert-error">
          {{ store.error }}
        </div>

        <div v-if="store.loading && store.items.length === 0" class="loading-state">
          <span class="spinner"></span>
          <span>Loading configuration...</span>
        </div>

        <div v-else-if="!store.loading && filteredRawRows.length === 0" class="empty-state">
          No configuration items matched the current filters.
        </div>

        <div v-else class="table-card">
          <div class="table-header">
            <div>
              <h2 class="section-title">Configuration Items ({{ filteredRawRows.length }})</h2>
              <p class="section-subtitle">
                Each row belongs to one scoped component instance; edits here update that same
                component and therefore affect runtime resolution.
              </p>
            </div>
          </div>

          <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Application</th>
                <th>SNOW Group</th>
                <th>Agent</th>
                <th>Config Item</th>
                <th>Config Value</th>
                <th>Scope Source</th>
                <th>Updated By</th>
                <th>Updated Time</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in filteredRawRows" :key="row.rowId">
                <tr>
                  <td>{{ row.application }}</td>
                  <td>{{ row.owningGroup }}</td>
                  <td>{{ row.agent }}</td>
                  <td>
                    <div class="config-item-name mono">{{ row.label }}</div>
                    <div class="config-item-meta">{{ row.integration }}</div>
                  </td>
                  <td class="config-value-cell">
                    <template v-if="editingRowId === row.rowId">
                      <input
                        v-model="editForm.value"
                        class="form-control inline-input"
                        :type="row.sensitive ? 'password' : 'text'"
                      />
                      <input
                        v-model="editForm.description"
                        class="form-control inline-input description-input"
                        type="text"
                        placeholder="Optional description..."
                      />
                    </template>
                    <template v-else>
                      <div class="config-value-text">{{ formatValue(row) }}</div>
                      <div class="config-item-meta">{{ row.description ?? 'No description' }}</div>
                    </template>
                  </td>
                  <td>
                    <span class="scope-source-badge">{{ row.scopeSource }}</span>
                  </td>
                  <td>{{ row.updatedBy ?? '—' }}</td>
                  <td class="timestamp">{{ formatDate(row.updatedAt) }}</td>
                  <td>
                    <div class="action-btns">
                      <template v-if="editingRowId === row.rowId">
                        <button
                          class="btn btn-primary btn-sm"
                          :disabled="savingRowId === row.rowId"
                          @click="saveEdit(row)"
                        >
                          <span
                            v-if="savingRowId === row.rowId"
                            class="spinner"
                            style="width: 12px; height: 12px; border-width: 1px"
                          ></span>
                          Save
                        </button>
                        <button class="btn btn-secondary btn-sm" @click="cancelEdit">Cancel</button>
                      </template>
                      <template v-else>
                        <button
                          class="btn btn-secondary btn-sm"
                          :disabled="!canEdit"
                          :title="canEdit ? '' : 'DEVOPS_ADMIN can edit configuration.'"
                          @click="startEdit(row)"
                        >
                          {{ row.value ? 'Edit' : 'Set Value' }}
                        </button>
                      </template>
                    </div>
                  </td>
                </tr>
                <tr v-if="rowError[row.rowId] || rowSuccess[row.rowId]">
                  <td colspan="9" class="feedback-row">
                    <span v-if="rowError[row.rowId]" class="feedback-error">{{ rowError[row.rowId] }}</span>
                    <span v-if="rowSuccess[row.rowId]" class="feedback-success">Saved successfully.</span>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        </div>
    </div>

    <ConfigComponentDialog
      v-if="editingComponent || creatingComponent"
      :key="editingComponent?.id ?? editingComponent?.componentId ?? 'new-component'"
      :component="editingComponent"
      :mode="creatingComponent ? 'create' : 'edit'"
      :component-options="componentDialogOptions"
      :saving="componentSaving"
      :error="componentError"
      @close="closeComponentEditor"
      @save="saveComponent"
    />
    <ScopeDirectoryDialog
      v-if="editingScopeEntry || creatingScopeEntry"
      :key="editingScopeEntry?.id ?? 'new-scope-entry'"
      :entry="editingScopeEntry"
      :mode="creatingScopeEntry ? 'create' : 'edit'"
      :saving="scopeSaving"
      :error="scopeError"
      @close="closeScopeEditor"
      @save="saveScopeEntry"
    />
  </div>
</template>

<style scoped>
.config-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.view-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.view-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.view-subtitle {
  margin: 8px 0 0;
  max-width: 720px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.mode-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #dbe3f0;
}

.mode-tab {
  padding: 12px 16px;
  border: none;
  border-radius: 12px 12px 0 0;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.mode-tab.active {
  color: #1d4ed8;
  background: #eff6ff;
}

.component-workspace,
.scope-workspace,
.raw-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.helper-banner {
  padding: 14px 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
  border: 1px solid #dbeafe;
  color: #1e3a8a;
  font-size: 14px;
  line-height: 1.6;
}

.helper-banner-muted {
  color: var(--color-text-secondary);
  background: var(--color-surface-secondary);
  border-color: #e2e8f0;
}

.toolbar-card,
.table-card,
.table-container {
  background: white;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.toolbar-card {
  padding: 18px;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: 2fr minmax(180px, 260px);
  gap: 16px;
}

.toolbar-grid-config {
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
}

.toolbar-field-wide {
  grid-column: span 2;
}

.toolbar-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.table-card {
  padding: 18px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.section-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.table-container {
  overflow: hidden;
}

.component-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.component-description {
  margin-top: 4px;
  max-width: 320px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.endpoint-cell {
  max-width: 260px;
  word-break: break-word;
}

.config-item-name {
  font-size: 13px;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.config-item-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.config-value-cell {
  min-width: 280px;
}

.config-value-text {
  max-width: 420px;
  word-break: break-word;
  color: var(--color-text-primary);
}

.status-badge,
.secret-badge,
.scope-source-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.scope-source-badge {
  color: #334155;
  background: #e2e8f0;
}

.status-ready {
  color: #166534;
  background: #dcfce7;
}

.status-partial {
  color: #92400e;
  background: #fef3c7;
}

.status-needs-setup {
  color: #991b1b;
  background: #fee2e2;
}

.secret-configured {
  color: #1d4ed8;
  background: #dbeafe;
}

.secret-missing {
  color: #991b1b;
  background: #fee2e2;
}

.secret-not-required {
  color: #475569;
  background: #e2e8f0;
}

.key-cell {
  font-size: 13px;
  color: #2563eb;
  white-space: nowrap;
}

.mono {
  font-family: var(--font-mono);
}

.timestamp {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.inline-input {
  min-width: 180px;
}

.description-input {
  margin-top: 8px;
}

.action-btns {
  display: flex;
  gap: 6px;
}

.feedback-row {
  padding: 4px 14px 8px;
}

.feedback-error {
  font-size: 12px;
  color: #dc2626;
}

.feedback-success {
  font-size: 12px;
  color: #16a34a;
}

@media (max-width: 1080px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-grid-config {
    grid-template-columns: 1fr;
  }

  .toolbar-field-wide {
    grid-column: span 1;
  }
}

@media (max-width: 720px) {
  .view-header {
    flex-direction: column;
    align-items: stretch;
  }

  .mode-tabs {
    overflow-x: auto;
  }
}
</style>
