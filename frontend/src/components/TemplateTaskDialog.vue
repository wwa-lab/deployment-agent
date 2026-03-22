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

const form = reactive({
  category: props.task?.category ?? props.activityCategories[0] ?? 'release preparation',
  taskName: props.task?.taskName ?? '',
  step: props.task?.step ?? props.nextStep,
  stepName: props.task?.stepName ?? '',
  type: props.task?.type ?? 'MANUAL',
  owner: props.task?.owner ?? props.defaultOwner,
  estDurationMinutes: parseDurationToMinutes(props.task?.estDuration),
  dependencies: props.task?.dependencies ?? '',
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
      owner: form.owner.trim(),
      estDurationMinutes: form.estDurationMinutes,
      dependencies: form.dependencies.trim() || undefined,
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

          <div class="form-row">
            <label class="form-label">Dependencies</label>
            <select v-model="form.dependencies" class="form-control">
              <option value="">No dependency</option>
              <option v-for="name in dependencyOptions" :key="name" :value="name">
                {{ name }}
              </option>
            </select>
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
  background: #f8fafc;
  color: #334155;
  font-size: 20px;
  line-height: 1;
}

.field-hint {
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 760px) {
  .modal-wide {
    width: calc(100vw - 24px);
  }

  .task-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
