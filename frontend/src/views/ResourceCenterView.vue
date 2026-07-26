<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ResourceCenterDeleteDialog from '../components/ResourceCenterDeleteDialog.vue'
import ResourceCenterEntityDialog from '../components/ResourceCenterEntityDialog.vue'
import { useRecentResourceCenterLinks } from '../platform/composables/useRecentResourceCenterLinks'
import {
  linkMark,
  resolveDirectoryLinkIconSrc,
  resolveDirectoryLinkIconSurface,
} from '../platform/resourceCenter/linkPresentation'
import { useResourceCenterStore } from '../stores/resourceCenter'
import { useUserStore } from '../stores/user'
import type {
  DirectoryGroup,
  DirectoryLink,
  DirectoryLinkKind,
  DirectoryScope,
} from '../types'

type VisibleGroup = DirectoryGroup & {
  scopeKey: string
  scopeTitle: string
  links: DirectoryLink[]
}

type NavSection = {
  key: string
  title: string
  items: Array<{
    key: string
    title: string
    count: number
    stageOrder?: number | null
    type: DirectoryGroup['type']
    enabled: boolean
  }>
}

type DialogState =
  | {
      kind: 'entity'
      entityType: 'scope' | 'group' | 'link'
      mode: 'create' | 'edit'
      entity?: DirectoryScope | DirectoryGroup | DirectoryLink | null
      parentScopeKey?: string
      parentGroupKey?: string
      presetKind?: DirectoryLinkKind
    }
  | {
      kind: 'delete'
      entityType: 'scope' | 'group' | 'link'
      title: string
      scopeKey?: string
      groupKey?: string
      linkId?: string
      removedGroups?: number
      removedLinks?: number
    }
  | null

const KIND_OPTIONS: Array<{ id: 'all' | DirectoryLinkKind; label: string }> = [
  { id: 'all', label: 'All' },
  { id: 'docs', label: 'Docs' },
  { id: 'tool', label: 'Tools' },
  { id: 'workspace', label: 'Workspaces' },
  { id: 'repo', label: 'Repos' },
]

const KIND_ORDER: DirectoryLinkKind[] = ['docs', 'tool', 'workspace', 'repo']

const router = useRouter()
const store = useResourceCenterStore()
const userStore = useUserStore()
const { record, resolved, clear } = useRecentResourceCenterLinks()

const manageMode = ref(false)
const activeKind = ref<'all' | DirectoryLinkKind>('all')
const searchText = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)
const dialogState = ref<DialogState>(null)
const spyActiveKey = ref<string | null>(null)
const contentScrollRoot = ref<HTMLElement | null>(null)

let observer: IntersectionObserver | null = null

const isAdmin = computed(() => userStore.isDevOpsAdmin)
const includeDisabled = computed(() => isAdmin.value)
const recentLinks = computed(() => resolved(store.catalog))

function isPendingUrl(url: string): boolean {
  if (url.startsWith('/')) {
    return false
  }
  try {
    return new URL(url).hostname.endsWith('.invalid')
  } catch {
    return false
  }
}

/** Strip redundant "Common · Platform" → "Platform" when seed titles prefix the scope. */
function displayGroupTitle(title: string, scopeTitle: string): string {
  if (!scopeTitle) {
    return title
  }
  const separators = [' · ', ' - ', ' – ', ': ']
  for (const sep of separators) {
    const prefix = `${scopeTitle}${sep}`
    if (title.startsWith(prefix)) {
      return title.slice(prefix.length).trim()
    }
  }
  if (title.toLowerCase().startsWith(`${scopeTitle.toLowerCase()} `)) {
    return title.slice(scopeTitle.length).trim()
  }
  return title
}

/** Feedback entries get a small pen badge on the icon so write-back links read differently from plain docs. */
function isFeedbackLink(link: DirectoryLink): boolean {
  return /feedback/i.test(link.kindLabel ?? '')
}

const STAGE_HUE_START = 222
const STAGE_HUE_STEP = 9

