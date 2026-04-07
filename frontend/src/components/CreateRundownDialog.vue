<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { createRundownFromTemplate } from '../api/releaseFlows'
import { useUserStore } from '../stores/user'
import type { Stage, TemplateRecord, UploadResponse } from '../types'

const RELEASE_IDENTIFIER_PATTERN = /^(?<prefix>[a-z0-9]+(?:-[a-z0-9]+)*)-(?<stage>sit|uat|prod)-(?<sequence>0[1-9]|[1-9][0-9])$/i

const props = defineProps<{ template: TemplateRecord }>()

const emit = defineEmits<{
  close: []
  created: [result: UploadResponse]
}>()

const userStore = useUserStore()

const saving = ref(false)
const attemptedSubmit = ref(false)
const error = ref('')
const fieldErrors = reactive<{
  projectName?: string
  releaseId?: string
}>({})

const form = reactive({
  projectName: props.template.application || props.template.name,
  stage: defaultStageForCategory(props.template.category) as Stage,
  releaseId: '',
  application: props.template.application,
  snowGroup: props.template.snowGroup,
  agent: props.template.agent,
  site: props.template.site,
  owner: userStore.displayName || userStore.userId || '',
})

const estimatedRemainingMinutes = computed(() =>
  props.template.tasks.reduce((sum, task) => sum + parseDurationToMinutes(task.estDuration), 0),
)

const releaseIdentifierPlaceholder = computed(
  () => `e.g. amh-hcc-${form.stage.toLowerCase()}-01`,
)

const canSubmit = computed(
  () =>
    props.template.tasks.length > 0 &&
    form.projectName.trim().length > 0 &&
    form.releaseId.trim().length > 0 &&
    !saving.value,
)

function defaultStageForCategory(category: string): Stage {
  const normalized = category.trim().toLowerCase()
  if (normalized === 'production') return 'PROD'
  if (normalized === 'release') return 'UAT'
  return 'SIT'
}

function parseDurationToMinutes(value: string): number {
  const hoursMatch = value.match(/(\d+)\s*h/i)
  const minutesMatch = value.match(/(\d+)\s*m/i)
  const hours = hoursMatch ? Number.parseInt(hoursMatch[1], 10) : 0
  const minutes = minutesMatch ? Number.parseInt(minutesMatch[1], 10) : 0
  return hours * 60 + minutes
}

function deriveProjectId(projectName: string): string | undefined {
  const normalized = projectName
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '-')
    .replace(/^-+/, '')
    .replace(/-+$/, '')

  return normalized.length > 0 ? normalized : undefined
}

function validateForm(): boolean {
  fieldErrors.projectName = undefined
  fieldErrors.releaseId = undefined

  if (!form.projectName.trim()) {
    fieldErrors.projectName = 'Project Name is required.'
  }

  const releaseId = form.releaseId.trim()
  if (!releaseId) {
    fieldErrors.releaseId = 'Workflow Identifier is required.'
  } else {
    const match = releaseId.match(RELEASE_IDENTIFIER_PATTERN)
    if (!match) {
      fieldErrors.releaseId = 'Use format xxx-sit-01 / xxx-uat-01 / xxx-prod-01.'
    } else if ((match.groups?.stage ?? '').toUpperCase() !== form.stage) {
      fieldErrors.releaseId = `Workflow Identifier must match the selected stage ${form.stage}.`
    }
  }

  return !fieldErrors.projectName && !fieldErrors.releaseId
}

