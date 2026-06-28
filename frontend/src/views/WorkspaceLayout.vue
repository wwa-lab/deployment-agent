<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { useRoute, useRouter } from 'vue-router'
import { agentRegistry, platformCapabilities } from '../config/agentRegistry'
import { FINBLOCK_URL } from '../config/platformConfig'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const sidebarRef = ref<HTMLElement | null>(null)
const sidebarScrollRef = ref<HTMLElement | null>(null)
const wwaButtonRef = ref<HTMLElement | null>(null)
const flyoutRef = ref<HTMLElement | null>(null)
const flyoutTop = ref(0)
const isWwaFlyoutOpen = ref(false)

// Derived from flyout state — was previously referenced but never declared (bug fix WWA-005)
const isWwaExpanded = computed(() => isWwaFlyoutOpen.value)

type PrimaryNavItem = {
  key: string
  label: string
  icon: string
  expandable?: boolean
}

const primaryNavItems: PrimaryNavItem[] = [
  { key: 'wwa', label: 'WWA-Atlas Hub', icon: '◫', expandable: true },
]

const activeSectionTitle = computed(() => (route.meta.sectionTitle as string) ?? 'WWA-Atlas Hub')
const isWwaWorkspace = computed(() => route.path.startsWith('/wwa'))
const isWwaHomeRoute = computed(() => route.name === 'wwa-home')

// Breadcrumb: show agent name when inside an agent workspace (not on home)
const activeWorkspaceLabel = computed(() => {
  const section = route.meta.section as string | undefined
  if (!section || section === 'home') return null
  const agent = agentRegistry.find((a) => a.key === section)
  return agent?.name ?? null
})
const shouldShowWorkspaceBreadcrumb = computed(
  () => !!activeWorkspaceLabel.value && activeWorkspaceLabel.value !== activeSectionTitle.value,
)

function openWwaHome() {
  router.push({ name: 'wwa-home' })
}

function setSidebarRef(element: Element | null) {
  sidebarRef.value = element as HTMLElement | null
}

function setSidebarScrollRef(element: Element | null) {
  sidebarScrollRef.value = element as HTMLElement | null
}

function setWwaButtonRef(element: Element | null) {
  wwaButtonRef.value = element as HTMLElement | null
}

function setFlyoutRef(element: Element | null) {
  flyoutRef.value = element as HTMLElement | null
}

function updateFlyoutPosition() {
  if (!sidebarRef.value || !wwaButtonRef.value) {
    return
  }

  const sidebarRect = sidebarRef.value.getBoundingClientRect()
  const buttonRect = wwaButtonRef.value.getBoundingClientRect()
  const buttonCenter = buttonRect.top - sidebarRect.top + buttonRect.height / 2

  if (!flyoutRef.value || window.innerWidth <= 1024) {
    flyoutTop.value = buttonCenter
    return
  }

  const flyoutHeight = flyoutRef.value.getBoundingClientRect().height
  const viewportPadding = 12
  const minTop = viewportPadding
  const maxTop = Math.max(
    minTop,
    window.innerHeight - sidebarRect.top - flyoutHeight - viewportPadding,
  )

  flyoutTop.value = Math.min(
    Math.max(buttonCenter - flyoutHeight / 2, minTop),
    maxTop,
  )
}

function handlePrimaryNav(item: PrimaryNavItem) {
  if (item.key === 'wwa') {
    if (!isWwaWorkspace.value) {
      isWwaFlyoutOpen.value = true
      openWwaHome()
      return
    }

    isWwaFlyoutOpen.value = !isWwaFlyoutOpen.value
  }
}

function closeWwaFlyout() {
  isWwaFlyoutOpen.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (!isWwaFlyoutOpen.value || !sidebarRef.value) {
    return
  }

  const target = event.target as Node | null
  if (target && !sidebarRef.value.contains(target)) {
    closeWwaFlyout()
  }
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('resize', updateFlyoutPosition)
  document.addEventListener('click', handleDocumentClick)
  nextTick(updateFlyoutPosition)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateFlyoutPosition)
  document.removeEventListener('click', handleDocumentClick)
})

watch(
  () => route.path,
  async () => {
    await nextTick()
    updateFlyoutPosition()
  },
)

watch(
  () => isWwaFlyoutOpen.value,
  async (isOpen) => {
    if (!isOpen) {
      return
    }

    await nextTick()
    updateFlyoutPosition()
  },
)
</script>