/**
 * Soft-hue ramp across SDLC stage numbers (Planning blue → Maintenance teal-green).
 * Tuned for the resource-center rail: light pastel chips, muted text — not the loud
 * dark blue/teal that read as "black" against the neutral sidebar background.
 */
function stageTone(order: number | null | undefined): { background: string; color: string } | undefined {
  if (order == null || order < 1) {
    return undefined
  }
  const hue = STAGE_HUE_START - (order - 1) * STAGE_HUE_STEP
  return {
    background: `hsl(${hue}, 50%, 93%)`,
    color: `hsl(${hue}, 42%, 42%)`,
  }
}

function activateLink(link: DirectoryLink) {
  if (isPendingUrl(link.url)) {
    return
  }
  if (link.kind === 'workspace') {
    void router.push(link.url)
  } else {
    window.open(link.url, '_blank', 'noopener,noreferrer')
  }
  record(link.id)
}

function matchesSearch(
  link: DirectoryLink,
  group: DirectoryGroup,
  scope: DirectoryScope,
  query: string,
): boolean {
  if (!query) {
    return true
  }
  const haystack = [
    link.title,
    link.description,
    link.kindLabel,
    link.kind,
    group.title,
    group.description,
    group.agentName,
    scope.title,
  ]
    .join(' ')
    .toLowerCase()
  return haystack.includes(query)
}

function filterLinksForGroup(
  links: DirectoryLink[],
  group: DirectoryGroup,
  scope: DirectoryScope,
): DirectoryLink[] {
  const query = searchText.value.trim().toLowerCase()
  return links.filter((link) => {
    if (activeKind.value !== 'all' && link.kind !== activeKind.value) {
      return false
    }
    return matchesSearch(link, group, scope, query)
  })
}

function sortLinks(links: DirectoryLink[]): DirectoryLink[] {
  return [...links].sort((a, b) => {
    const kindDelta = KIND_ORDER.indexOf(a.kind) - KIND_ORDER.indexOf(b.kind)
    if (kindDelta !== 0) {
      return kindDelta
    }
    return a.sortOrder - b.sortOrder
  })
}

const visibleGroups = computed<VisibleGroup[]>(() => {
  const catalog = store.catalog
  if (!catalog) {
    return []
  }

  const output: VisibleGroup[] = []

  for (const scope of catalog.scopes) {
    for (const group of scope.groups) {
      const links = sortLinks(filterLinksForGroup(group.links, group, scope))
      if (links.length > 0 || manageMode.value) {
        output.push({
          ...group,
          scopeKey: scope.key,
          scopeTitle: scope.title,
          links,
        })
      }
    }
  }

  return output
})

const navSections = computed<NavSection[]>(() => {
  const catalog = store.catalog
  if (!catalog) {
    return []
  }

  const visibleKeys = new Set(visibleGroups.value.map((group) => `${group.scopeKey}:${group.key}`))

  return catalog.scopes
    .map((scope) => {
      const items = scope.groups
        .filter((group) => manageMode.value || visibleKeys.has(`${scope.key}:${group.key}`))
        .map((group) => {
          const matched = visibleGroups.value.find(
            (item) => item.scopeKey === scope.key && item.key === group.key,
          )
          return {
            key: group.key,
            title: displayGroupTitle(group.title, scope.title),
            count: matched?.links.length ?? group.links.length,
            stageOrder: group.stageOrder ?? (group.type === 'stage' ? group.sortOrder : null),
            type: group.type,
            enabled: group.enabled,
          }
        })

      return {
        key: scope.key,
        title: scope.layout === 'stage-strip' ? 'SDLC stages' : scope.title,
        items,
      }
    })
    .filter((section) => section.items.length > 0)
})

const isEmptyCatalog = computed(
  () => !store.loading && store.catalog != null && store.catalog.scopes.length === 0,
)

const hasFilterMatches = computed(() => visibleGroups.value.length > 0)

const hasActiveFilters = computed(
  () => activeKind.value !== 'all' || searchText.value.trim().length > 0,
)

function clearFilters() {
  activeKind.value = 'all'
  searchText.value = ''
}