async function submit() {
  attemptedSubmit.value = true
  error.value = ''
  if (!validateForm()) return
  if (!canSubmit.value) return

  saving.value = true

  try {
    const result = await createRundownFromTemplate({
      templateId: props.template.id,
      templateName: props.template.name,
      projectId: deriveProjectId(form.projectName),
      projectName: form.projectName.trim(),
      stage: form.stage,
      releaseId: form.releaseId.trim(),
      application: form.application.trim() || undefined,
      snowGroup: form.snowGroup.trim() || undefined,
      agent: form.agent.trim() || undefined,
      site: form.site.trim() || undefined,
      owner: form.owner.trim() || undefined,
      estimatedRemainingMinutes: estimatedRemainingMinutes.value || undefined,
      tasks: props.template.tasks.map((task) => ({
        category: task.category,
        taskName: task.taskName,
        step: task.step,
        stepName: task.stepName,
        type: task.type,
        critical: task.critical,
        owner: task.owner,
        estDurationMinutes: parseDurationToMinutes(task.estDuration),
        dependencies: task.dependencies,
      })),
    })

    emit('created', result)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to create rundown from template'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">Create Rundown</span>
        <button class="modal-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="template-summary-card">
          <div class="template-summary-eyebrow">Template</div>
          <div class="template-summary-title">{{ template.name }}</div>
          <div class="template-summary-meta">
            {{ template.tasks.length }} task{{ template.tasks.length === 1 ? '' : 's' }} ·
            {{ template.estDuration }}
          </div>
        </div>

        <div v-if="error" class="alert alert-error">{{ error }}</div>
        <div v-if="template.tasks.length === 0" class="alert alert-info">
          Add at least one task before creating a workflow rundown from this template.
        </div>

        <div class="form-group">
          <label class="form-label">Project Name <span class="required">*</span></label>
          <input
            v-model="form.projectName"
            type="text"
            class="form-control"
            placeholder="e.g. AMH HCC"
            :class="{ 'input-error': fieldErrors.projectName }"
          />
          <div class="field-hint">
            This becomes the workflow name shown in the summary and detail pages.
          </div>
          <div v-if="attemptedSubmit && fieldErrors.projectName" class="field-error">
            {{ fieldErrors.projectName }}
          </div>
        </div>

        <div class="create-grid">
          <div class="form-group">
            <label class="form-label">Stage</label>
            <select v-model="form.stage" class="form-control">
              <option value="SIT">SIT</option>
              <option value="UAT">UAT</option>
              <option value="PROD">PROD</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">Workflow Identifier <span class="required">*</span></label>
            <input
              v-model="form.releaseId"
              type="text"
              class="form-control"
              :class="{ 'input-error': attemptedSubmit && !!fieldErrors.releaseId }"
              :placeholder="releaseIdentifierPlaceholder"
            />
            <div class="field-hint">Format: `xxx-sit-01` / `xxx-uat-01` / `xxx-prod-01`</div>
            <div v-if="attemptedSubmit && fieldErrors.releaseId" class="field-error">
              {{ fieldErrors.releaseId }}
            </div>
          </div>
        </div>

        <div class="create-grid">
          <div class="form-group">
            <label class="form-label">Application</label>
            <input v-model="form.application" type="text" class="form-control" />
          </div>

          <div class="form-group">
            <label class="form-label">SNOW Group</label>
            <input v-model="form.snowGroup" type="text" class="form-control" />
          </div>
        </div>

        <div class="create-grid">
          <div class="form-group">
            <label class="form-label">Agent</label>
            <input v-model="form.agent" type="text" class="form-control" />
          </div>

          <div class="form-group">
            <label class="form-label">Site</label>
            <input v-model="form.site" type="text" class="form-control" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Rundown Owner</label>
          <input v-model="form.owner" type="text" class="form-control" />
        </div>

        <div class="scope-preview">
          <div class="scope-preview-label">Estimated Remaining</div>
          <div class="scope-preview-value">
            {{ estimatedRemainingMinutes > 0 ? `${estimatedRemainingMinutes} min` : template.estDuration }}
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" :disabled="saving" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" :disabled="!canSubmit" @click="submit">
          <span v-if="saving" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ saving ? 'Creating...' : 'Create Rundown' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.required {
  color: #dc2626;
}

.input-error {
  border-color: #ef4444 !important;
}

.field-error {
  font-size: 12px;
  color: #dc2626;
  line-height: 1.4;
}

.template-summary-card {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #dbe5f4;
  background: linear-gradient(135deg, rgba(241, 246, 255, 0.96), rgba(250, 253, 255, 0.9));
}

.template-summary-eyebrow {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6b7a97;
  margin-bottom: 4px;
}

.template-summary-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2a44;
}

.template-summary-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #5b6c8f;
}

.create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-hint {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
}

.scope-preview {
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(247, 250, 255, 0.92);
  border: 1px solid #dbe5f4;
}

.scope-preview-label {
  font-size: 12px;
  color: #64748b;
}

.scope-preview-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #1f2a44;
}

@media (max-width: 720px) {
  .create-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
