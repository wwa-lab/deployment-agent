<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Task } from '../types'
import type { TaskDocLink, TaskDocSpec } from '../platform/composables/releaseFlowTypes'

const props = defineProps<{
  task: Task
  taskDocs: TaskDocSpec
  saveTaskDocsFn?: (
    taskId: string,
    docs: { inputs: TaskDocLink[]; outputs: TaskDocLink[] },
  ) => Promise<Task>
}>()

const emit = defineEmits<{ close: []; saved: [task: Task] }>()

const saving = ref(false)
const error = ref('')
const inputs = ref<TaskDocLink[]>([])
const outputs = ref<TaskDocLink[]>([])

const canEdit = computed(() => !!props.saveTaskDocsFn)
const hasSuggestions = computed(
  () => (props.taskDocs.suggestedInputs?.length ?? 0) > 0 || (props.taskDocs.suggestedOutputs?.length ?? 0) > 0,
)

function cloneDocLink(doc: TaskDocLink): TaskDocLink {
  return {
    label: doc.label,
    url: doc.url,
    note: doc.note,
    required: doc.required,
  }
}

function resetForm() {
  inputs.value = props.taskDocs.inputs.map(cloneDocLink)
  outputs.value = props.taskDocs.outputs.map(cloneDocLink)
  error.value = ''
}

watch(
  () => [props.task.id, props.taskDocs],
  () => resetForm(),
  { immediate: true },
)

function addDoc(target: 'inputs' | 'outputs') {
  const collection = target === 'inputs' ? inputs : outputs
  collection.value = [
    ...collection.value,
    {
      label: '',
      url: '',
      note: '',
      required: false,
    },
  ]
}

function removeDoc(target: 'inputs' | 'outputs', index: number) {
  const collection = target === 'inputs' ? inputs : outputs
  collection.value = collection.value.filter((_, currentIndex) => currentIndex !== index)
}

function resetToSuggested() {
  inputs.value = (props.taskDocs.suggestedInputs ?? []).map(cloneDocLink)
  outputs.value = (props.taskDocs.suggestedOutputs ?? []).map(cloneDocLink)
  error.value = ''
}

function sanitizeDocs(docs: TaskDocLink[]): TaskDocLink[] {
  return docs
    .map((doc) => ({
      label: doc.label.trim(),
      url: doc.url.trim(),
      note: doc.note?.trim() || undefined,
      required: !!doc.required,
    }))
    .filter((doc) => doc.label || doc.url)
}

function validateDocs(docs: TaskDocLink[], sectionLabel: string): string | null {
  for (const doc of docs) {
    if (!doc.label) {
      return `${sectionLabel}: label is required for each link.`
    }
    if (!doc.url) {
      return `${sectionLabel}: url is required for each link.`
    }
  }
  return null
}

