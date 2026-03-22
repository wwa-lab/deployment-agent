<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useUserStore } from '../stores/user'
import CreateTemplateDialog from '../components/CreateTemplateDialog.vue'
import DeleteTemplateDialog from '../components/DeleteTemplateDialog.vue'
import TemplateTaskDialog from '../components/TemplateTaskDialog.vue'
import type { CreateTemplateDraft, TemplateRecord, TemplateTask, TemplateTaskDraft } from '../types'

const userStore = useUserStore()

const templates = ref<TemplateRecord[]>([
  {
    id: 'tpl-001',
    name: 'Deployment Agent POC',
    version: '1.0',
    agent: 'Deployment Agent',
    category: 'development',
    snowGroup: 'HTSA-CSI-HCC-AMH-PRJ',
    application: 'AMH HCC',
    site: 'HK',
    estDuration: '20m',
    description: 'AMH HCC deployment starter template for local release testing.',
    createdBy: 'Admin User',
    createdAt: '2026-01-23T07:18:18Z',
    updatedAt: '2026-01-29T10:24:45Z',
    tasks: [
      {
        id: 'tpl-001-task-1',
        category: 'release preparation',
        taskName: 'Prepare Deployment Package',
        step: 1,
        stepName: 'verify build artifact',
        type: 'MANUAL',
        owner: 'alice',
        estDuration: '5m',
      },
      {
        id: 'tpl-001-task-2',
        category: 'release',
        taskName: 'Deploy Application',
        step: 2,
        stepName: 'trigger deployment job',
        type: 'AUTO',
        owner: 'alice',
        estDuration: '10m',
        dependencies: 'Prepare Deployment Package',
      },
      {
        id: 'tpl-001-task-3',
        category: 'post-release',
        taskName: 'Smoke Validation',
        step: 3,
        stepName: 'confirm service health',
        type: 'MANUAL',
        owner: 'alice',
        estDuration: '5m',
        dependencies: 'Deploy Application',
      },
    ],
  },
  {
    id: 'tpl-002',
    name: 'AMH-HCC-SIT-TEMPLATE',
    version: '1.0',
    agent: 'Deployment Agent',
    category: 'development',
    snowGroup: 'HTSA-CSI-HCC-AMH-PRJ',
    application: 'AMH HCC',
    site: 'HK',
    estDuration: '1h',
    description: 'AMH HCC SIT template with stage-based deployment tasks and review checkpoints.',
    createdBy: 'Admin User',
    createdAt: '2026-01-23T07:18:18Z',
    updatedAt: '2026-01-29T10:24:45Z',
    tasks: [
      {
        id: 'tpl-002-task-1',
        category: 'release preparation',
        taskName: 'Prepare Deployment Package',
        step: 1,
        stepName: 'collect sit release files',
        type: 'MANUAL',
        owner: 'alice',
        estDuration: '10m',
      },
      {
        id: 'tpl-002-task-2',
        category: 'pre-release',
        taskName: 'Pre-Deployment Checks',
        step: 2,
        stepName: 'confirm sit environment readiness',
        type: 'MANUAL',
        owner: 'alice',
        estDuration: '10m',
        dependencies: 'Prepare Deployment Package',
      },
      {
        id: 'tpl-002-task-3',
        category: 'release',
        taskName: 'Deploy Application',
        step: 3,
        stepName: 'deploy sit package',
        type: 'AUTO',
        owner: 'alice',
        estDuration: '20m',
        dependencies: 'Pre-Deployment Checks',
      },
      {
        id: 'tpl-002-task-4',
        category: 'post-release',
        taskName: 'Smoke Validation',
        step: 4,
        stepName: 'run sit verification checklist',
        type: 'MANUAL',
        owner: 'alice',
        estDuration: '20m',
        dependencies: 'Deploy Application',
      },
    ],
  },
  {
    id: 'tpl-003',
    name: 'AMH-HCC-UAT-TEMPLATE',
    version: '1.2',
    agent: 'Deployment Agent',
    category: 'release',
    snowGroup: 'HTSA-CSI-HCC-AMH-PRJ',
    application: 'AMH HCC',
    site: 'SG',
    estDuration: '2h',
    description: 'UAT deployment flow with approval checkpoints and validation after rollout.',
    createdBy: 'Bob Kim',
    createdAt: '2026-02-02T09:15:00Z',
    updatedAt: '2026-02-18T11:40:00Z',
    tasks: [
      {
        id: 'tpl-003-task-1',
        category: 'release preparation',
        taskName: 'Prepare Deployment Package',
        step: 1,
        stepName: 'lock approved uat bundle',
        type: 'MANUAL',
        owner: 'bob',
        estDuration: '20m',
      },
      {
        id: 'tpl-003-task-2',
        category: 'pre-release',
        taskName: 'Approval Gate',
        step: 2,
        stepName: 'confirm deployment approval',
        type: 'MANUAL',
        owner: 'bob',
        estDuration: '15m',
        dependencies: 'Prepare Deployment Package',
      },
      {
        id: 'tpl-003-task-3',
        category: 'release',
        taskName: 'Deploy Application',
        step: 3,
        stepName: 'run uat deployment job',
        type: 'AUTO',
        owner: 'bob',
        estDuration: '45m',
        dependencies: 'Approval Gate',
      },
      {
        id: 'tpl-003-task-4',
        category: 'post-release',
        taskName: 'Validation and Handover',
        step: 4,
        stepName: 'capture evidence and hand over',
        type: 'MANUAL',
        owner: 'bob',
        estDuration: '40m',
        dependencies: 'Deploy Application',
      },
    ],
  },
  {
    id: 'tpl-004',
    name: 'PowerCARD PRD Baseline',
    version: '2.1',
    agent: 'PowerCARD',
    category: 'production',
    snowGroup: 'HTSA-CSI-CARD-PRD',
    application: 'PowerCARD',
    site: 'HK',
    estDuration: '4h',
    description: 'Production template for staged rollout, validation, and rollback readiness.',
    createdBy: 'Carol Lee',
    createdAt: '2026-02-10T08:10:00Z',
    updatedAt: '2026-03-01T17:20:00Z',
    tasks: [
      {
        id: 'tpl-004-task-1',
        category: 'release preparation',
        taskName: 'Prepare Change Window',
        step: 1,
        stepName: 'confirm prd slot and stakeholders',
        type: 'MANUAL',
        owner: 'carol',
        estDuration: '30m',
      },
      {
        id: 'tpl-004-task-2',
        category: 'pre-release',
        taskName: 'Backup and Safeguard',
        step: 2,
        stepName: 'complete production backup',
        type: 'AUTO',
        owner: 'carol',
        estDuration: '45m',
        dependencies: 'Prepare Change Window',
      },
      {
        id: 'tpl-004-task-3',
        category: 'release',
        taskName: 'Deploy Application',
        step: 3,
        stepName: 'run production deployment',
        type: 'AUTO',
        owner: 'carol',
        estDuration: '90m',
        dependencies: 'Backup and Safeguard',
      },
      {
        id: 'tpl-004-task-4',
        category: 'post-release',
        taskName: 'Post-Release Validation',
        step: 4,
        stepName: 'validate production health checks',
        type: 'MANUAL',
        owner: 'carol',
        estDuration: '45m',
        dependencies: 'Deploy Application',
      },
      {
        id: 'tpl-004-task-5',
        category: 'post-release',
        taskName: 'Rollback Readiness',
        step: 5,
        stepName: 'confirm rollback evidence pack',
        type: 'MANUAL',
        owner: 'carol',
        estDuration: '30m',
        dependencies: 'Post-Release Validation',
      },
    ],
  },
])