<template>
  <div class="workspace">
    <aside :ref="setSidebarRef" class="sidebar">
      <div class="sidebar-logo">
        <span class="logo-icon">▣</span>
        <div class="logo-copy">
          <span class="logo-text">WWA-Atlas Hub</span>
          <span class="logo-subtitle">Engineering Delivery Hub</span>
        </div>
      </div>
      <div class="sidebar-scroll" :ref="setSidebarScrollRef" @scroll="updateFlyoutPosition">
        <div class="sidebar-section-label">Delivery Hub</div>

        <nav class="sidebar-nav">
          <div
            v-for="item in primaryNavItems"
            :key="item.key"
            class="primary-nav-group"
            :class="{ 'has-flyout': item.key === 'wwa' && isWwaExpanded }"
          >
            <button
              :ref="item.key === 'wwa' ? setWwaButtonRef : undefined"
              class="primary-link"
              :class="{ active: item.key === 'wwa' && isWwaWorkspace }"
              type="button"
              @click="handlePrimaryNav(item)"
            >
              <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
              <span class="nav-label">{{ item.label }}</span>
              <span
                v-if="item.expandable"
                class="primary-chevron"
                aria-hidden="true"
              >
                {{ isWwaExpanded ? '▾' : '▸' }}
              </span>
            </button>
          </div>
        </nav>
      </div>

      <div
        v-if="isWwaFlyoutOpen"
        :ref="setFlyoutRef"
        class="secondary-flyout"
        :style="{ top: `${flyoutTop}px` }"
      >
        <div class="secondary-nav">
          <!-- WWA-Atlas Hub Home -->
          <router-link
            to="/wwa/home"
            class="nav-link secondary-link flyout-home"
            @click="closeWwaFlyout"
          >
            <span class="nav-icon" aria-hidden="true">🏠</span>
            <span class="nav-label">WWA-Atlas Hub Home</span>
          </router-link>

          <!-- Agent Workspaces -->
          <div class="flyout-section-label">Agent Workspaces</div>
          <router-link
            v-for="agent in agentRegistry.filter((a) => a.enabled)"
            :key="agent.key"
            :to="agent.route"
            class="nav-link secondary-link"
            @click="closeWwaFlyout"
          >
            <span class="nav-icon" aria-hidden="true">{{ agent.icon }}</span>
            <span class="nav-label">{{ agent.name }}</span>
          </router-link>

          <!-- Platform Capabilities -->
          <div class="flyout-section-label">Platform</div>
          <router-link
            v-for="cap in platformCapabilities"
            :key="cap.key"
            :to="cap.to"
            class="nav-link secondary-link"
            :title="cap.accessPermission && !userStore.hasPermission(cap.accessPermission as never) ? 'Visible in workspace, but access is role-restricted.' : ''"
            @click="closeWwaFlyout"
          >
            <span class="nav-icon" aria-hidden="true">{{ cap.icon }}</span>
            <span class="nav-label">{{ cap.label }}</span>
            <span
              v-if="cap.accessPermission && !userStore.hasPermission(cap.accessPermission as never)"
              class="nav-lock"
              aria-hidden="true"
            >🔒</span>
          </router-link>

          <!-- Return to FinBlock -->
          <div class="flyout-section-label">Navigation</div>
          <a :href="FINBLOCK_URL" class="nav-link secondary-link finblock-link">
            <span class="nav-icon" aria-hidden="true">↩</span>
            <span class="nav-label">Return to FinBlock</span>
          </a>
        </div>
      </div>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-branding">
          <div class="topbar-kicker">WWA-Atlas Hub</div>
          <div class="topbar-title">
            <span v-if="shouldShowWorkspaceBreadcrumb" class="breadcrumb-workspace">{{ activeWorkspaceLabel }}</span>
            <span v-if="shouldShowWorkspaceBreadcrumb" class="breadcrumb-sep" aria-hidden="true"> › </span>
            {{ activeSectionTitle }}
          </div>
        </div>
        <div class="topbar-user">
          <router-link
            to="/wwa/home"
            class="workspace-topbar-link wwa-home-topbar-link"
            :aria-current="isWwaHomeRoute ? 'page' : undefined"
          >
            ← WWA-Atlas Hub Home
          </router-link>
          <a :href="FINBLOCK_URL" class="finblock-topbar-link" title="Return to FinBlock">← FinBlock</a>
          <span
            v-if="userStore.isGuest"
            class="guest-readonly-badge"
            title="Guest mode is read-only. Sign in to perform actions."
          >
            👁 Read-only preview
          </span>
          <span class="user-name">{{ userStore.displayName || userStore.userId }}</span>
          <span class="badge badge-role">{{ userStore.role }}</span>
          <button class="btn btn-secondary btn-sm" @click="handleLogout">
            {{ userStore.isGuest ? 'Exit' : 'Logout' }}
          </button>
        </div>
      </header>
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.workspace {
  position: relative;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: rgba(245, 248, 255, 0.48);
  isolation: isolate;
}

.workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(116, 143, 188, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(116, 143, 188, 0.08) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.1));
  pointer-events: none;
  z-index: 0;
}

.sidebar {
  position: relative;
  width: 220px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #172235 0%, #1d2b45 38%, #21385a 100%);
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: visible;
  z-index: 20;
  border-right: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(87, 122, 190, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(87, 122, 190, 0.08) 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: 0.35;
  pointer-events: none;
}

.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 24px;
}

.sidebar-logo {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid #334155;
  font-weight: 700;
  font-size: 15px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4f7cff 0%, #2f66f3 100%);
  color: white;
  font-size: 14px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.28);
}