async function saveDocs() {
  if (!props.saveTaskDocsFn) return

  const normalizedInputs = sanitizeDocs(inputs.value)
  const normalizedOutputs = sanitizeDocs(outputs.value)
  const validationError =
    validateDocs(normalizedInputs, 'Input Docs') ?? validateDocs(normalizedOutputs, 'Output Docs')
  if (validationError) {
    error.value = validationError
    return
  }

  saving.value = true
  error.value = ''
  try {
    const updatedTask = await props.saveTaskDocsFn(props.task.id, {
      inputs: normalizedInputs,
      outputs: normalizedOutputs,
    })
    emit('saved', updatedTask)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to save task docs'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal modal-wide">
      <div class="modal-header">
        <span class="modal-title">Task Docs — {{ task.taskName }}</span>
        <button class="modal-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="task-docs-dialog">
          <div v-if="error" class="alert alert-error">{{ error }}</div>

          <div class="task-docs-header">
            <div class="task-docs-group">
              <div class="task-docs-label">Primary Skill</div>
              <span class="task-doc-skill-chip">
                {{ taskDocs.primarySkill.label }}
              </span>
            </div>

            <div v-if="taskDocs.relatedSkills?.length" class="task-docs-group">
              <div class="task-docs-label">Related Skills</div>
              <div class="task-doc-skill-list">
                <span
                  v-for="skill in taskDocs.relatedSkills"
                  :key="`${skill.role}:${skill.key}`"
                  class="task-doc-skill-chip task-doc-skill-chip-secondary"
                >
                  {{ skill.label }}
                </span>
              </div>
            </div>
          </div>

          <div class="task-docs-help">
            We prefill our recommended docs for this skill. Adjust the links below to match the
            actual project markdown files your team should use.
          </div>

          <div class="task-docs-toolbar">
            <button
              v-if="canEdit"
              type="button"
              class="btn btn-secondary btn-sm"
              @click="resetForm"
            >
              Revert Changes
            </button>
            <button
              v-if="canEdit && hasSuggestions"
              type="button"
              class="btn btn-secondary btn-sm"
              @click="resetToSuggested"
            >
              Reset to Suggested Docs
            </button>
          </div>

          <div class="task-docs-grid">
            <div class="task-docs-card">
              <div class="task-docs-card-header">
                <div class="task-docs-title">Input Docs</div>
                <button
                  v-if="canEdit"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  @click="addDoc('inputs')"
                >
                  Add Link
                </button>
              </div>

              <div v-if="inputs.length === 0" class="task-docs-empty">No input docs configured.</div>

              <div
                v-for="(doc, index) in inputs"
                :key="`input:${index}`"
                class="task-doc-editor"
              >
                <div class="task-doc-editor-top">
                  <label class="task-doc-checkbox">
                    <input v-model="doc.required" type="checkbox" :disabled="!canEdit" />
                    <span>Required</span>
                  </label>
                  <button
                    v-if="canEdit"
                    type="button"
                    class="btn-link-danger"
                    @click="removeDoc('inputs', index)"
                  >
                    Remove
                  </button>
                </div>

                <div class="form-group compact">
                  <label class="form-label">Label</label>
                  <input v-model="doc.label" class="form-control" :disabled="!canEdit" />
                </div>

                <div class="form-group compact">
                  <label class="form-label">GitHub Link</label>
                  <input v-model="doc.url" class="form-control" :disabled="!canEdit" />
                </div>

                <div class="form-group compact">
                  <label class="form-label">Note</label>
                  <textarea v-model="doc.note" rows="2" class="form-control" :disabled="!canEdit" />
                </div>
              </div>
            </div>

            <div class="task-docs-card">
              <div class="task-docs-card-header">
                <div class="task-docs-title">Output Docs</div>
                <button
                  v-if="canEdit"
                  type="button"
                  class="btn btn-secondary btn-sm"
                  @click="addDoc('outputs')"
                >
                  Add Link
                </button>
              </div>

              <div v-if="outputs.length === 0" class="task-docs-empty">No output docs configured.</div>

              <div
                v-for="(doc, index) in outputs"
                :key="`output:${index}`"
                class="task-doc-editor"
              >
                <div class="task-doc-editor-top">
                  <label class="task-doc-checkbox">
                    <input v-model="doc.required" type="checkbox" :disabled="!canEdit" />
                    <span>Required</span>
                  </label>
                  <button
                    v-if="canEdit"
                    type="button"
                    class="btn-link-danger"
                    @click="removeDoc('outputs', index)"
                  >
                    Remove
                  </button>
                </div>

                <div class="form-group compact">
                  <label class="form-label">Label</label>
                  <input v-model="doc.label" class="form-control" :disabled="!canEdit" />
                </div>

                <div class="form-group compact">
                  <label class="form-label">GitHub Link</label>
                  <input v-model="doc.url" class="form-control" :disabled="!canEdit" />
                </div>

                <div class="form-group compact">
                  <label class="form-label">Note</label>
                  <textarea v-model="doc.note" rows="2" class="form-control" :disabled="!canEdit" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button
          v-if="canEdit"
          class="btn btn-primary"
          :disabled="saving"
          @click="saveDocs"
        >
          {{ saving ? 'Saving...' : 'Save Task Docs' }}
        </button>
        <button class="btn btn-secondary" @click="emit('close')">Close</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-docs-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-docs-header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
}

.task-docs-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-docs-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.task-doc-skill-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.task-doc-skill-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.task-doc-skill-chip-secondary {
  background: #eff6ff;
  color: #2563eb;
}

.task-docs-help {
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.task-docs-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.task-docs-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.task-docs-card {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.task-docs-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.task-docs-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.task-docs-empty {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.task-doc-editor + .task-doc-editor {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.task-doc-editor-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.task-doc-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.btn-link-danger {
  border: 0;
  background: transparent;
  padding: 0;
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-link-danger:hover {
  text-decoration: underline;
}

.form-group.compact + .form-group.compact {
  margin-top: 10px;
}

@media (max-width: 720px) {
  .task-docs-grid {
    grid-template-columns: 1fr;
  }
}
</style>
