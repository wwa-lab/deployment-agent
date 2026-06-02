<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSkillHubSkill,
  createSkillHubVersion,
  getSkillHubSkill,
  getSkillHubVersion,
  listSkillHubSkills,
  updateSkillHubSkill,
  type SkillHubSkill,
  type SkillHubSkillPayload,
  type SkillHubVersionDetail,
  type SkillHubVersionPayload,
  type SkillStatus,
} from '../api/skillHub'
import { useUserStore } from '../stores/user'

const STATUS_OPTIONS: Array<{ value: SkillStatus; label: string }> = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'DEPRECATED', label: 'Deprecated' },
  { value: 'ARCHIVED', label: 'Archived' },
]

const userStore = useUserStore()
const skills = ref<SkillHubSkill[]>([])
const selectedSkill = ref<SkillHubSkill | null>(null)
const selectedVersion = ref<SkillHubVersionDetail | null>(null)
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const versionLoading = ref(false)
const saving = ref(false)
const versionSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const versionError = ref('')
const query = ref('')
const categoryFilter = ref('')
const statusFilter = ref<SkillStatus | ''>('')
const versionFilter = ref('')
const isDialogOpen = ref(false)
const isVersionDialogOpen = ref(false)
const isDetailDialogOpen = ref(false)
const editingSkill = ref<SkillHubSkill | null>(null)

const form = reactive({
  name: '',
  description: '',
  category: '',
  tagsText: '',
  owner: '',
  status: 'DRAFT' as SkillStatus,
  currentVersion: '',
  versionNotes: '',
  content: '',
})

const versionForm = reactive({
  version: '',
  versionNotes: '',
  content: '',
})

const canMutate = computed(() => userStore.isAuthenticated && !userStore.isGuest)
const categories = computed(() =>
  [...new Set(skills.value.map((skill) => skill.category).filter(Boolean))].sort((a, b) => a.localeCompare(b)),
)
const visibleSkills = computed(() => {
  const normalizedVersion = versionFilter.value.trim().toLowerCase()
  if (!normalizedVersion) return skills.value
  return skills.value.filter((skill) => skill.currentVersion.toLowerCase().includes(normalizedVersion))
})

onMounted(() => {
  void loadSkills()
})