function toggleManageMode() {
  if (!isAdmin.value) {
    return
  }
  manageMode.value = !manageMode.value
}

function scrollToGroup(groupKey: string) {
  spyActiveKey.value = groupKey
  requestAnimationFrame(() => {
    document.getElementById(`group-${groupKey}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function openCreateScope() {
  dialogState.value = { kind: 'entity', entityType: 'scope', mode: 'create' }
}

function openEditScope(scope: DirectoryScope) {
  dialogState.value = { kind: 'entity', entityType: 'scope', mode: 'edit', entity: scope }
}

function openCreateGroup(scopeKey?: string) {
  dialogState.value = {
    kind: 'entity',
    entityType: 'group',
    mode: 'create',
    parentScopeKey: scopeKey ?? store.catalog?.scopes[0]?.key,
  }
}

function openEditGroup(scopeKey: string, group: DirectoryGroup) {
  dialogState.value = {
    kind: 'entity',
    entityType: 'group',
    mode: 'edit',
    entity: group,
    parentScopeKey: scopeKey,
  }
}

function openCreateLink(scopeKey: string, groupKey: string, presetKind?: DirectoryLinkKind) {
  dialogState.value = {
    kind: 'entity',
    entityType: 'link',
    mode: 'create',
    parentScopeKey: scopeKey,
    parentGroupKey: groupKey,
    presetKind,
  }
}

function openEditLink(scopeKey: string, groupKey: string, link: DirectoryLink) {
  dialogState.value = {
    kind: 'entity',
    entityType: 'link',
    mode: 'edit',
    entity: link,
    parentScopeKey: scopeKey,
    parentGroupKey: groupKey,
  }
}

function countScopeDescendants(scope: DirectoryScope) {
  const groups = scope.groups.length
  const links = scope.groups.reduce((total, group) => total + group.links.length, 0)
  return { groups, links }
}

function openDeleteScope(scope: DirectoryScope) {
  const counts = countScopeDescendants(scope)
  dialogState.value = {
    kind: 'delete',
    entityType: 'scope',
    title: scope.title,
    scopeKey: scope.key,
    removedGroups: counts.groups,
    removedLinks: counts.links,
  }
}

function openDeleteGroup(scopeKey: string, group: DirectoryGroup) {
  dialogState.value = {
    kind: 'delete',
    entityType: 'group',
    title: group.title,
    scopeKey,
    groupKey: group.key,
    removedLinks: group.links.length,
  }
}

function openDeleteLink(link: DirectoryLink) {
  dialogState.value = {
    kind: 'delete',
    entityType: 'link',
    title: link.title,
    linkId: link.id,
  }
}

function closeDialog() {
  dialogState.value = null
}

async function handleEntitySave(payload: Record<string, unknown>) {
  const state = dialogState.value
  if (!state || state.kind !== 'entity') {
    return
  }

  try {
    if (state.entityType === 'scope') {
      if (state.mode === 'create') {
        await store.addScope(payload as never, includeDisabled.value)
      } else if (state.entity) {
        await store.editScope((state.entity as DirectoryScope).key, payload as never, includeDisabled.value)
      }
    } else if (state.entityType === 'group') {
      const scopeKey = state.parentScopeKey
      if (!scopeKey) {
        return
      }
      if (state.mode === 'create') {
        await store.addGroup(scopeKey, payload as never, includeDisabled.value)
      } else if (state.entity) {
        await store.editGroup(scopeKey, (state.entity as DirectoryGroup).key, payload as never, includeDisabled.value)
      }
    } else if (state.entityType === 'link') {
      const targetScopeKey = (payload.targetScopeKey as string | undefined) ?? state.parentScopeKey
      const targetGroupKey = (payload.targetGroupKey as string | undefined) ?? state.parentGroupKey
      if (!targetScopeKey || !targetGroupKey) {
        return
      }
      const linkPayload = { ...payload }
      delete linkPayload.targetScopeKey
      delete linkPayload.targetGroupKey

      if (state.mode === 'create') {
        await store.addLink(targetScopeKey, targetGroupKey, linkPayload as never, includeDisabled.value)
      } else if (state.entity) {
        const moving =
          targetScopeKey !== state.parentScopeKey || targetGroupKey !== state.parentGroupKey
        await store.editLink(
          (state.entity as DirectoryLink).id,
          {
            ...(linkPayload as never),
            ...(moving ? { targetScopeKey, targetGroupKey } : {}),
          },
          includeDisabled.value,
        )
      }
    }
    closeDialog()
  } catch {
    // saveError is handled in the store
  }
}

async function handleDeleteConfirm() {
  const state = dialogState.value
  if (!state || state.kind !== 'delete') {
    return
  }

  try {
    if (state.entityType === 'scope' && state.scopeKey) {
      await store.removeScope(state.scopeKey, includeDisabled.value)
    } else if (state.entityType === 'group' && state.scopeKey && state.groupKey) {
      await store.removeGroup(state.scopeKey, state.groupKey, includeDisabled.value)
    } else if (state.entityType === 'link' && state.linkId) {
      await store.removeLink(state.linkId, includeDisabled.value)
    }
    closeDialog()
  } catch {
    // saveError is handled in the store
  }
}

function onGlobalKeydown(event: KeyboardEvent) {
  if (dialogState.value) {
    if (event.key === 'Escape') {
      closeDialog()
    }
    return
  }

  const target = event.target
  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement
  ) {
    return
  }

  if (event.key === '/') {
    event.preventDefault()
    searchInputRef.value?.focus()
  }
}

function teardownObserver() {
  observer?.disconnect()
  observer = null
}

function setupScrollSpy() {
  teardownObserver()
  if (typeof IntersectionObserver === 'undefined') {
    return
  }

  const rootMargin = '-120px 0px -55% 0px'
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)
      if (visible[0]?.target instanceof HTMLElement) {
        const key = visible[0].target.dataset.groupKey
        if (key) {
          spyActiveKey.value = key
        }
      }
    },
    { root: null, rootMargin, threshold: [0.15, 0.35, 0.6] },
  )

  for (const group of visibleGroups.value) {
    const el = document.getElementById(`group-${group.key}`)
    if (el) {
      el.dataset.groupKey = group.key
      observer.observe(el)
    }
  }

  if (!spyActiveKey.value && visibleGroups.value[0]) {
    spyActiveKey.value = visibleGroups.value[0].key
  }
}

onMounted(() => {
  void store.fetchCatalog(includeDisabled.value)
  window.addEventListener('keydown', onGlobalKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  teardownObserver()
})

watch(includeDisabled, (value) => {
  void store.fetchCatalog(value)
})

watch(
  visibleGroups,
  async () => {
    await nextTick()
    setupScrollSpy()
  },
  { flush: 'post' },
)
</script>

<template>
  <div class="resource-center-view">
    <!-- Title lives in WorkspaceLayout topbar — keep this strip search/actions only to avoid double sticky titles. -->
    <header class="toolbar" aria-label="Resource Center tools">
      <div class="toolbar-search">
        <span class="search-icon" aria-hidden="true">⌕</span>
        <input
          id="resource-center-search"
          ref="searchInputRef"
          v-model="searchText"
          class="search-input"
          type="search"
          placeholder="Search links, stages, tools…"
          aria-label="Search Resource Center"
        />
        <kbd class="search-kbd">/</kbd>
      </div>

      <div class="toolbar-actions">
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="store.loading"
          @click="store.fetchCatalog(includeDisabled)"
        >
          {{ store.loading ? 'Refreshing…' : 'Refresh' }}
        </button>
        <button
          v-if="isAdmin"
          class="btn btn-sm"
          :class="manageMode ? 'btn-primary' : 'btn-secondary'"
          type="button"
          @click="toggleManageMode"
        >
          {{ manageMode ? 'Managing…' : 'Manage' }}
        </button>
      </div>
    </header>

    <div v-if="recentLinks.length > 0" class="recent-bar">
      <span class="recent-label">Recent</span>
      <div class="recent-row">
        <button
          v-for="link in recentLinks"
          :key="link.id"
          class="recent-chip"
          type="button"
          :disabled="isPendingUrl(link.url)"
          @click="activateLink(link)"
        >
          <span
            class="recent-mark"
            :class="{ 'has-brand-icon': !!resolveDirectoryLinkIconSrc(link) }"
            :style="{ background: resolveDirectoryLinkIconSurface(link) }"
          >
            <img
              v-if="resolveDirectoryLinkIconSrc(link)"
              :src="resolveDirectoryLinkIconSrc(link)!"
              alt=""
              class="icon-image"
            />
            <span v-else>{{ linkMark(link) }}</span>
          </span>
          <span class="recent-title">{{ link.title }}</span>
        </button>
      </div>
      <button class="recent-clear" type="button" @click="clear">Clear</button>
    </div>

    <div v-if="!isAdmin" class="status-banner muted">
      Read-only view. Only <strong>DEVOPS_ADMIN</strong> can change the directory.
    </div>
    <div v-if="manageMode && isAdmin" class="status-banner manage">
      Managing the catalog — edits apply immediately and are audited.
    </div>
    <div v-if="store.saveError" class="status-banner error">{{ store.saveError }}</div>
    <div v-if="store.error" class="status-banner error">
      {{ store.error }}
      <button class="inline-action" type="button" @click="store.fetchCatalog(includeDisabled)">
        Retry
      </button>
    </div>

    <div
      v-if="store.loading && !store.catalog"
      class="state-panel"
    >
      <span class="spinner"></span>
      <span>Loading Resource Center…</span>
    </div>

    <section v-else-if="isEmptyCatalog" class="state-panel">
      <p v-if="isAdmin">The directory is empty. Add a scope to get started.</p>
      <p v-else>The directory is empty.</p>
      <button v-if="isAdmin && manageMode" class="btn btn-primary" type="button" @click="openCreateScope">
        Add scope
      </button>
    </section>

    <div v-else class="workspace">
      <aside class="nav-sidebar" aria-label="Resource Center sections">
        <div
          v-for="section in navSections"
          :key="section.key"
          class="nav-section"
        >
          <p class="nav-section-title">{{ section.title }}</p>
          <button
            v-for="item in section.items"
            :key="`${section.key}-${item.key}`"
            class="nav-item"
            :class="{ active: spyActiveKey === item.key, disabled: !item.enabled }"
            type="button"
            @click="scrollToGroup(item.key)"
          >
            <span
              v-if="item.type === 'stage'"
              class="nav-num"
              :style="{ color: stageTone(item.stageOrder)?.color }"
            >{{ item.stageOrder }}</span>
            <span class="nav-name">{{ item.title }}</span>
            <span class="nav-count">{{ item.count }}</span>
          </button>
        </div>
      </aside>

      <div ref="contentScrollRoot" class="content-pane">
        <div class="kind-bar">
          <div class="kind-chips">
            <button
              v-for="kind in KIND_OPTIONS"
              :key="kind.id"
              class="kind-chip"
              :class="{ active: activeKind === kind.id }"
              type="button"
              @click="activeKind = kind.id"
            >
              {{ kind.label }}
            </button>
          </div>
          <button
            v-if="hasActiveFilters"
            class="btn btn-secondary btn-sm"
            type="button"
            @click="clearFilters"
          >
            Clear filters
          </button>
        </div>

        <div v-if="manageMode && isAdmin" class="manage-toolbar">
          <button class="btn btn-secondary btn-sm" type="button" @click="openCreateScope">Add scope</button>
          <button class="btn btn-secondary btn-sm" type="button" @click="openCreateGroup()">Add group</button>
        </div>

        <section v-if="!hasFilterMatches" class="state-panel compact">
          <p>No links match your filters.</p>
          <button class="btn btn-secondary" type="button" @click="clearFilters">Clear filters</button>
        </section>

        <template v-else>
          <article
            v-for="group in visibleGroups"
            :id="`group-${group.key}`"
            :key="`${group.scopeKey}-${group.key}`"
            class="group-block"
            :class="{ disabled: !group.enabled }"
          >
            <div class="group-head">
              <div class="group-title-row">
                <span
                  v-if="group.type === 'stage'"
                  class="stage-num"
                  :style="stageTone(group.stageOrder ?? group.sortOrder)"
                >{{ group.stageOrder ?? group.sortOrder }}</span>
                <h2 class="group-title">{{ displayGroupTitle(group.title, group.scopeTitle) }}</h2>
                <span class="group-meta-inline">
                  {{ group.links.length }} link{{ group.links.length === 1 ? '' : 's' }}
                  <template v-if="group.agentName"> · {{ group.agentName }}</template>
                </span>
                <span v-if="!group.enabled" class="status-pill">Disabled</span>
              </div>
              <div v-if="manageMode && isAdmin" class="group-actions">
                <button class="btn btn-secondary btn-sm" type="button" @click="openEditGroup(group.scopeKey, group)">
                  Edit group
                </button>
                <button class="btn btn-danger btn-sm" type="button" @click="openDeleteGroup(group.scopeKey, group)">
                  Delete group
                </button>
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  @click="openCreateLink(group.scopeKey, group.key, 'docs')"
                >
                  + Add link
                </button>
              </div>
            </div>

            <div class="link-grid">
              <div
                v-for="link in group.links"
                :key="link.id"
                class="card-wrap"
                :class="{ disabled: !link.enabled, pending: isPendingUrl(link.url) }"
              >
                <div v-if="manageMode && isAdmin" class="card-actions">
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    @click="openEditLink(group.scopeKey, group.key, link)"
                  >
                    Edit
                  </button>
                  <button class="btn btn-danger btn-sm" type="button" @click="openDeleteLink(link)">
                    Delete
                  </button>
                </div>
                <button
                  class="link-card"
                  :class="`kind-${link.kind}`"
                  type="button"
                  :disabled="isPendingUrl(link.url)"
                  @click="activateLink(link)"
                >
                  <span
                    v-if="isPendingUrl(link.url)"
                    class="pending-dot"
                    title="URL pending"
                    role="img"
                    aria-label="URL pending"
                  ></span>
                  <div
                    class="icon"
                    :class="{ 'has-brand-icon': !!resolveDirectoryLinkIconSrc(link) }"
                    :style="{ background: resolveDirectoryLinkIconSurface(link) }"
                  >
                    <img
                      v-if="resolveDirectoryLinkIconSrc(link)"
                      :src="resolveDirectoryLinkIconSrc(link)!"
                      alt=""
                      class="icon-image"
                    />
                    <span v-else>{{ linkMark(link) }}</span>
                    <span
                      v-if="isFeedbackLink(link)"
                      class="icon-badge"
                      title="Feedback entry"
                      role="img"
                      aria-label="Feedback entry"
                    >
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="3.2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                      </svg>
                    </span>
                  </div>
                  <div class="body">
                    <p class="name">{{ link.title }}</p>
                    <p v-if="link.description" class="desc">{{ link.description }}</p>
                    <span v-if="!link.enabled && !isPendingUrl(link.url)" class="status-pill">Disabled</span>
                  </div>
                  <span class="arrow" aria-hidden="true">›</span>
                </button>
              </div>
            </div>
          </article>
        </template>

        <article
          v-for="scope in store.catalog?.scopes.filter(() => manageMode && isAdmin) ?? []"
          :key="`manage-scope-${scope.key}`"
          class="scope-manage"
        >
          <div class="scope-manage-head">
            <div>
              <h3 class="scope-manage-title">{{ scope.title }}</h3>
              <p class="scope-manage-meta">{{ scope.key }} · {{ scope.layout }}</p>
              <p v-if="scope.system && !scope.enabled" class="scope-warning">
                Disabling this system scope hides the section for all users.
              </p>
            </div>
            <div class="scope-manage-actions">
              <button class="btn btn-secondary btn-sm" type="button" @click="openEditScope(scope)">
                Edit scope
              </button>
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                @click="openCreateGroup(scope.key)"
              >
                Add group
              </button>
              <button
                v-if="!scope.system"
                class="btn btn-danger btn-sm"
                type="button"
                @click="openDeleteScope(scope)"
              >
                Delete scope
              </button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <ResourceCenterEntityDialog
      v-if="dialogState?.kind === 'entity'"
      :entity-type="dialogState.entityType"
      :mode="dialogState.mode"
      :scopes="store.catalog?.scopes ?? []"
      :entity="dialogState.entity"
      :parent-scope-key="dialogState.parentScopeKey"
      :parent-group-key="dialogState.parentGroupKey"
      :preset-kind="dialogState.presetKind"
      :saving="store.saving"
      :error="store.saveError"
      @close="closeDialog"
      @save="handleEntitySave"
    />

    <ResourceCenterDeleteDialog
      v-if="dialogState?.kind === 'delete'"
      :entity-type="dialogState.entityType"
      :title="dialogState.title"
      :removed-groups="dialogState.removedGroups"
      :removed-links="dialogState.removedLinks"
      :saving="store.saving"
      @close="closeDialog"
      @confirm="handleDeleteConfirm"
    />
  </div>
</template>

<style scoped>
.resource-center-view {
  width: 100%;
  max-width: 1480px;
  margin: 0 auto;
  padding: 0 clamp(0px, 1vw, 12px) 40px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  --rc-accent: #2563eb;
  --rc-accent-strong: #1d4ed8;
  --rc-accent-soft: rgba(37, 99, 235, 0.12);
  --rc-accent-hover: rgba(37, 99, 235, 0.06);
}

.toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 24px rgba(31, 42, 68, 0.08);
  backdrop-filter: blur(12px);
}

.toolbar-search {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
  max-width: 640px;
}

.search-icon {
  position: absolute;
  left: 12px;
  color: var(--color-text-muted);
  font-size: 14px;
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 36px;
  padding: 0 42px 0 34px;
  border: 1px solid var(--color-border-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--color-text-primary);
  font: inherit;
}

.search-input:focus {
  outline: none;
  border-color: var(--rc-accent);
  box-shadow: 0 0 0 3px var(--rc-accent-soft);
}

.search-kbd {
  position: absolute;
  right: 10px;
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border-subtle);
  border-radius: 6px;
  background: #f8fafc;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.recent-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
}

.recent-label {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.recent-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
}

.recent-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 220px;
  padding: 5px 10px 5px 6px;
  border-radius: 999px;
  border: 1px solid var(--color-border-subtle);
  background: #fff;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.recent-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.recent-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.recent-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  flex: 0 0 auto;
}

.recent-mark.has-brand-icon {
  color: inherit;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.recent-mark .icon-image {
  width: 12px;
  height: 12px;
  object-fit: contain;
}

.recent-clear {
  border: none;
  background: none;
  color: var(--color-text-muted);
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
  flex: 0 0 auto;
}

.status-banner {
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
}

.status-banner.muted {
  background: #f8fafc;
  border: 1px solid var(--color-border-subtle);
  color: var(--color-text-secondary);
}

.status-banner.manage {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  font-weight: 600;
}

.status-banner.error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
}

.inline-action {
  margin-left: 8px;
  border: none;
  background: none;
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
}

.workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.nav-sidebar {
  position: sticky;
  top: 68px;
  max-height: calc(100vh - 96px);
  overflow: auto;
  padding: 4px 2px 12px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.nav-section-title {
  margin: 0 0 6px 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.nav-item {
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  text-align: left;
  cursor: pointer;
}

.nav-item:hover {
  background: var(--rc-accent-hover);
}

.nav-item.active {
  background: var(--rc-accent-soft);
  color: var(--rc-accent-strong);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--rc-accent);
}

.nav-item.disabled {
  opacity: 0.55;
}

.nav-num {
  width: 18px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  font-variant-numeric: tabular-nums;
}

.nav-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-count {
  font-size: 12px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.content-pane {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.kind-bar,
.manage-toolbar,
.group-head,
.scope-manage-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.kind-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.kind-chip {
  border: 1px solid var(--color-border-subtle);
  background: #fff;
  color: var(--color-text-secondary);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
}

.kind-chip.active {
  background: var(--rc-accent);
  border-color: var(--rc-accent);
  color: #fff;
}

.manage-toolbar {
  justify-content: flex-start;
}

.group-block {
  scroll-margin-top: 84px;
}

.group-block + .group-block {
  margin-top: 6px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-subtle);
}

.group-block.disabled {
  opacity: 0.72;
}

.group-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 10px;
  min-width: 0;
}

.stage-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

.group-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.group-meta-inline {
  font-size: 12px;
  color: var(--color-text-muted);
}

.group-actions,
.scope-manage-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.link-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.card-wrap {
  position: relative;
}

.card-wrap.disabled {
  opacity: 0.72;
}

.card-actions {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
  display: none;
  gap: 4px;
}

.card-wrap:hover .card-actions {
  display: flex;
}

.link-card {
  position: relative;
  --accent: transparent;
  width: 100%;
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border-radius: 10px;
  border: 0.5px solid var(--color-border-subtle);
  background: #fff;
  box-shadow: inset 3px 0 0 var(--accent), 0 1px 2px rgba(31, 42, 68, 0.04);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.link-card:hover:not(:disabled) {
  border-color: #93c5fd;
  box-shadow: inset 3px 0 0 var(--accent), 0 4px 14px rgba(37, 99, 235, 0.08);
}

.link-card.kind-workspace {
  --accent: #7c3aed;
  background: linear-gradient(135deg, #fbf8ff 0%, #f6f1ff 100%);
  border-color: rgba(124, 58, 237, 0.22);
}

.link-card.kind-workspace:hover:not(:disabled) {
  border-color: #a78bfa;
  box-shadow: inset 3px 0 0 var(--accent), 0 4px 14px rgba(124, 58, 237, 0.12);
}

.link-card.kind-docs {
  --accent: #2563eb;
}

.link-card.kind-tool {
  --accent: #0f766e;
}

.link-card.kind-repo {
  --accent: #24292f;
}

.link-card:hover:not(:disabled) .arrow {
  opacity: 1;
}

.link-card:disabled {
  cursor: default;
}

.card-wrap.pending .link-card {
  border-style: dashed;
  border-color: rgba(148, 163, 184, 0.55);
}

.pending-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.16);
}

.icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-top: 1px;
  border-radius: 8px;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  flex: 0 0 auto;
}

.icon-badge {
  position: absolute;
  right: -4px;
  bottom: -4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #0f766e;
  color: #fff;
  border: 1.5px solid #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.18);
}

.icon-badge svg {
  width: 8px;
  height: 8px;
}

.icon.has-brand-icon {
  color: inherit;
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.icon-image {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.body {
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--color-text-primary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.desc {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-text-muted);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.status-pill {
  display: inline-flex;
  margin-top: 4px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  background: rgba(148, 163, 184, 0.18);
  color: var(--color-text-secondary);
}

.arrow {
  align-self: center;
  font-size: 18px;
  color: var(--accent, var(--color-text-muted));
  opacity: 0.4;
  transition: opacity 0.15s, color 0.15s;
}

.scope-manage {
  padding: 12px 14px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: #fff;
}

.scope-manage-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
}

.scope-manage-meta,
.scope-warning {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.scope-warning {
  color: #b45309;
}

.state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 180px;
  padding: 24px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 12px;
  background: #fff;
  color: var(--color-text-secondary);
}

.state-panel.compact {
  min-height: 120px;
}

.btn-danger {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
  border-color: #b91c1c;
}

@media (max-width: 960px) {
  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar-search {
    flex: 1 1 100%;
    max-width: none;
    order: 2;
  }

  .toolbar-actions {
    margin-left: auto;
    order: 1;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .nav-sidebar {
    position: static;
    max-height: none;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 8px 16px;
    padding-bottom: 4px;
    border-bottom: 1px solid var(--color-border-subtle);
  }

  .nav-section {
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .recent-bar {
    flex-wrap: wrap;
  }

  .link-grid {
    grid-template-columns: 1fr;
  }
}
</style>
