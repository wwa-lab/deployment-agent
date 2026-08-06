<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type {
  DirectoryGroup,
  DirectoryLink,
  DirectoryLinkIconKey,
  DirectoryLinkKind,
  DirectoryScope,
  SdlcStageKey,
} from '../types'
import { DIRECTORY_LINK_ICON_KEYS } from '../platform/resourceCenter/linkPresentation'

const props = defineProps<{
  entityType: 'scope' | 'group' | 'link'
  mode: 'create' | 'edit'
  scopes: DirectoryScope[]
  entity?: DirectoryScope | DirectoryGroup | DirectoryLink | null
  parentScopeKey?: string
  parentGroupKey?: string
  presetKind?: DirectoryLinkKind
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [payload: Record<string, unknown>]
}>()

const KEY_PATTERN = /^[a-z0-9][a-z0-9_-]{1,31}$/
const WORKSPACE_URL_PATTERN = /^\/wwa\/[A-Za-z0-9._~\-/]*$/
const HTTP_URL_PATTERN = /^https?:\/\/.+/i
const DENIED_URL_PATTERN = /^(javascript:|data:|vbscript:|\/\/)/i

const STAGE_KEYS: SdlcStageKey[] = [
  'planning',
  'estimation',
  'discovery',
  'build',
  'testing',
  'deployment',
  'maintenance',
]

const isCreate = computed(() => props.mode === 'create')
const isScope = computed(() => props.entityType === 'scope')
const isGroup = computed(() => props.entityType === 'group')
const isLink = computed(() => props.entityType === 'link')

const scopeEntity = computed(() => (props.entityType === 'scope' ? (props.entity as DirectoryScope | null) : null))
const groupEntity = computed(() => (props.entityType === 'group' ? (props.entity as DirectoryGroup | null) : null))
const linkEntity = computed(() => (props.entityType === 'link' ? (props.entity as DirectoryLink | null) : null))

const localError = ref('')

const form = reactive({
  key: '',
  title: '',
  description: '',
  layout: 'buckets' as 'stage-strip' | 'buckets',
  enabled: true,
  sortOrder: '' as string | number,
  type: 'bucket' as 'stage' | 'bucket',
  stageKey: '' as SdlcStageKey | '',
  stageOrder: '' as string | number,
  agentName: '',
  url: '',
  kind: 'docs' as DirectoryLinkKind,
  kindLabel: '',
  iconKey: '' as DirectoryLinkIconKey | '',
  targetScopeKey: '',
  targetGroupKey: '',
})

const groupOptions = computed(() => {
  const scopeKey = form.targetScopeKey || props.parentScopeKey || props.scopes[0]?.key || ''
  const scope = props.scopes.find((candidate) => candidate.key === scopeKey)
  return scope?.groups ?? []
})

function resetForm() {
  localError.value = ''

  if (isScope.value) {
    form.key = scopeEntity.value?.key ?? ''
    form.title = scopeEntity.value?.title ?? ''
    form.description = scopeEntity.value?.description ?? ''
    form.layout = scopeEntity.value?.layout ?? 'buckets'
    form.enabled = scopeEntity.value?.enabled ?? true
    form.sortOrder = scopeEntity.value?.sortOrder ?? ''
    return
  }

  if (isGroup.value) {
    form.key = groupEntity.value?.key ?? ''
    form.title = groupEntity.value?.title ?? ''
    form.description = groupEntity.value?.description ?? ''
    form.type = groupEntity.value?.type ?? 'bucket'
    form.stageKey = groupEntity.value?.stageKey ?? ''
    form.stageOrder = groupEntity.value?.stageOrder ?? ''
    form.agentName = groupEntity.value?.agentName ?? ''
    form.enabled = groupEntity.value?.enabled ?? true
    form.sortOrder = groupEntity.value?.sortOrder ?? ''
    return
  }

  form.title = linkEntity.value?.title ?? ''
  form.description = linkEntity.value?.description ?? ''
  form.url = linkEntity.value?.url ?? ''
  form.kind = linkEntity.value?.kind ?? props.presetKind ?? 'docs'
  form.kindLabel = linkEntity.value?.kindLabel ?? ''
  form.iconKey = linkEntity.value?.iconKey ?? ''
  form.enabled = linkEntity.value?.enabled ?? true
  form.sortOrder = linkEntity.value?.sortOrder ?? ''
  form.targetScopeKey = props.parentScopeKey ?? props.scopes[0]?.key ?? ''
  form.targetGroupKey = props.parentGroupKey ?? groupOptions.value[0]?.key ?? ''
}

watch(
  () => [props.entityType, props.mode, props.entity, props.parentScopeKey, props.parentGroupKey, props.presetKind],
  () => resetForm(),
  { immediate: true },
)

watch(
  () => form.targetScopeKey,
  () => {
    if (!groupOptions.value.some((group) => group.key === form.targetGroupKey)) {
      form.targetGroupKey = groupOptions.value[0]?.key ?? ''
    }
  },
)

function normalizeKey(value: string) {
  return value.trim().toLowerCase()
}