const search = ref('')
const selectedCategory = ref('All')
const selectedAgent = ref('All')
const selectedSnowGroup = ref('All')
const selectedApplication = ref('All')
const selectedSite = ref('All')
const selectedTemplateId = ref('')
const activeMoreMenuId = ref('')
const actionFeedback = ref('')
const showCreateTemplateDialog = ref(false)
const editingTemplateId = ref('')
const deletingTemplateId = ref('')
const showTaskDialog = ref(false)
const editingTaskId = ref('')

const defaultActivityCategories = [
  'release preparation',
  'pre-release',
  'release',
  'post-release',
]

const categories = computed(() =>
  Array.from(new Set(templates.value.map((template) => template.category))).sort(),
)

const agents = computed(() =>
  Array.from(new Set(templates.value.map((template) => template.agent))).sort(),
)

const snowGroups = computed(() =>
  Array.from(new Set(templates.value.map((template) => template.snowGroup))).sort(),
)

const applications = computed(() =>
  Array.from(new Set(templates.value.map((template) => template.application))).sort(),
)

const sites = computed(() =>
  Array.from(new Set(templates.value.map((template) => template.site))).sort(),
)

const filteredTemplates = computed(() => {
  const keyword = search.value.trim().toLowerCase()

  return templates.value.filter((template) => {
    const matchesKeyword =
      keyword.length === 0 ||
      template.name.toLowerCase().includes(keyword) ||
      template.description.toLowerCase().includes(keyword)

    const matchesCategory =
      selectedCategory.value === 'All' || template.category === selectedCategory.value

    const matchesAgent =
      selectedAgent.value === 'All' || template.agent === selectedAgent.value

    const matchesSnowGroup =
      selectedSnowGroup.value === 'All' || template.snowGroup === selectedSnowGroup.value

    const matchesApplication =
      selectedApplication.value === 'All' || template.application === selectedApplication.value

    const matchesSite = selectedSite.value === 'All' || template.site === selectedSite.value

    return (
      matchesKeyword &&
      matchesCategory &&
      matchesAgent &&
      matchesSnowGroup &&
      matchesApplication &&
      matchesSite
    )
  })
})

