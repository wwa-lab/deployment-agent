<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { TemplateTask, TemplateTaskDraft } from '../types'

const props = defineProps<{
  task?: TemplateTask | null
  activityCategories: string[]
  existingTaskNames: string[]
  defaultOwner: string
  nextStep: number
}>()

const emit = defineEmits<{
  close: []
  save: [draft: TemplateTaskDraft]
}>()

function parseDurationToMinutes(value?: string): number {
  if (!value) return 15

  const hoursMatch = value.match(/(\d+)\s*h/i)
  const minutesMatch = value.match(/(\d+)\s*m/i)

  const hours = hoursMatch ? Number.parseInt(hoursMatch[1], 10) : 0
  const minutes = minutesMatch ? Number.parseInt(minutesMatch[1], 10) : 0
  const total = hours * 60 + minutes

  return total > 0 ? total : 15
}

function parseDependencyList(value?: string): string[] {
  if (!value) return []

  return Array.from(
    new Set(
      value
        .split(/[\n,;]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  )
}

const form = reactive({
  category: props.task?.category ?? props.activityCategories[0] ?? 'release preparation',
  taskName: props.task?.taskName ?? '',
  step: props.task?.step ?? props.nextStep,
  stepName: props.task?.stepName ?? '',
  type: props.task?.type ?? 'MANUAL',
  critical: props.task?.critical ?? false,
  owner: props.task?.owner ?? props.defaultOwner,
  estDurationMinutes: parseDurationToMinutes(props.task?.estDuration),
  dependencies: parseDependencyList(props.task?.dependencies),
})

const saving = ref(false)
const error = ref('')

const dialogTitle = computed(() => (props.task ? 'Edit Template Task' : 'Add Template Task'))

const durationLabel = computed(() => {
  const totalMinutes = form.estDurationMinutes
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (hours === 0) return `${minutes}m`
  if (minutes === 0) return `${hours}h`
  return `${hours}h ${minutes}m`
})

const dependencyOptions = computed(() =>
  props.existingTaskNames.filter((name) => name !== props.task?.taskName),
)

const selectedDependencyLabel = computed(() => {
  if (form.dependencies.length === 0) return 'No dependency selected.'
  if (form.dependencies.length === 1) return `Depends on 1 task: ${form.dependencies[0]}.`
  return `Depends on ${form.dependencies.length} tasks.`
})

const canSave = computed(
  () =>
    form.category.trim().length > 0 &&
    form.taskName.trim().length > 0 &&
    form.step > 0 &&
    form.stepName.trim().length > 0 &&
    form.owner.trim().length > 0 &&
    form.estDurationMinutes > 0,
)

function changeDuration(delta: number) {
  form.estDurationMinutes = Math.max(5, form.estDurationMinutes + delta)
}

function toggleDependency(name: string, checked: boolean) {
  if (checked) {
    form.dependencies = [...form.dependencies, name]
    return
  }

  form.dependencies = form.dependencies.filter((dependency) => dependency !== name)
}

async function submit() {
  if (!canSave.value) {
    error.value = 'Complete the required fields before saving this task.'
    return
  }

  saving.value = true
  error.value = ''
  try {
    emit('save', {
      category: form.category.trim(),
      taskName: form.taskName.trim(),
      step: form.step,
      stepName: form.stepName.trim(),
      type: form.type,
      critical: form.critical,
      owner: form.owner.trim(),
      estDurationMinutes: form.estDurationMinutes,
      dependencies: form.dependencies.length > 0 ? form.dependencies.join(', ') : undefined,
    })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal modal-wide">
      <div class="modal-header">
        <span class="modal-title">{{ dialogTitle }}</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="task-form-grid">
          <div class="form-row">
            <label class="form-label">Activity Category <span class="required">*</span></label>
            <select v-model="form.category" class="form-control">
              <option v-for="category in activityCategories" :key="category" :value="category">
                {{ category }}
              </option>
            </select>
          </div>

          <div class="form-row">
            <label class="form-label">Task Name <span class="required">*</span></label>
            <input
              v-model="form.taskName"
              class="form-control"
              type="text"
              placeholder="Enter task name"
            />
          </div>

          <div class="form-row">
            <label class="form-label">Step <span class="required">*</span></label>
            <input v-model.number="form.step" class="form-control" type="number" min="1" />
            <div class="field-hint">Used as ordering. The list will be normalized after save.</div>
          </div>

          <div class="form-row">
            <label class="form-label">Step Name <span class="required">*</span></label>
            <input
              v-model="form.stepName"
              class="form-control"
              type="text"
              placeholder="Enter step name"
            />
          </div>

          <div class="form-row">
            <label class="form-label">Type <span class="required">*</span></label>
            <select v-model="form.type" class="form-control">
              <option value="MANUAL">MANUAL</option>
              <option value="AUTO">AUTO</option>
            </select>
          </div>

          <div class="form-row">
            <label class="form-label">Owner <span class="required">*</span></label>
            <input
              v-model="form.owner"
              class="form-control"
              type="text"
              placeholder="Enter owner"
            />
          </div>

          <div class="form-row">
            <label class="form-label">Critical</label>
            <select v-model="form.critical" class="form-control">
              <option :value="true">Yes (gate)</option>
              <option :value="false">No</option>
            </select>
            <div class="field-hint">
              Critical tasks must be reviewed before the next task can be released.
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">Estimated Duration <span class="required">*</span></label>
            <div class="duration-input">
              <button class="stepper-btn" type="button" @click="changeDuration(-5)">−</button>
              <input
                v-model.number="form.estDurationMinutes"
                class="form-control duration-control"
                type="number"
                min="5"
                step="5"
              />
              <button class="stepper-btn" type="button" @click="changeDuration(5)">+</button>
            </div>
            <div class="field-hint">minutes ({{ durationLabel }})</div>
          </div>

          <div class="form-row form-row-wide">
            <label class="form-label">Dependencies</label>
            <div class="field-hint">
              Select the tasks that must finish before this task can start.
            </div>
            <div v-if="dependencyOptions.length === 0" class="dependency-empty-state">
              No other tasks are available yet. Save this task first, then come back to wire dependencies.
            </div>
            <div v-else class="dependency-option-grid">
              <label
                v-for="name in dependencyOptions"
                :key="name"
                class="dependency-option"
                :class="{ selected: form.dependencies.includes(name) }"
              >
                <input
                  class="dependency-checkbox"
                  type="checkbox"
                  :checked="form.dependencies.includes(name)"
                  @change="toggleDependency(name, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ name }}</span>
              </label>
            </div>
            <div class="field-hint">{{ selectedDependencyLabel }}</div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submit">
          {{ saving ? 'Saving...' : 'Save Task' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-wide {
  width: 760px;
}

.task-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-row-wide {
  grid-column: 1 / -1;
}

.required {
  color: #dc2626;
}

.duration-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.duration-control {
  flex: 1;
  text-align: center;
}

.stepper-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: var(--color-surface-secondary);
  color: var(--color-text-secondary);
  font-size: 20px;
  line-height: 1;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

.dependency-option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.dependency-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #dbe2ea;
  border-radius: 10px;
  background: var(--color-surface-secondary);
  color: var(--color-text-secondary);
}

.dependency-option.selected {
  border-color: #3b82f6;
  background: #eff6ff;
}

.dependency-checkbox {
  width: 14px;
  height: 14px;
}

.dependency-empty-state {
  padding: 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: var(--color-surface-secondary);
  font-size: 12px;
  color: var(--color-text-muted);
}

@media (max-width: 760px) {
  .modal-wide {
    width: calc(100vw - 24px);
  }

  .task-form-grid {
    grid-template-columns: 1fr;
  }

  .dependency-option-grid {
    grid-template-columns: 1fr;
  }
}
</style>