.logo-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-text {
  color: #f8fafc;
}

.logo-subtitle {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.sidebar-section-label {
  padding: 12px 16px 0;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  padding: 12px 8px;
  gap: 8px;
}

.primary-nav-group {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #cbd5e1;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, color 0.15s;
}

.primary-link::before {
  content: '';
  position: absolute;
  inset: 8px auto 8px 0;
  width: 2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(92, 164, 255, 0.2), rgba(92, 164, 255, 0.9), rgba(92, 164, 255, 0.2));
  opacity: 0;
  transition: opacity 0.15s;
}

.primary-link:hover {
  background: rgba(51, 65, 85, 0.88);
  color: #f8fafc;
}

.primary-link.active {
  background: rgba(51, 65, 85, 0.94);
  color: #f8fafc;
  box-shadow: inset 0 0 0 1px rgba(92, 164, 255, 0.08);
}

.primary-link:hover::before,
.primary-link.active::before {
  opacity: 1;
}

.primary-chevron {
  margin-left: auto;
  font-size: 12px;
  color: #94a3b8;
}

.secondary-flyout {
  position: absolute;
  top: 0;
  left: calc(100% + 18px);
  min-width: 260px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  padding: 14px 12px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(37, 67, 128, 0.96) 0%, rgba(30, 53, 101, 0.96) 100%);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(16px);
  z-index: 30;
}

.secondary-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.flyout-section-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7b97c7;
  padding: 10px 12px 4px;
}

.flyout-home {
  margin-bottom: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s, color 0.15s;
  text-decoration: none;
}

.secondary-link {
  font-size: 14px;
  color: #dbe7ff;
  border-radius: 8px;
}

.finblock-link {
  color: #93c5fd;
  font-style: italic;
}

.nav-label {
  min-width: 0;
}

.nav-lock {
  margin-left: auto;
  font-size: 12px;
  opacity: 0.75;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
}

.nav-link.router-link-active {
  background: #2563eb;
  color: white;
}

@media (max-width: 1024px) {
  .sidebar {
    overflow: hidden;
  }

  .secondary-flyout {
    position: static;
    transform: none;
    left: auto;
    top: auto;
    min-width: 0;
    margin-left: 12px;
    padding: 8px 0 0;
    border-radius: 0;
    background: transparent;
    border: none;
    box-shadow: none;
  }

  .secondary-nav {
    padding-left: 12px;
    border-left: 1px solid #334155;
  }
}

.nav-icon { font-size: 16px; }

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  isolation: isolate;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.42) 0%,
    rgba(245, 248, 255, 0.9) 38%,
    rgba(240, 248, 246, 0.78) 100%
  );
}

.main-area::before,
.main-area::after {
  content: '';
  position: absolute;
  pointer-events: none;
  border-radius: 999px;
  filter: blur(72px);
  opacity: 0.8;
  z-index: 0;
}

.main-area::before {
  top: -140px;
  right: -120px;
  width: 320px;
  height: 320px;
  background: rgba(220, 235, 255, 0.85);
}

.main-area::after {
  bottom: -180px;
  left: 8%;
  width: 280px;
  height: 280px;
  background: rgba(232, 247, 242, 0.68);
}

.topbar {
  min-height: 60px;
  background: rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(227, 234, 247, 0.94);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 24px rgba(31, 42, 68, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.topbar::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(61, 107, 218, 0), rgba(61, 107, 218, 0.86), rgba(61, 107, 218, 0));
}

.topbar-branding {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.topbar-kicker {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.breadcrumb-workspace {
  font-weight: 400;
  color: var(--color-text-muted);
}

.breadcrumb-sep {
  color: #94a3b8;
}

.topbar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.workspace-topbar-link {
  font-size: 12px;
  font-family: var(--font-mono);
  text-decoration: none;
  border: 1px solid #e2e8f0;
  padding: 4px 10px;
  border-radius: 6px;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.wwa-home-topbar-link {
  color: #2563eb;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.wwa-home-topbar-link:hover {
  color: #1d4ed8;
  border-color: #93c5fd;
  background: #dbeafe;
}

.wwa-home-topbar-link[aria-current='page'] {
  color: #1e3a8a;
  border-color: #93c5fd;
  background: #dbeafe;
}

.finblock-topbar-link {
  font-size: 12px;
  color: var(--color-text-muted);
  text-decoration: none;
  border: 1px solid #e2e8f0;
  padding: 4px 10px;
  border-radius: 6px;
  transition: color 0.15s, border-color 0.15s;
}

.finblock-topbar-link:hover {
  color: #2563eb;
  border-color: #2563eb;
}

.user-name {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.guest-readonly-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border: 1px solid #fcd34d;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px 32px;
  position: relative;
  z-index: 1;
}

@media (max-width: 768px) {
  .topbar {
    padding: 12px 16px;
    min-height: 72px;
  }

  .main-content {
    padding: 20px 16px 24px;
  }
}
</style>