function parseSortOrder(value: string | number): number | undefined {
  if (value === '' || value == null) {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function validateUrl(url: string, kind: DirectoryLinkKind): string | null {
  const trimmed = url.trim()
  if (!trimmed) {
    return 'URL is required.'
  }
  if (DENIED_URL_PATTERN.test(trimmed)) {
    return 'URL uses a disallowed scheme or format.'
  }
  if (kind === 'workspace') {
    if (!WORKSPACE_URL_PATTERN.test(trimmed)) {
      return 'Workspace links must use an in-Hub path starting with /wwa/.'
    }
    return null
  }
  if (!HTTP_URL_PATTERN.test(trimmed)) {
    return 'External links must use http or https.'
  }
  return null
}

function submit() {
  localError.value = ''

  if (isScope.value) {
    const key = normalizeKey(form.key)
    if (!KEY_PATTERN.test(key)) {
      localError.value = 'Scope key must match the required pattern.'
      return
    }
    if (!form.title.trim()) {
      localError.value = 'Title is required.'
      return
    }
    if (form.title.trim().length > 120) {
      localError.value = 'Title must be 120 characters or fewer.'
      return
    }
    if (form.description.trim().length > 240) {
      localError.value = 'Description must be 240 characters or fewer.'
      return
    }
    const sortOrder = parseSortOrder(form.sortOrder)
    if (sortOrder != null && (sortOrder < 0 || sortOrder > 9999)) {
      localError.value = 'Sort order must be between 0 and 9999.'
      return
    }
    emit('save', {
      key,
      title: form.title.trim(),
      description: form.description.trim(),
      layout: form.layout,
      enabled: form.enabled,
      sortOrder,
    })
    return
  }

  if (isGroup.value) {
    const key = normalizeKey(form.key)
    if (!KEY_PATTERN.test(key)) {
      localError.value = 'Group key must match the required pattern.'
      return
    }
    if (!form.title.trim()) {
      localError.value = 'Title is required.'
      return
    }
    if (form.title.trim().length > 120) {
      localError.value = 'Title must be 120 characters or fewer.'
      return
    }
    if (form.description.trim().length > 240) {
      localError.value = 'Description must be 240 characters or fewer.'
      return
    }
    if (form.type === 'stage') {
      if (!form.stageKey) {
        localError.value = 'Stage key is required for stage groups.'
        return
      }
      if (key !== form.stageKey) {
        localError.value = 'Stage group key must match the stage key.'
        return
      }
      const stageOrder = parseSortOrder(form.stageOrder)
      if (stageOrder == null || stageOrder < 1 || stageOrder > 99) {
        localError.value = 'Stage order must be between 1 and 99.'
        return
      }
    } else if (form.stageKey || form.stageOrder !== '') {
      localError.value = 'Bucket groups cannot include stage fields.'
      return
    }
    const sortOrder = parseSortOrder(form.sortOrder)
    if (sortOrder != null && (sortOrder < 0 || sortOrder > 9999)) {
      localError.value = 'Sort order must be between 0 and 9999.'
      return
    }
    emit('save', {
      key,
      title: form.title.trim(),
      description: form.description.trim(),
      type: form.type,
      stageKey: form.type === 'stage' ? form.stageKey : undefined,
      stageOrder: form.type === 'stage' ? parseSortOrder(form.stageOrder) : undefined,
      agentName: form.agentName.trim() || undefined,
      enabled: form.enabled,
      sortOrder,
    })
    return
  }

  if (!form.title.trim()) {
    localError.value = 'Title is required.'
    return
  }
  if (form.title.trim().length > 120) {
    localError.value = 'Title must be 120 characters or fewer.'
    return
  }
  if (form.description.trim().length > 240) {
    localError.value = 'Description must be 240 characters or fewer.'
    return
  }
  const urlError = validateUrl(form.url, form.kind)
  if (urlError) {
    localError.value = urlError
    return
  }
  if (form.kindLabel.trim().length > 24) {
    localError.value = 'Kind label must be 24 characters or fewer.'
    return
  }
  const sortOrder = parseSortOrder(form.sortOrder)
  if (sortOrder != null && (sortOrder < 0 || sortOrder > 9999)) {
    localError.value = 'Sort order must be between 0 and 9999.'
    return
  }
  const hasTargetScope = Boolean(form.targetScopeKey)
  const hasTargetGroup = Boolean(form.targetGroupKey)
  if (hasTargetScope !== hasTargetGroup) {
    localError.value = 'Target scope and group must be supplied together.'
    return
  }

  emit('save', {
    title: form.title.trim(),
    description: form.description.trim(),
    url: form.url.trim(),
    kind: form.kind,
    kindLabel: form.kindLabel.trim() || undefined,
    iconKey: form.iconKey || undefined,
    enabled: form.enabled,
    sortOrder,
    targetScopeKey: hasTargetScope ? form.targetScopeKey : undefined,
    targetGroupKey: hasTargetGroup ? form.targetGroupKey : undefined,
  })
}

const dialogTitle = computed(() => {
  const action = isCreate.value ? 'Add' : 'Edit'
  const label = isScope.value ? 'Scope' : isGroup.value ? 'Group' : 'Link'
  return `${action} ${label}`
})
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

        <template v-if="isScope">
          <div class="form-group">
            <label class="form-label">Key <span class="required">*</span></label>
            <input
              v-model="form.key"
              class="form-control"
              type="text"
              :disabled="!isCreate"
              placeholder="e.g. security"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Title <span class="required">*</span></label>
            <input v-model="form.title" class="form-control" type="text" maxlength="120" />
          </div>
          <div class="form-group">
            <label class="form-label">Description</label>
            <textarea v-model="form.description" class="form-control" rows="3" maxlength="240" />
          </div>
          <div class="form-group">
            <label class="form-label">Layout <span class="required">*</span></label>
            <select v-model="form.layout" class="form-control">
              <option value="buckets">Buckets</option>
              <option value="stage-strip">Stage strip</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Sort order</label>
            <input v-model="form.sortOrder" class="form-control" type="number" min="0" max="9999" />
          </div>
          <label class="checkbox-row">
            <input v-model="form.enabled" type="checkbox" />
            Enabled
          </label>
          <p v-if="scopeEntity?.system && !form.enabled" class="field-hint warning">
            Disabling a system scope hides this section for all users.
          </p>
        </template>

        <template v-else-if="isGroup">
          <div class="form-group">
            <label class="form-label">Key <span class="required">*</span></label>
            <input
              v-model="form.key"
              class="form-control"
              type="text"
              :disabled="!isCreate"
              placeholder="e.g. scanners"
            />
          </div>
          <div class="form-group">
            <label class="form-label">Title <span class="required">*</span></label>
            <input v-model="form.title" class="form-control" type="text" maxlength="120" />
          </div>
          <div class="form-group">
            <label class="form-label">Description</label>
            <textarea v-model="form.description" class="form-control" rows="3" maxlength="240" />
          </div>
          <div class="form-group">
            <label class="form-label">Type <span class="required">*</span></label>
            <select v-model="form.type" class="form-control">
              <option value="bucket">Bucket</option>
              <option value="stage">Stage</option>
            </select>
          </div>
          <template v-if="form.type === 'stage'">
            <div class="form-group">
              <label class="form-label">Stage key <span class="required">*</span></label>
              <select v-model="form.stageKey" class="form-control" :disabled="!isCreate">
                <option value="">Select stage</option>
                <option v-for="stage in STAGE_KEYS" :key="stage" :value="stage">
                  {{ stage }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Stage order <span class="required">*</span></label>
              <input v-model="form.stageOrder" class="form-control" type="number" min="1" max="99" />
            </div>
          </template>
          <div class="form-group">
            <label class="form-label">Agent name</label>
            <input v-model="form.agentName" class="form-control" type="text" placeholder="e.g. Deployment Agent" />
          </div>
          <div class="form-group">
            <label class="form-label">Sort order</label>
            <input v-model="form.sortOrder" class="form-control" type="number" min="0" max="9999" />
          </div>
          <label class="checkbox-row">
            <input v-model="form.enabled" type="checkbox" />
            Enabled
          </label>
        </template>

        <template v-else>
          <div class="form-group">
            <label class="form-label">Title <span class="required">*</span></label>
            <input v-model="form.title" class="form-control" type="text" maxlength="120" />
          </div>
          <div class="form-group">
            <label class="form-label">Description</label>
            <textarea v-model="form.description" class="form-control" rows="3" maxlength="240" />
          </div>
          <div class="form-group">
            <label class="form-label">URL <span class="required">*</span></label>
            <input v-model="form.url" class="form-control" type="text" placeholder="https://... or /wwa/..." />
          </div>
          <div class="form-group">
            <label class="form-label">Kind <span class="required">*</span></label>
            <select v-model="form.kind" class="form-control">
              <option value="docs">Docs</option>
              <option value="tool">Tool</option>
              <option value="workspace">Workspace</option>
              <option value="repo">Repo</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Kind label</label>
            <input v-model="form.kindLabel" class="form-control" type="text" maxlength="24" />
          </div>
          <div class="form-group">
            <label class="form-label">Icon</label>
            <select v-model="form.iconKey" class="form-control">
              <option value="">Default (letter badge)</option>
              <option v-for="key in DIRECTORY_LINK_ICON_KEYS" :key="key" :value="key">
                {{ key }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Target scope</label>
            <select v-model="form.targetScopeKey" class="form-control">
              <option v-for="scope in scopes" :key="scope.key" :value="scope.key">
                {{ scope.title }} ({{ scope.key }})
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Target group</label>
            <select v-model="form.targetGroupKey" class="form-control">
              <option v-for="group in groupOptions" :key="group.key" :value="group.key">
                {{ group.title }} ({{ group.key }})
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Sort order</label>
            <input v-model="form.sortOrder" class="form-control" type="number" min="0" max="9999" />
          </div>
          <label class="checkbox-row">
            <input v-model="form.enabled" type="checkbox" />
            Enabled
          </label>
        </template>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submit">
          {{ saving ? 'Saving...' : isCreate ? 'Create' : 'Save Changes' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.required {
  color: #ef4444;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.field-hint.warning {
  color: #b45309;
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--color-text-secondary);
}
</style>
