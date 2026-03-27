<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ConfigComponentDialog from '../components/ConfigComponentDialog.vue'
import { useConfigStore } from '../stores/config'
import { useUserStore } from '../stores/user'
import type { ConfigComponentDraft, ConfigComponentRow, ConfigIntegrationId, ConfigKey } from '../types'

type ConfigCatalogRow = {
  componentId?: ConfigIntegrationId
  key: ConfigKey
  label: string
  application: string
  owningGroup: string
  agent: string
  scopeSource: 'Platform Default'
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
    defaultDescription: 'Configuration used for Jenkins-triggered deployment jobs.',
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

const CONFIG_CATALOG: Array<{
  key: ConfigKey
  label: string
  integrationId: ConfigIntegrationId
}> = [
  { key: 'jenkins_url', label: 'JENKINS_URL', integrationId: 'jenkins' },
  { key: 'jenkins_user', label: 'JENKINS_USER', integrationId: 'jenkins' },
  { key: 'jenkins_api_token', label: 'JENKINS_API_TOKEN', integrationId: 'jenkins' },
  { key: 'ansible_url', label: 'ANSIBLE_URL', integrationId: 'ansible' },
  { key: 'ansible_user', label: 'ANSIBLE_USER', integrationId: 'ansible' },
  { key: 'ansible_api_token', label: 'ANSIBLE_API_TOKEN', integrationId: 'ansible' },
  { key: 'execution_callback_endpoint', label: 'EXECUTION_CALLBACK_ENDPOINT', integrationId: 'callback' },
]

const SECRET_KEYS: ConfigKey[] = ['jenkins_api_token', 'ansible_api_token']

const store = useConfigStore()
const userStore = useUserStore()

const canEdit = computed(() => userStore.isDevOpsAdmin)

const activeView = ref<'component' | 'raw'>('raw')
const searchTerm = ref('')
const statusFilter = ref<'All' | ConfigComponentRow['status']>('All')
const componentScopeFilters = reactive({
  application: 'All',
  owningGroup: 'All',
  agent: 'All',
})
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

const editingComponentId = ref<ConfigIntegrationId | null>(null)
const componentSaving = ref(false)
const componentError = ref('')

const editingKey = ref<string | null>(null)
const editForm = reactive<{ value: string; description: string }>({ value: '', description: '' })
const savingKey = ref<string | null>(null)
const rowError = ref<Record<string, string>>({})
const rowSuccess = ref<Record<string, boolean>>({})

onMounted(() => {
  void store.fetchConfig()
})

const configItemsByKey = computed(() => {
  return new Map(store.items.map((item) => [item.key, item]))
})

const componentsById = computed(() => {
  return new Map(store.components.map((component) => [component.componentId, component]))
})

const componentRows = computed<ConfigComponentRow[]>(() => {
  return COMPONENT_DEFINITIONS.map((definition) => {
    const component = componentsById.value.get(definition.id)
    const endpointItem = configItemsByKey.value.get(definition.endpointKey)
    const userItem = definition.userKey ? configItemsByKey.value.get(definition.userKey) : undefined
    const secretItem = definition.secretKey ? configItemsByKey.value.get(definition.secretKey) : undefined

    const trackServiceUser = component?.trackServiceUser ?? Boolean(definition.userKey)
    const trackCredential = component?.trackCredential ?? Boolean(definition.secretKey)
    const requiredCount = 1 + (trackServiceUser ? 1 : 0) + (trackCredential ? 1 : 0)
    const configuredRequiredCount =
      (component?.serviceEndpoint?.trim() ? 1 : 0) +
      (trackServiceUser && component?.serviceUser?.trim() ? 1 : 0) +
      (trackCredential && component?.credentialConfigured ? 1 : 0)

    let status: ConfigComponentRow['status'] = 'Needs Setup'
    if (configuredRequiredCount === requiredCount && requiredCount > 0) {
      status = 'Ready'
    } else if (configuredRequiredCount > 0) {
      status = 'Partial'
    }

    return {
      id: definition.id,
      label: component?.displayName ?? definition.label,
      category: component?.area ?? definition.category,
      application: component?.application ?? '',
      owningGroup: component?.snowGroup ?? '',
      agent: component?.agent ?? '',
      scopeSource: 'Platform Default',
      endpointKey: definition.endpointKey,
      userKey: definition.userKey,
      secretKey: definition.secretKey,
      trackServiceUser,
      trackCredential,
      endpoint: component?.serviceEndpoint ?? '',
      serviceUser: component?.serviceUser ?? '',
      secretState: trackCredential
        ? component?.credentialConfigured
          ? 'Configured'
          : 'Missing'
        : 'Not required',
      description: component?.description ?? endpointItem?.description ?? definition.defaultDescription,
      updatedBy: component?.updatedBy ?? endpointItem?.updatedBy ?? userItem?.updatedBy ?? secretItem?.updatedBy,
      updatedAt: component?.updatedAt ?? endpointItem?.updatedAt ?? userItem?.updatedAt ?? secretItem?.updatedAt,
      status,
      credentialConfigured: component?.credentialConfigured ?? Boolean(secretItem?.configured),
    }
  })
})

const componentOwningGroupOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.owningGroup ?? '')),
].filter(Boolean))
const componentApplicationOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.application ?? '')),
].filter(Boolean))
const componentAgentOptions = computed(() => [
  'All',
  ...new Set(componentRows.value.map((row) => row.agent ?? '')),
].filter(Boolean))

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
  return CONFIG_CATALOG.map((entry) => {
    const item = configItemsByKey.value.get(entry.key)
    const component = componentsById.value.get(entry.integrationId)
    const definition = COMPONENT_DEFINITIONS.find((value) => value.id === entry.integrationId)

    return {
      componentId: item?.componentId ?? entry.integrationId,
      key: entry.key,
      label: entry.label,
      application: item?.application ?? component?.application ?? '',
      owningGroup: item?.snowGroup ?? component?.snowGroup ?? '',
      agent: item?.agent ?? component?.agent ?? '',
      scopeSource: 'Platform Default',
      integration: item?.integration ?? component?.displayName ?? definition?.label ?? '',
      area: item?.area ?? component?.area ?? definition?.category ?? '',
      value: item?.value ?? '',
      configured: item?.configured ?? Boolean(item?.value),
      sensitive: item?.sensitive ?? SECRET_KEYS.includes(entry.key),
      description: item?.description,
      updatedBy: item?.updatedBy ?? component?.updatedBy,
      updatedAt: item?.updatedAt ?? component?.updatedAt,
    }
  })
})

const owningGroupOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.owningGroup).filter(Boolean))])
const applicationOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.application).filter(Boolean))])
const agentOptions = computed(() => ['All', ...new Set(rawRows.value.map((row) => row.agent).filter(Boolean))])
const configItemOptions = computed(() => ['All', ...rawRows.value.map((row) => row.label)])

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

const editingComponent = computed(() => {
  if (!editingComponentId.value) return null
  return componentRows.value.find((row) => row.id === editingComponentId.value) ?? null
})

async function refreshConfig() {
  await store.fetchConfig()
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

function selectView(view: 'component' | 'raw') {
  activeView.value = view
}

function openComponentEditor(component: ConfigComponentRow) {
  if (!canEdit.value) return
  editingComponentId.value = component.id
  componentError.value = ''
}

function closeComponentEditor() {
  editingComponentId.value = null
  componentSaving.value = false
  componentError.value = ''
}

async function saveComponent(draft: ConfigComponentDraft) {
  if (!editingComponent.value || !canEdit.value) return

  componentSaving.value = true
  componentError.value = ''

  try {
    await store.saveComponent({
      componentId: editingComponent.value.id,
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

function startEdit(row: ConfigCatalogRow) {
  editingKey.value = row.key
  editForm.value = row.sensitive ? '' : row.value
  editForm.description = row.description ?? ''
  rowError.value = { ...rowError.value, [row.key]: '' }
  rowSuccess.value = { ...rowSuccess.value, [row.key]: false }
}

function cancelEdit() {
  editingKey.value = null
}

async function saveEdit(row: ConfigCatalogRow) {
  if (!canEdit.value) return
  savingKey.value = row.key
  rowError.value = { ...rowError.value, [row.key]: '' }

  if (!editForm.value.trim()) {
    rowError.value = {
      ...rowError.value,
      [row.key]: row.sensitive ? 'Enter a new credential value.' : 'Value must not be blank.',
    }
    savingKey.value = null
    return
  }

  try {
    await store.saveConfig({
      componentId: row.componentId,
      key: row.key,
      value: editForm.value,
      description: editForm.description,
    })
    await store.fetchConfig()
    rowSuccess.value = { ...rowSuccess.value, [row.key]: true }
    editingKey.value = null
    setTimeout(() => {
      rowSuccess.value = { ...rowSuccess.value, [row.key]: false }
    }, 2000)
  } catch (error: unknown) {
    rowError.value = {
      ...rowError.value,
      [row.key]: error instanceof Error ? error.message : 'Save failed',
    }
  } finally {
    savingKey.value = null
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
          Manage the shared system integrations used by Deployment Agent execution and review
          workflows.
        </p>
      </div>

      <button class="btn btn-secondary" type="button" :disabled="store.loading" @click="refreshConfig">
        {{ store.loading ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <div v-if="!canEdit" class="helper-banner helper-banner-muted">
      You are in read-only mode. All signed-in users can review shared configuration, and only
      <strong>DEVOPS_ADMIN</strong> can edit it. For local testing, use <strong>emp-003</strong>
      if you want to verify editing behavior.
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
          :class="{ active: activeView === 'raw' }"
          type="button"
          @click="selectView('raw')"
        >
          Configuration
        </button>
    </div>

    <div v-if="activeView === 'component'" class="component-workspace">
        <div class="helper-banner">
          This view groups the current backend configuration keys into reusable integration
          components. Scope fields show who this shared default is meant to serve today; true
          per-agent overrides are not wired yet. Use <strong>Configuration</strong> only when you
          need key-level edits.
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
                <option value="All">All Statuses</option>
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
                Fixed integrations backed by the current system configuration keys.
              </p>
            </div>
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
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :disabled="!canEdit"
                      :title="canEdit ? '' : 'DEVOPS_ADMIN can edit configuration.'"
                      @click="openComponentEditor(component)"
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
    </div>

    <div v-else class="raw-workspace">
        <div class="helper-banner helper-banner-muted">
          Configuration lists the fixed catalog of backend-managed settings. This view is closer to
          a traditional admin table: filter by context, review values, and edit only when needed.
          Today these keys still act as shared defaults, even when scoped ownership fields are shown.
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
                Current backend supports a fixed set of integration keys, so this table focuses on
                review and maintenance rather than free-form creation.
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
              <template v-for="row in filteredRawRows" :key="row.key">
                <tr>
                  <td>{{ row.application }}</td>
                  <td>{{ row.owningGroup }}</td>
                  <td>{{ row.agent }}</td>
                  <td>
                    <div class="config-item-name mono">{{ row.label }}</div>
                    <div class="config-item-meta">{{ row.integration }}</div>
                  </td>
                  <td class="config-value-cell">
                    <template v-if="editingKey === row.key">
                      <input
                        v-model="editForm.value"
                        class="form-control inline-input"
                        :type="isSecretKey(row.key) ? 'password' : 'text'"
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
                      <template v-if="editingKey === row.key">
                        <button
                          class="btn btn-primary btn-sm"
                          :disabled="savingKey === row.key"
                          @click="saveEdit(row)"
                        >
                          <span
                            v-if="savingKey === row.key"
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
                <tr v-if="rowError[row.key] || rowSuccess[row.key]">
                  <td colspan="9" class="feedback-row">
                    <span v-if="rowError[row.key]" class="feedback-error">{{ rowError[row.key] }}</span>
                    <span v-if="rowSuccess[row.key]" class="feedback-success">Saved successfully.</span>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        </div>
    </div>

    <ConfigComponentDialog
      v-if="editingComponent"
      :key="editingComponent.id"
      :component="editingComponent"
      :saving="componentSaving"
      :error="componentError"
      @close="closeComponentEditor"
      @save="saveComponent"
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
  color: #64748b;
}

.view-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.view-subtitle {
  margin: 8px 0 0;
  max-width: 720px;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
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
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.mode-tab.active {
  color: #1d4ed8;
  background: #eff6ff;
}

.component-workspace,
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
  color: #475569;
  background: #f8fafc;
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
  color: #475569;
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
  color: #0f172a;
}

.section-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.table-container {
  overflow: hidden;
}

.component-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.component-description {
  margin-top: 4px;
  max-width: 320px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.endpoint-cell {
  max-width: 260px;
  word-break: break-word;
}

.config-item-name {
  font-size: 13px;
  color: #0f172a;
  white-space: nowrap;
}

.config-item-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.config-value-cell {
  min-width: 280px;
}

.config-value-text {
  max-width: 420px;
  word-break: break-word;
  color: #0f172a;
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
  font-family: monospace;
}

.timestamp {
  font-size: 12px;
  color: #64748b;
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