const selectedTemplate = computed(() => {
  const explicitSelection = filteredTemplates.value.find(
    (template) => template.id === selectedTemplateId.value,
  )
  if (explicitSelection) return explicitSelection
  return filteredTemplates.value[0] ?? null
})

const editingTemplate = computed(() =>
  templates.value.find((template) => template.id === editingTemplateId.value) ?? null,
)

const deletingTemplate = computed(() =>
  templates.value.find((template) => template.id === deletingTemplateId.value) ?? null,
)

const editingTask = computed(() =>
  selectedTemplate.value?.tasks.find((task) => task.id === editingTaskId.value) ?? null,
)

const selectedTemplateTaskNames = computed(() =>
  selectedTemplate.value?.tasks.map((task) => task.taskName) ?? [],
)

const selectedTemplateActivityCategories = computed(() =>
  Array.from(
    new Set([
      ...defaultActivityCategories,
      ...(selectedTemplate.value?.tasks.map((task) => task.category) ?? []),
    ]),
  ),
)

const selectedTemplateNextStep = computed(() => (selectedTemplate.value?.tasks.length ?? 0) + 1)

function selectTemplate(templateId: string) {
  selectedTemplateId.value = templateId
  actionFeedback.value = ''
}

function resetFilters() {
  search.value = ''
  selectedCategory.value = 'All'
  selectedAgent.value = 'All'
  selectedSnowGroup.value = 'All'
  selectedApplication.value = 'All'
  selectedSite.value = 'All'
  activeMoreMenuId.value = ''
  actionFeedback.value = ''
}