async function loadSkills() {
  loading.value = true
  pageError.value = ''
  try {
    const response = await listSkillHubSkills({
      query: query.value || undefined,
      category: categoryFilter.value || undefined,
      status: statusFilter.value || undefined,
      page: 0,
      size: 50,
    })
    skills.value = response.data
    total.value = response.total
    const selectedId = selectedSkill.value?.id
    const nextSelected = selectedId
      ? skills.value.find((skill) => skill.id === selectedId) ?? skills.value[0]
      : skills.value[0]
    if (nextSelected) {
      await selectSkill(nextSelected.id, false)
    } else {
      selectedSkill.value = null
      selectedVersion.value = null
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : 'Failed to load Skill Hub catalog'
  } finally {
    loading.value = false
  }
}

async function selectSkill(id: string, openDialog = true) {
  detailLoading.value = true
  pageError.value = ''
  try {
    selectedSkill.value = await getSkillHubSkill(id)
    await selectInitialVersion()
    if (openDialog) {
      isDetailDialogOpen.value = true
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : 'Failed to load skill details'
  } finally {
    detailLoading.value = false
  }
}

function closeDetailDialog() {
  isDetailDialogOpen.value = false
}

async function selectInitialVersion() {
  if (!selectedSkill.value) {
    selectedVersion.value = null
    return
  }
  const currentVersionId = selectedSkill.value.currentVersionId ?? selectedSkill.value.versions[0]?.id
  if (currentVersionId) {
    await selectVersion(currentVersionId)
    return
  }
  selectedVersion.value = null
}

async function selectVersion(versionId: string) {
  if (!selectedSkill.value) return
  versionLoading.value = true
  pageError.value = ''
  try {
    selectedVersion.value = await getSkillHubVersion(selectedSkill.value.id, versionId)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : 'Failed to load skill version'
  } finally {
    versionLoading.value = false
  }
}

function openCreateDialog() {
  if (!canMutate.value) return
  editingSkill.value = null
  Object.assign(form, {
    name: '',
    description: '',
    category: '',
    tagsText: '',
    owner: userStore.displayName || userStore.userId || '',
    status: 'DRAFT',
    currentVersion: '',
    versionNotes: '',
    content: '',
  })
  formError.value = ''
  isDialogOpen.value = true
}

function openEditDialog(skill: SkillHubSkill) {
  if (!canMutate.value) return
  editingSkill.value = skill
  Object.assign(form, {
    name: skill.name,
    description: skill.description,
    category: skill.category,
    tagsText: skill.tags.join(', '),
    owner: skill.owner,
    status: skill.status,
    currentVersion: skill.currentVersion,
    versionNotes: skill.versionNotes ?? '',
    content: skill.currentContentSnapshot ?? '',
  })
  formError.value = ''
  isDialogOpen.value = true
}

function openVersionDialog(skill: SkillHubSkill) {
  if (!canMutate.value) return
  Object.assign(versionForm, {
    version: '',
    versionNotes: '',
    content: skill.currentContentSnapshot ?? '',
  })
  versionError.value = ''
  isVersionDialogOpen.value = true
}

function closeDialog() {
  isDialogOpen.value = false
}

function closeVersionDialog() {
  isVersionDialogOpen.value = false
}

async function saveSkill() {
  if (!canMutate.value) return
  const payload = buildPayload()
  if (!payload) return

  saving.value = true
  formError.value = ''
  try {
    const saved = editingSkill.value
      ? await updateSkillHubSkill(editingSkill.value.id, payload)
      : await createSkillHubSkill(payload)
    isDialogOpen.value = false
    await loadSkills()
    await selectSkill(saved.id)
  } catch (error) {
    formError.value = error instanceof Error ? error.message : 'Failed to save skill'
  } finally {
    saving.value = false
  }
}

async function saveVersion() {
  if (!canMutate.value || !selectedSkill.value) return
  const payload = buildVersionPayload()
  if (!payload) return

  versionSaving.value = true
  versionError.value = ''
  try {
    await createSkillHubVersion(selectedSkill.value.id, payload)
    isVersionDialogOpen.value = false
    await loadSkills()
    await selectSkill(selectedSkill.value.id)
  } catch (error) {
    versionError.value = error instanceof Error ? error.message : 'Failed to create version'
  } finally {
    versionSaving.value = false
  }
}

function buildPayload(): SkillHubSkillPayload | null {
  if (
    !form.name.trim() ||
    !form.description.trim() ||
    !form.category.trim() ||
    !form.owner.trim() ||
    !form.currentVersion.trim() ||
    (!editingSkill.value && !form.content.trim())
  ) {
    formError.value = 'Name, description, category, owner, and current version are required. New skills also need content.'
    return null
  }

  return {
    name: form.name.trim(),
    description: form.description.trim(),
    category: form.category.trim(),
    tags: form.tagsText.split(',').map((tag) => tag.trim()).filter(Boolean),
    owner: form.owner.trim(),
    status: form.status,
    currentVersion: form.currentVersion.trim(),
    versionNotes: form.versionNotes.trim(),
    content: form.content.trim() || undefined,
  }
}

function buildVersionPayload(): SkillHubVersionPayload | null {
  if (!versionForm.version.trim() || !versionForm.content.trim()) {
    versionError.value = 'Version and skill content are required.'
    return null
  }

  return {
    version: versionForm.version.trim(),
    versionNotes: versionForm.versionNotes.trim(),
    content: versionForm.content.trim(),
  }
}

function formatTimestamp(value: string | undefined) {
  if (!value) return 'Not recorded'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return `${parsed.toISOString().slice(0, 16).replace('T', ' ')} UTC`
}

function truncateHash(value: string | undefined) {
  return value ? `${value.slice(0, 10)}...` : 'Not indexed'
}
</script>

<template>
  <div class="skill-hub-market">
    <div class="market-header">
      <div>
        <p class="view-eyebrow">WWA Shared Capability</p>
        <h1>Skill Hub</h1>
        <p>Browse curated skills, inspect project-backed skill files, and track version history.</p>
      </div>
      <div class="header-actions">
        <button class="icon-btn" type="button" :disabled="loading" title="Refresh catalog" @click="loadSkills">
          Refresh
        </button>
        <button class="btn btn-primary btn-sm" type="button" :disabled="!canMutate" @click="openCreateDialog">
          New Skill
        </button>
      </div>
    </div>

    <section class="market-toolbar" aria-label="Skill catalog filters">
      <label>
        <span>Search</span>
        <input
          v-model="query"
          class="form-control"
          type="search"
          placeholder="Name, description, owner, tag"
          @keyup.enter="loadSkills"
        >
      </label>
      <label>
        <span>Category</span>
        <input
          v-model="categoryFilter"
          class="form-control"
          list="skill-hub-categories"
          placeholder="All categories"
          @keyup.enter="loadSkills"
        >
        <datalist id="skill-hub-categories">
          <option v-for="category in categories" :key="category" :value="category" />
        </datalist>
      </label>
      <label>
        <span>Status</span>
        <select v-model="statusFilter" class="form-control" @change="loadSkills">
          <option value="">All statuses</option>
          <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>Version</span>
        <input v-model="versionFilter" class="form-control" type="search" placeholder="Current version">
      </label>
      <button class="btn btn-secondary btn-sm" type="button" :disabled="loading" @click="loadSkills">
        Apply
      </button>
    </section>

    <p v-if="pageError" class="status-message error">{{ pageError }}</p>

    <section class="catalog-area" aria-label="Skill marketplace cards">
      <div class="catalog-meta">
        <strong>{{ visibleSkills.length }} shown</strong>
        <span>{{ total }} total registry entries</span>
        <span v-if="userStore.isGuest" class="readonly-badge">Read-only preview</span>
      </div>

      <div v-if="!loading && visibleSkills.length === 0" class="empty-market">
        <h2>No skills match the current filters.</h2>
        <p>Adjust the filters or create a project-backed skill file.</p>
        <button class="btn btn-primary" type="button" :disabled="!canMutate" @click="openCreateDialog">
          New Skill
        </button>
      </div>

      <div class="skill-card-grid">
        <article
          v-for="skill in visibleSkills"
          :key="skill.id"
          class="skill-card"
          tabindex="0"
          @click="selectSkill(skill.id)"
          @keydown.enter.prevent="selectSkill(skill.id)"
        >
          <div class="card-topline">
            <span class="category-pill">{{ skill.category }}</span>
            <span class="status-badge" :class="skill.status.toLowerCase()">{{ skill.status }}</span>
          </div>
          <h2>{{ skill.name }}</h2>
          <p>{{ skill.description }}</p>
          <div class="tag-list">
            <strong v-for="tag in skill.tags.slice(0, 3)" :key="tag">{{ tag }}</strong>
            <em v-if="skill.tags.length > 3">+{{ skill.tags.length - 3 }}</em>
          </div>
          <div class="card-footer">
            <span>v{{ skill.currentVersion }}</span>
            <span>{{ skill.owner }}</span>
          </div>
          <code>{{ skill.sourcePath }}</code>
        </article>
      </div>
    </section>

    <div v-if="isDetailDialogOpen" class="modal-backdrop" role="presentation" @click.self="closeDetailDialog">
      <section class="skill-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="skill-detail-title">
        <div v-if="detailLoading" class="detail-empty">Loading skill details...</div>
        <div v-else-if="selectedSkill" class="detail-content">
          <div class="detail-header">
            <div>
              <p class="view-eyebrow">Selected Skill</p>
              <h2 id="skill-detail-title">{{ selectedSkill.name }}</h2>
            </div>
            <div class="detail-actions">
              <button class="btn btn-secondary btn-sm" type="button" :disabled="!canMutate" @click="openEditDialog(selectedSkill)">
                Edit
              </button>
              <button class="btn btn-primary btn-sm" type="button" :disabled="!canMutate" @click="openVersionDialog(selectedSkill)">
                Create Version
              </button>
              <button class="btn btn-secondary btn-sm" type="button" @click="closeDetailDialog">
                Close
              </button>
            </div>
          </div>

          <p class="detail-description">{{ selectedSkill.description }}</p>

          <dl class="metadata-grid">
            <div>
              <dt>Source Path</dt>
              <dd><code>{{ selectedSkill.sourcePath }}</code></dd>
            </div>
            <div>
              <dt>Current Version</dt>
              <dd>{{ selectedSkill.currentVersion }}</dd>
            </div>
            <div>
              <dt>Content Hash</dt>
              <dd>{{ truncateHash(selectedSkill.contentSha256) }}</dd>
            </div>
            <div>
              <dt>Last Indexed</dt>
              <dd>{{ formatTimestamp(selectedSkill.lastIndexedAt) }}</dd>
            </div>
          </dl>

          <section class="content-preview" aria-label="Current skill content">
            <div class="panel-heading">
              <div>
                <p class="view-eyebrow">Skill Content</p>
                <h3>{{ selectedVersion ? `Version ${selectedVersion.version}` : 'Current Content Snapshot' }}</h3>
              </div>
              <span>{{ versionLoading ? 'Loading version...' : selectedSkill.contentSourceType }}</span>
            </div>
            <pre>{{ selectedVersion?.contentSnapshot || selectedSkill.currentContentSnapshot || 'No content snapshot recorded yet.' }}</pre>
          </section>

          <section class="version-timeline" aria-label="Skill version history">
            <div class="panel-heading">
              <h3>Version History</h3>
              <span>{{ selectedSkill.versions.length }} snapshots</span>
            </div>
            <ol>
              <li
                v-for="version in selectedSkill.versions"
                :key="version.id"
                :class="{ selected: selectedVersion?.id === version.id }"
                tabindex="0"
                @click="selectVersion(version.id)"
                @keydown.enter.prevent="selectVersion(version.id)"
              >
                <strong>{{ version.version }}</strong>
                <span>{{ formatTimestamp(version.createdAt) }}</span>
                <p>{{ version.versionNotes || 'No version notes recorded.' }}</p>
                <code>{{ version.sourcePath }}</code>
              </li>
            </ol>
          </section>
        </div>
        <div v-else class="detail-empty">
          <h2>No Skill Selected</h2>
          <p>Create the first project-backed skill file or adjust filters.</p>
        </div>
      </section>
    </div>

    <div v-if="isDialogOpen" class="modal-backdrop" role="presentation" @click.self="closeDialog">
      <section class="skill-dialog" role="dialog" aria-modal="true" aria-labelledby="skill-dialog-title">
        <div class="dialog-header">
          <div>
            <p class="view-eyebrow">{{ editingSkill ? 'Edit Skill' : 'New Skill' }}</p>
            <h2 id="skill-dialog-title">{{ editingSkill ? editingSkill.name : 'Create Skill' }}</h2>
          </div>
          <button class="btn btn-secondary btn-sm" type="button" @click="closeDialog">Close</button>
        </div>

        <div class="form-grid">
          <label>
            <span>Name</span>
            <input v-model="form.name" class="form-control" type="text">
          </label>
          <label>
            <span>Category</span>
            <input v-model="form.category" class="form-control" type="text">
          </label>
          <label>
            <span>Owner</span>
            <input v-model="form.owner" class="form-control" type="text">
          </label>
          <label>
            <span>Status</span>
            <select v-model="form.status" class="form-control">
              <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>Current Version</span>
            <input v-model="form.currentVersion" class="form-control" type="text">
          </label>
          <label class="wide">
            <span>Tags</span>
            <input v-model="form.tagsText" class="form-control" type="text" placeholder="Comma-separated tags">
          </label>
          <label class="wide">
            <span>Description</span>
            <textarea v-model="form.description" class="form-control" rows="4" />
          </label>
          <label class="wide">
            <span>Version Notes</span>
            <textarea v-model="form.versionNotes" class="form-control" rows="3" />
          </label>
          <label class="wide">
            <span>Skill Content</span>
            <textarea v-model="form.content" class="form-control" rows="8" placeholder="Write the SKILL.md content for this version." />
          </label>
        </div>

        <p v-if="formError" class="status-message error">{{ formError }}</p>

        <div class="dialog-actions">
          <button class="btn btn-secondary" type="button" @click="closeDialog">Cancel</button>
          <button class="btn btn-primary" type="button" :disabled="saving" @click="saveSkill">
            {{ saving ? 'Saving...' : 'Save Skill' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="isVersionDialogOpen" class="modal-backdrop" role="presentation" @click.self="closeVersionDialog">
      <section class="skill-dialog compact" role="dialog" aria-modal="true" aria-labelledby="version-dialog-title">
        <div class="dialog-header">
          <div>
            <p class="view-eyebrow">Create Snapshot</p>
            <h2 id="version-dialog-title">Create Version</h2>
          </div>
          <button class="btn btn-secondary btn-sm" type="button" @click="closeVersionDialog">Close</button>
        </div>

        <div class="form-grid single">
          <label>
            <span>Version</span>
            <input v-model="versionForm.version" class="form-control" type="text">
          </label>
          <label>
            <span>Version Notes</span>
            <textarea v-model="versionForm.versionNotes" class="form-control" rows="4" />
          </label>
          <label>
            <span>Skill Content</span>
            <textarea v-model="versionForm.content" class="form-control" rows="8" />
          </label>
        </div>

        <p v-if="versionError" class="status-message error">{{ versionError }}</p>

        <div class="dialog-actions">
          <button class="btn btn-secondary" type="button" @click="closeVersionDialog">Cancel</button>
          <button class="btn btn-primary" type="button" :disabled="versionSaving" @click="saveVersion">
            {{ versionSaving ? 'Creating...' : 'Create Version' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.skill-hub-market {
  max-width: 1320px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.market-header,
.dialog-header,
.dialog-actions,
.detail-header,
.card-topline,
.card-footer,
.panel-heading,
.catalog-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.market-header {
  padding: 4px 0 2px;
}

.market-header h1,
.detail-header h2,
.dialog-header h2,
.empty-market h2,
.detail-empty h2 {
  margin: 0;
  color: var(--color-text-primary);
}

.market-header h1 {
  font-size: 30px;
}

.market-header p,
.detail-description,
.empty-market p,
.detail-empty p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
}

.view-eyebrow,
.market-toolbar label span,
.form-grid label span,
.metadata-grid dt {
  display: block;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.header-actions,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.icon-btn {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: var(--color-surface-strong);
  color: var(--color-text-secondary);
  font-weight: 800;
  cursor: pointer;
}

.market-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) minmax(150px, 0.8fr) minmax(140px, 0.7fr) minmax(140px, 0.7fr) auto;
  gap: 10px;
  align-items: end;
  padding: 14px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-card);
}

.market-toolbar label,
.form-grid label {
  min-width: 0;
}

.market-toolbar label span,
.form-grid label span {
  margin-bottom: 5px;
}

.catalog-area,
.skill-detail-dialog,
.skill-dialog {
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow-card);
}

.catalog-area {
  padding: 16px;
}

.catalog-meta {
  align-items: center;
  justify-content: flex-start;
  margin-bottom: 14px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.catalog-meta strong {
  color: var(--color-text-primary);
}

.skill-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.skill-card {
  min-height: 228px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid rgba(216, 227, 243, 0.95);
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  cursor: pointer;
  transition: border-color 140ms ease, transform 140ms ease, box-shadow 140ms ease;
}

.skill-card:hover {
  border-color: #2563eb;
  box-shadow: 0 14px 32px rgba(37, 99, 235, 0.14);
  transform: translateY(-1px);
}

.skill-card h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 18px;
}

.skill-card p {
  min-height: 44px;
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.skill-card code,
.metadata-grid code,
.version-timeline code {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #475569;
  font-size: 11px;
}

.category-pill,
.status-badge,
.tag-list strong,
.tag-list em,
.readonly-badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 900;
}

.category-pill {
  padding: 4px 8px;
  background: #eef2ff;
  color: #3730a3;
  border: 1px solid #c7d2fe;
}

.status-badge {
  padding: 4px 8px;
}

.status-badge.active {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.status-badge.draft {
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.status-badge.deprecated,
.status-badge.archived {
  background: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: auto;
}

.tag-list strong,
.tag-list em {
  padding: 4px 8px;
  background: #f0fdfa;
  border: 1px solid #99f6e4;
  color: #0f766e;
  font-style: normal;
}

.card-footer {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.skill-detail-dialog {
  width: min(1180px, 100%);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 20px;
}

.metadata-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 16px 0;
}

.metadata-grid div {
  min-width: 0;
  padding: 10px;
  border: 1px solid rgba(227, 234, 247, 0.88);
  border-radius: 8px;
  background: #f8fafc;
}

.metadata-grid dd {
  margin: 5px 0 0;
  color: var(--color-text-primary);
  font-weight: 800;
}

.content-preview,
.version-timeline {
  margin-top: 14px;
  border: 1px solid rgba(227, 234, 247, 0.92);
  border-radius: 8px;
  overflow: hidden;
}

.content-preview {
  border-color: #bfdbfe;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(37, 99, 235, 0.12);
}

.panel-heading {
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.92);
  background: #f8fafc;
}

.panel-heading h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
}

.panel-heading span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.content-preview pre {
  min-height: 520px;
  max-height: 680px;
  margin: 0;
  padding: 18px;
  overflow: auto;
  color: #111827;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.version-timeline ol {
  list-style: none;
  margin: 0;
  padding: 0;
}

.version-timeline li {
  display: grid;
  gap: 5px;
  padding: 12px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.92);
  cursor: pointer;
  transition: background 140ms ease, box-shadow 140ms ease;
}

.version-timeline li:last-child {
  border-bottom: 0;
}

.version-timeline li:hover,
.version-timeline li.selected {
  background: #eff6ff;
  box-shadow: inset 3px 0 0 #2563eb;
}

.version-timeline strong {
  color: var(--color-text-primary);
}

.version-timeline span,
.version-timeline p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.empty-market,
.detail-empty {
  padding: 32px 18px;
  text-align: center;
  color: var(--color-text-secondary);
}

.readonly-badge {
  padding: 4px 8px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.status-message {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
}

.status-message.error {
  color: #b91c1c;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.skill-dialog {
  width: min(780px, 100%);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 18px;
}

.skill-dialog.compact {
  width: min(560px, 100%);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.form-grid.single {
  grid-template-columns: 1fr;
}

.form-grid .wide {
  grid-column: 1 / -1;
}

.dialog-actions {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .market-toolbar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .market-header,
  .detail-header,
  .form-grid,
  .metadata-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}
</style>