function executionTypeBadgeClass(type: 'MANUAL' | 'AUTO'): string {
  return type === 'MANUAL' ? 'badge-manual' : 'badge-auto'
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('en-US', {
    month: 'numeric',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function toggleMoreMenu(templateId: string) {
  activeMoreMenuId.value = activeMoreMenuId.value === templateId ? '' : templateId
}

function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  if (remainingMinutes === 0) return `${hours}h`
  return `${hours}h ${remainingMinutes}m`
}

function parseDurationToMinutes(value: string): number {
  const hoursMatch = value.match(/(\d+)\s*h/i)
  const minutesMatch = value.match(/(\d+)\s*m/i)
  const hours = hoursMatch ? Number.parseInt(hoursMatch[1], 10) : 0
  const minutes = minutesMatch ? Number.parseInt(minutesMatch[1], 10) : 0
  const total = hours * 60 + minutes
  return total > 0 ? total : 0
}

function normalizeTemplateTasks(tasks: TemplateTask[]): TemplateTask[] {
  return [...tasks]
    .sort((left, right) => left.step - right.step || left.taskName.localeCompare(right.taskName))
    .map((task, index) => ({
      ...task,
      step: index + 1,
    }))
}

function calculateTemplateDuration(tasks: TemplateTask[], fallback: string): string {
  const totalMinutes = tasks.reduce((sum, task) => sum + parseDurationToMinutes(task.estDuration), 0)
  return totalMinutes > 0 ? formatDuration(totalMinutes) : fallback
}

function updateTemplateRecord(templateId: string, updater: (template: TemplateRecord) => TemplateRecord) {
  templates.value = templates.value.map((template) =>
    template.id === templateId ? updater(template) : template,
  )
}

function generateCloneName(baseName: string): string {
  const existingNames = new Set(templates.value.map((template) => template.name))
  const directCloneName = `${baseName} Copy`

  if (!existingNames.has(directCloneName)) {
    return directCloneName
  }

  let copyIndex = 2
  while (existingNames.has(`${baseName} Copy ${copyIndex}`)) {
    copyIndex += 1
  }

  return `${baseName} Copy ${copyIndex}`
}

function handleTemplateAction(
  action: 'clone' | 'edit' | 'delete',
  template: TemplateRecord,
) {
  selectedTemplateId.value = template.id
  activeMoreMenuId.value = ''

  if (action === 'clone') {
    const now = new Date().toISOString()
    const owner = userStore.displayName || userStore.userId || 'Current User'
    const clonedTemplateId = `tpl-${Date.now()}`
    const clonedTemplateName = generateCloneName(template.name)

    const clonedTasks = template.tasks.map((task, index) => ({
      ...task,
      id: `${clonedTemplateId}-task-${index + 1}`,
    }))

    const clonedTemplate: TemplateRecord = {
      ...template,
      id: clonedTemplateId,
      name: clonedTemplateName,
      version: '1.0',
      createdBy: owner,
      createdAt: now,
      updatedAt: now,
      tasks: clonedTasks,
    }

    templates.value = [clonedTemplate, ...templates.value]
    selectedTemplateId.value = clonedTemplateId
    actionFeedback.value = `Cloned "${template.name}" into "${clonedTemplateName}". The new draft keeps all tasks and is ready for editing.`
    return
  }

  if (action === 'edit') {
    editingTemplateId.value = template.id
    showCreateTemplateDialog.value = true
    return
  }

  deletingTemplateId.value = template.id
}

function openCreateTemplateDialog() {
  editingTemplateId.value = ''
  showCreateTemplateDialog.value = true
  activeMoreMenuId.value = ''
  actionFeedback.value = ''
}

function closeCreateTemplateDialog() {
  showCreateTemplateDialog.value = false
  editingTemplateId.value = ''
}

function closeDeleteTemplateDialog() {
  deletingTemplateId.value = ''
}

function confirmDeleteTemplate() {
  if (!deletingTemplate.value) return

  const removedTemplate = deletingTemplate.value
  const deletingSelectedTemplate = selectedTemplateId.value === removedTemplate.id
  const nextTemplates = templates.value.filter((template) => template.id !== removedTemplate.id)

  templates.value = nextTemplates

  if (deletingSelectedTemplate) {
    selectedTemplateId.value = nextTemplates[0]?.id ?? ''
    showTaskDialog.value = false
    editingTaskId.value = ''
  }

  if (editingTemplateId.value === removedTemplate.id) {
    editingTemplateId.value = ''
    showCreateTemplateDialog.value = false
  }

  deletingTemplateId.value = ''
  actionFeedback.value = `Deleted "${removedTemplate.name}" from the local template list.`
}

function openAddTaskDialog() {
  if (!selectedTemplate.value) return
  editingTaskId.value = ''
  showTaskDialog.value = true
}

function openEditTaskDialog(task: TemplateTask) {
  editingTaskId.value = task.id
  showTaskDialog.value = true
}

function closeTaskDialog() {
  showTaskDialog.value = false
  editingTaskId.value = ''
}

function removeTask(task: TemplateTask) {
  if (!selectedTemplate.value) return
  if (!window.confirm(`Delete task "${task.taskName}" from this template?`)) return

  const templateId = selectedTemplate.value.id
  const templateName = selectedTemplate.value.name
  const fallbackDuration = selectedTemplate.value.estDuration
  const nextTasks = normalizeTemplateTasks(
    selectedTemplate.value.tasks
      .filter((item) => item.id !== task.id)
      .map((item) => ({
        ...item,
        dependencies: item.dependencies === task.taskName ? undefined : item.dependencies,
      })),
  )

  updateTemplateRecord(templateId, (template) => ({
    ...template,
    tasks: nextTasks,
    estDuration: calculateTemplateDuration(nextTasks, fallbackDuration),
    updatedAt: new Date().toISOString(),
  }))

  actionFeedback.value = `Removed task "${task.taskName}" from "${templateName}".`
}

function saveTask(draft: TemplateTaskDraft) {
  if (!selectedTemplate.value) return

  const templateId = selectedTemplate.value.id
  const templateName = selectedTemplate.value.name
  const fallbackDuration = selectedTemplate.value.estDuration
  const previousTaskName = editingTask.value?.taskName
  const taskId = editingTask.value?.id ?? `${templateId}-task-${Date.now()}`

  const nextTask: TemplateTask = {
    id: taskId,
    category: draft.category,
    taskName: draft.taskName,
    step: draft.step,
    stepName: draft.stepName,
    type: draft.type,
    owner: draft.owner,
    estDuration: formatDuration(draft.estDurationMinutes),
    dependencies: draft.dependencies,
  }

  const mergedTasks = editingTask.value
    ? selectedTemplate.value.tasks.map((task) => {
        if (task.id === taskId) return nextTask
        if (previousTaskName && task.dependencies === previousTaskName) {
          return { ...task, dependencies: draft.taskName }
        }
        return task
      })
    : [...selectedTemplate.value.tasks, nextTask]

  const normalizedTasks = normalizeTemplateTasks(mergedTasks)

  updateTemplateRecord(templateId, (template) => ({
    ...template,
    tasks: normalizedTasks,
    estDuration: calculateTemplateDuration(normalizedTasks, fallbackDuration),
    updatedAt: new Date().toISOString(),
  }))

  actionFeedback.value = editingTask.value
    ? `Updated task "${draft.taskName}" in "${templateName}".`
    : `Added task "${draft.taskName}" to "${templateName}".`

  closeTaskDialog()
}

function submitTemplate(draft: CreateTemplateDraft) {
  if (editingTemplate.value) {
    const templateName = editingTemplate.value.name

    updateTemplateRecord(editingTemplate.value.id, (template) => ({
      ...template,
      name: draft.name,
      version: draft.version,
      agent: draft.agent,
      category: draft.category,
      snowGroup: draft.snowGroup,
      application: draft.application,
      site: draft.site,
      estDuration: formatDuration(draft.estDurationMinutes),
      description: draft.description,
      updatedAt: new Date().toISOString(),
    }))

    showCreateTemplateDialog.value = false
    editingTemplateId.value = ''
    actionFeedback.value = `Updated template "${templateName}" with refreshed metadata.`
    return
  }

  const now = new Date().toISOString()
  const owner = userStore.displayName || userStore.userId || 'Current User'
  const id = `tpl-${Date.now()}`

  const newTemplate: TemplateRecord = {
    id,
    name: draft.name,
    version: draft.version,
    agent: draft.agent,
    category: draft.category,
    snowGroup: draft.snowGroup,
    application: draft.application,
    site: draft.site,
    estDuration: formatDuration(draft.estDurationMinutes),
    description: draft.description,
    createdBy: owner,
    createdAt: now,
    updatedAt: now,
    tasks: [],
  }

  templates.value = [newTemplate, ...templates.value]
  selectedTemplateId.value = id
  showCreateTemplateDialog.value = false
  editingTemplateId.value = ''

  if (draft.source === 'manual') {
    actionFeedback.value = `Created local template draft "${draft.name}". Add the first task to make the template executable.`
    nextTick(() => {
      openAddTaskDialog()
    })
    return
  }

  actionFeedback.value = `Created local upload preview for "${draft.sourceFileName}". Excel parsing is not wired yet, so this draft starts without tasks.`
}
</script>

<template>
  <div class="template-view">
    <div class="view-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1 class="view-title">Select Deployment Template</h1>
        <p class="view-subtitle">
          Pick a reusable template, narrow by ownership context, and start from a known deployment baseline.
        </p>
      </div>
    </div>

    <div class="template-layout">
      <aside class="card filters-panel">
        <h2 class="panel-title">Filters</h2>

        <div class="filter-stack">
          <div class="filter-item">
            <label class="form-label" for="agent-filter">Agent</label>
            <select id="agent-filter" v-model="selectedAgent" class="form-control">
              <option value="All">All Agents</option>
              <option v-for="agent in agents" :key="agent" :value="agent">{{ agent }}</option>
            </select>
          </div>

          <div class="filter-item">
            <label class="form-label" for="snow-group-filter">SNOW Group</label>
            <select id="snow-group-filter" v-model="selectedSnowGroup" class="form-control">
              <option value="All">All SNOW Groups</option>
              <option v-for="group in snowGroups" :key="group" :value="group">{{ group }}</option>
            </select>
          </div>

          <div class="filter-item">
            <label class="form-label" for="application-filter">Application</label>
            <select id="application-filter" v-model="selectedApplication" class="form-control">
              <option value="All">All Applications</option>
              <option v-for="application in applications" :key="application" :value="application">
                {{ application }}
              </option>
            </select>
          </div>

          <div class="filter-item">
            <label class="form-label" for="site-filter">Site</label>
            <select id="site-filter" v-model="selectedSite" class="form-control">
              <option value="All">All Sites</option>
              <option v-for="site in sites" :key="site" :value="site">{{ site }}</option>
            </select>
          </div>
        </div>
      </aside>

      <div class="main-column">
        <section class="card toolbar-card">
          <div class="toolbar-row">
            <div class="toolbar-search">
              <label class="sr-only" for="template-search">Search templates</label>
              <input
                id="template-search"
                v-model="search"
                class="form-control"
                type="text"
                placeholder="Search templates by name or description…"
              />
            </div>

            <div class="toolbar-category">
              <label class="sr-only" for="category-filter">Category</label>
              <select id="category-filter" v-model="selectedCategory" class="form-control">
                <option value="All">All Categories</option>
                <option v-for="category in categories" :key="category" :value="category">
                  {{ category }}
                </option>
              </select>
            </div>

            <div class="toolbar-actions">
              <button class="btn btn-primary" type="button" @click="openCreateTemplateDialog">
                Create New Template
              </button>
              <button class="btn btn-secondary" type="button" @click="resetFilters">Refresh</button>
            </div>
          </div>
        </section>

        <section class="card table-card">
          <div class="table-head">
            <h2 class="panel-title">Available Templates ({{ filteredTemplates.length }})</h2>
            <p v-if="actionFeedback" class="table-feedback">{{ actionFeedback }}</p>
          </div>

          <div v-if="filteredTemplates.length === 0" class="empty-state">
            No templates match the current filters.
          </div>

          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Template Name</th>
                <th>Version</th>
                <th>Category</th>
                <th>SNOW Group</th>
                <th>Application</th>
                <th>Site</th>
                <th>Tasks</th>
                <th>Est. Duration</th>
                <th>Description</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="template in filteredTemplates"
                :key="template.id"
                class="clickable"
                :class="{ 'selected-row': selectedTemplate?.id === template.id }"
                @click="selectTemplate(template.id)"
              >
                <td class="template-name-cell">{{ template.name }}</td>
                <td class="mono-cell">{{ template.version }}</td>
                <td>
                  <span class="category-pill">{{ template.category }}</span>
                </td>
                <td>{{ template.snowGroup }}</td>
                <td>{{ template.application }}</td>
                <td>
                  <span class="site-pill">{{ template.site }}</span>
                </td>
                <td>{{ template.tasks.length }}</td>
                <td>{{ template.estDuration }}</td>
                <td class="description-cell">{{ template.description }}</td>
                <td>
                  <div class="action-btns" @click.stop>
                    <button class="btn btn-primary btn-sm" type="button" disabled>
                      Create Rundown
                    </button>
                    <div class="more-menu-wrap">
                      <button
                        class="btn btn-secondary btn-sm"
                        type="button"
                        :aria-expanded="activeMoreMenuId === template.id"
                        @click="toggleMoreMenu(template.id)"
                      >
                        More
                      </button>

                      <div v-if="activeMoreMenuId === template.id" class="more-menu">
                        <button
                          class="more-menu-item"
                          type="button"
                          @click="handleTemplateAction('clone', template)"
                        >
                          Clone
                        </button>
                        <button
                          class="more-menu-item"
                          type="button"
                          @click="handleTemplateAction('edit', template)"
                        >
                          Edit
                        </button>
                        <button
                          class="more-menu-item more-menu-item-danger"
                          type="button"
                          @click="handleTemplateAction('delete', template)"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-if="selectedTemplate" class="card details-card">
          <div class="details-head">
            <h2 class="panel-title">Template Details</h2>
            <div class="details-head-actions">
              <span class="mono-cell">{{ selectedTemplate.version }}</span>
              <button class="btn btn-secondary btn-sm" type="button" @click="handleTemplateAction('edit', selectedTemplate)">
                Edit Template
              </button>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">Basic Information</div>
            <div class="details-grid">
              <div class="detail-item detail-item-wide">
                <div class="detail-label">Template Name</div>
                <div class="detail-value">{{ selectedTemplate.name }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Version</div>
                <div class="detail-value">{{ selectedTemplate.version }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Category</div>
                <div class="detail-value">
                  <span class="category-pill">{{ selectedTemplate.category }}</span>
                </div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Estimated Duration</div>
                <div class="detail-value">{{ selectedTemplate.estDuration }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Agent</div>
                <div class="detail-value">{{ selectedTemplate.agent }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">SNOW Group</div>
                <div class="detail-value">{{ selectedTemplate.snowGroup }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Application</div>
                <div class="detail-value">{{ selectedTemplate.application }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Site</div>
                <div class="detail-value">{{ selectedTemplate.site }}</div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">Description</div>
            <div class="detail-value">{{ selectedTemplate.description }}</div>
          </div>

          <div class="detail-section">
            <div class="detail-section-head">
              <div class="detail-section-title">Tasks ({{ selectedTemplate.tasks.length }})</div>
              <button class="btn btn-primary btn-sm" type="button" @click="openAddTaskDialog">
                Add Task
              </button>
            </div>

            <div v-if="selectedTemplate.tasks.length === 0" class="empty-task-state">
              <p>
                No tasks have been added to this template yet. Start with the first manual or auto
                step so this template can be used as a real deployment blueprint.
              </p>
              <button class="btn btn-primary btn-sm" type="button" @click="openAddTaskDialog">
                Add First Task
              </button>
            </div>

            <div v-else class="task-table-wrap">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Activity Category</th>
                    <th>Task Name</th>
                    <th>Step</th>
                    <th>Step Name</th>
                    <th>Type</th>
                    <th>Owner</th>
                    <th>Est. Duration</th>
                    <th>Dependencies</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="task in selectedTemplate.tasks" :key="task.id">
                    <td>{{ task.category }}</td>
                    <td>{{ task.taskName }}</td>
                    <td>{{ task.step }}</td>
                    <td>{{ task.stepName }}</td>
                    <td>
                      <span class="badge" :class="executionTypeBadgeClass(task.type)">{{ task.type }}</span>
                    </td>
                    <td>{{ task.owner }}</td>
                    <td>{{ task.estDuration }}</td>
                    <td>{{ task.dependencies ?? '—' }}</td>
                    <td>
                      <div class="action-btns">
                        <button class="btn btn-secondary btn-sm" type="button" @click="openEditTaskDialog(task)">
                          Edit
                        </button>
                        <button class="btn btn-secondary btn-sm" type="button" @click="removeTask(task)">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">Metadata</div>
            <div class="details-grid">
              <div class="detail-item">
                <div class="detail-label">Created By</div>
                <div class="detail-value">{{ selectedTemplate.createdBy }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Created At</div>
                <div class="detail-value">{{ formatDateTime(selectedTemplate.createdAt) }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">Updated At</div>
                <div class="detail-value">{{ formatDateTime(selectedTemplate.updatedAt) }}</div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <CreateTemplateDialog
      v-if="showCreateTemplateDialog"
      :agents="agents"
      :categories="categories"
      :snow-groups="snowGroups"
      :applications="applications"
      :sites="sites"
      :mode="editingTemplate ? 'edit' : 'create'"
      :initial-draft="
        editingTemplate
          ? {
              name: editingTemplate.name,
              version: editingTemplate.version,
              agent: editingTemplate.agent,
              category: editingTemplate.category,
              snowGroup: editingTemplate.snowGroup,
              application: editingTemplate.application,
              site: editingTemplate.site,
              estDurationMinutes: parseDurationToMinutes(editingTemplate.estDuration),
              description: editingTemplate.description,
              source: 'manual',
            }
          : undefined
      "
      @close="closeCreateTemplateDialog"
      @submit="submitTemplate"
    />

    <TemplateTaskDialog
      v-if="showTaskDialog && selectedTemplate"
      :task="editingTask"
      :activity-categories="selectedTemplateActivityCategories"
      :existing-task-names="selectedTemplateTaskNames"
      :default-owner="userStore.displayName || userStore.userId || 'Current User'"
      :next-step="selectedTemplateNextStep"
      @close="closeTaskDialog"
      @save="saveTask"
    />

    <DeleteTemplateDialog
      v-if="deletingTemplate"
      :template="deletingTemplate"
      @close="closeDeleteTemplateDialog"
      @confirm="confirmDeleteTemplate"
    />
  </div>
</template>

<style scoped>
.template-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  max-width: 760px;
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
}

.template-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.filters-panel {
  padding: 18px;
}

.filter-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 14px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.main-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-card {
  padding: 18px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.toolbar-search {
  flex: 1 1 320px;
}

.toolbar-category {
  width: 220px;
  max-width: 100%;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-card {
  overflow: hidden;
}

.table-head {
  padding: 18px 18px 0;
}

.table-feedback {
  margin: 10px 0 0;
  font-size: 13px;
  color: #475569;
}

.panel-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.template-name-cell {
  min-width: 220px;
  font-weight: 600;
  color: #1e293b;
}

.mono-cell {
  font-family: monospace;
}

.category-pill,
.site-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.category-pill {
  background: #eef2ff;
  color: #4f46e5;
}

.site-pill {
  background: #eff6ff;
  color: #2563eb;
}

.description-cell {
  min-width: 260px;
  color: #475569;
}

.empty-task-state {
  padding: 16px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #f8fafc;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}

.selected-row {
  background: #f8fbff;
}

.details-card {
  padding: 18px;
}

.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.more-menu-wrap {
  position: relative;
}

.more-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 20;
  min-width: 128px;
  padding: 6px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
}

.more-menu-item {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.more-menu-item:hover {
  background: #f8fafc;
}

.more-menu-item-danger {
  color: #dc2626;
}

.more-menu-item-danger:hover {
  background: #fef2f2;
}

.details-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.details-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.detail-section {
  padding: 0 0 18px;
}

.detail-section + .detail-section {
  border-top: 1px solid #dbeafe;
  padding-top: 18px;
}

.detail-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
}

.detail-item {
  min-width: 0;
}

.detail-item-wide {
  grid-column: 1 / -1;
}

.detail-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
}

.detail-value {
  font-size: 14px;
  color: #334155;
  line-height: 1.6;
}

.task-table-wrap {
  overflow-x: auto;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1200px) {
  .template-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 800px) {
  .details-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-category {
    width: 100%;
  }
}
</style>
