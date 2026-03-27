# WWA Agent Workspace Hub Transition — Migration Plan

**Date:** 2026-03-26
**Status:** Draft
**Source tasks:** `docs/06-tasks/wwa-platform-transition-tasks.md`
**Integration standard:** `docs/00-context/multi-agent-integration-standard.md`

**Naming note:** In this document, `WWA` refers to the `WWA Agent Workspace Hub`, the platform layer above individual agent workspaces.

---

## 1. Current State Assessment

### What exists and is already aligned

| Area | Current state |
|------|---------------|
| Route namespace | All routes under `/wwa/...` ✓ |
| Shell layout | `WorkspaceLayout.vue` wraps all `/wwa` routes ✓ |
| Topbar kicker | Shows "WWA" label above the section title ✓ |
| Shared capability pages | Configuration Management, Audit Log, Access Management all routed ✓ |
| Deny-by-default access | `AccessGrant` entity + `PermissionResolver` enforce entry gates ✓ |
| Scoped visibility | `AccessScope (application, snowGroup)` controls what each user sees ✓ |

### What is partially aligned

| Area | Gap |
|------|-----|
| Default entry | `/` and `/wwa` both redirect to `/wwa/deployment-agent`; there is no WWA home page |
| Shell semantics | `WorkspaceLayout.vue` contains a bug: `isWwaExpanded` is referenced (lines 183, 204) but never declared; the chevron direction is always stale |
| Authenticated redirect | `router.beforeEach` sends authenticated users to `wwa-deployment-agent`, not a WWA home |
| Navigation wording | Flyout nav lists "Deployment Agent" as the first item with no visual distinction from platform capabilities |
| Sidebar identity | Logo reads "Workspace Hub / Application Navigation", not "WWA" |

### What is missing

| Missing piece | Detail |
|---------------|--------|
| WWA home page | No `WwaHomeView.vue`; no `/wwa/home` route exists |
| Agent registry | No `agentRegistry.ts` or backend equivalent; nav items are hardcoded in `WorkspaceLayout.vue` |
| Platform vs agent permission boundary | All 15 `PermissionKey` values are Release-Agent-centric (`release.*`, `task.*`); no `platform.enter` or equivalent |
| Platform audit fields | `AuditLogEntry` has a free-text `agent` column but lacks `agentName`, `targetType`, `targetId`, `sourceSystem` from the integration standard |
| Config classification | All 7 `ConfigKey` values are Jenkins/Ansible execution settings; no platform-shared configuration concept |
| Return-to-FinBlock affordance | No link or button to return users to FinBlock from anywhere in the shell |
| Second agent onboarding path | Nothing prevents a second agent from needing its own custom shell integration |
| Onboarding checklist | Not present |

---

## 2. Task-to-Code Mapping

### WWA-001 — Confirm WWA Product Positioning and Naming

**Effort:** S — decision artifact, no code changes

| Action | File |
|--------|------|
| Write product positioning decision record | `docs/00-context/wwa-product-positioning.md` (create) |

**Change description:** Lock the naming hierarchy (`FinBlock → WWA → Agent Workspace → Deployment Agent`) and confirm it as the foundation for all subsequent route, permission, and documentation work. This document gates all other tasks.

---

### WWA-002 — Add Dedicated WWA Home / Agent Directory

**Effort:** M

| Action | File |
|--------|------|
| Create home view | `frontend/src/views/WwaHomeView.vue` (create) |
| Add route | `frontend/src/router/index.ts` — add `/wwa/home` route with `name: 'wwa-home'` and `sectionTitle: 'Home'` meta |
| Expose home in nav | `frontend/src/views/WorkspaceLayout.vue` — add `wwa-home` entry at top of `navItems` computed |

**Change description:** `WwaHomeView.vue` should display: platform identity, current user, agent cards driven by the agent registry (WWA-004), recent visits placeholder, and a return-to-FinBlock affordance. Do not hard-code Deployment Agent as the only card.

---

### WWA-003 — Change Default Routing to Land in WWA Home

**Effort:** S

| Action | File |
|--------|------|
| Change root redirect | `frontend/src/router/index.ts` line 15: `redirect: '/wwa/home'` |
| Change `/wwa` redirect | `frontend/src/router/index.ts` line 38-40: redirect to `{ name: 'wwa-home' }` |
| Change authenticated redirect | `frontend/src/router/index.ts` line 107: `return { name: 'wwa-home' }` |
| Add `/release-flows` legacy redirect | `frontend/src/router/index.ts` line 18-19: keep redirecting to `/wwa/deployment-agent` (legacy bookmark support) |

**Change description:** Ensure users who navigate to `/`, `/wwa`, or are redirected after login all land on the new WWA home page, not directly in Deployment Agent.

---

### WWA-004 — Introduce an Agent Registry Model

**Effort:** M

| Action | File |
|--------|------|
| Create agent registry config | `frontend/src/config/agentRegistry.ts` (create) |
| Define `AgentDescriptor` type | `frontend/src/config/agentRegistry.ts` — fields: `key`, `name`, `description`, `route`, `icon`, `enabled`, `category` |
| Register Deployment Agent | `frontend/src/config/agentRegistry.ts` — first entry |
| Consume in shell nav | `frontend/src/views/WorkspaceLayout.vue` — replace hardcoded `navItems` with registry-driven list for agent-category items |
| Consume in WWA home | `frontend/src/views/WwaHomeView.vue` — iterate registry to render agent cards |

**Change description:** A static TypeScript registry is sufficient for Phase 1. A backend registry endpoint can be added in Phase 3 when a real second agent is onboarded. The registry contract must match the integration standard's minimum agent intake fields (section 8).

---

### WWA-005 — Refactor Shell Navigation Around Platform-First Semantics

**Effort:** M

| Action | File |
|--------|------|
| Fix `isWwaExpanded` bug | `frontend/src/views/WorkspaceLayout.vue` — declare `const isWwaExpanded = computed(() => isWwaFlyoutOpen.value)` or replace references with `isWwaFlyoutOpen` |
| Separate platform and agent nav groups | `frontend/src/views/WorkspaceLayout.vue` — split `navItems` into `platformNavItems` (Audit Log, Access Management) and `agentNavItems` (from registry) |
| Update sidebar logo | `frontend/src/views/WorkspaceLayout.vue` — change `logo-text` to "WWA Platform" and `logo-subtitle` to "Agent Workspace Hub" |
| Add breadcrumb context | `frontend/src/views/WorkspaceLayout.vue` — add a visual breadcrumb row beneath topbar: `WWA > Deployment Agent` when inside an agent |
| Update `openWwaWorkspace()` | `frontend/src/views/WorkspaceLayout.vue` line 88: push to `/wwa/home` instead of `/wwa/deployment-agent` |

**Change description:** Users should always know they are in WWA, which agent they are viewing, and how to return to the platform home. The shell must not say or imply "Deployment Agent" is the default product.

---

### WWA-006 — Add a Clear Return-to-FinBlock Pattern

**Effort:** S

| Action | File |
|--------|------|
| Add FinBlock link to topbar | `frontend/src/views/WorkspaceLayout.vue` — add `<a href="..." class="finblock-link">← FinBlock</a>` in `.topbar-user` area |
| Externalize FinBlock URL | `frontend/src/config/platformConfig.ts` (create) — export `FINBLOCK_URL` from environment variable |
| Surface on WWA home | `frontend/src/views/WwaHomeView.vue` — include a secondary FinBlock entry link |

**Change description:** For Phase 1, a simple anchor link is sufficient. No deep-linked context handoff is required. Use an environment variable to avoid hardcoding the FinBlock URL.

---

### WWA-007 — Define Platform-Level Access Versus Agent-Level Access

**Effort:** M

| Action | File |
|--------|------|
| Add platform permission keys | `src/main/java/.../contracts/enums/PermissionKey.java` — add `PLATFORM_ENTER("platform.enter")`, `PLATFORM_ACCESS_MANAGE("platform.access.manage")`, `PLATFORM_AUDIT_VIEW("platform.audit.view")` |
| Update PermissionResolver | `src/main/java/.../domain/auth/PermissionResolver.java` — map platform permissions to DEVOPS_ADMIN and appropriate roles |
| Document permission taxonomy | `docs/00-context/wwa-permission-taxonomy.md` (create) — table showing platform-level vs agent-level permissions per role |

**Change description:** `AUDIT_VIEW` and `ACCESS_MANAGE` are currently agent-scoped keys used to gate shared platform pages. The intent must be clarified: platform-level visibility (entry and shared capability access) should eventually be owned by `platform.*` keys, while `release.*`, `task.*`, `config.*` remain agent-private. For backward compatibility in Phase 1, existing keys may remain; the new platform keys are additive.

---

### WWA-008 — Reframe Access Management as a Platform Capability

**Effort:** M

| Action | File |
|--------|------|
| Update page header wording | `frontend/src/views/AccessManagementView.vue` — change page title from "Access Management" (Deployment Agent implied) to "WWA Access Management" with subtitle "Controls platform entry and agent workspace visibility" |
| Add scope concept to UI | `frontend/src/views/AccessManagementView.vue` — add column or badge showing which access grants are platform-wide versus agent-scoped |
| Update Javadoc | `src/main/java/.../domain/auth/AccessGrant.java` — add class-level Javadoc clarifying the `scopeGrants` field and its intended multi-agent evolution |
| Update API endpoint path | `src/main/java/.../web/controller/AccessGrantController.java` — consider moving from `/api/deployment-agent/access-grants` to `/api/wwa/access-grants` (coordinate with WWA-013 API prefix work) |

**Change description:** The data model (`AccessGrant` with `assignedRoles` + `scopeGrants`) is already extensible. This task is primarily a wording and conceptual reframe, not a data schema change. Do not break the 167 existing tests.

---

### WWA-009 — Define Platform Audit Model

**Effort:** M

| Action | File |
|--------|------|
| Add standard audit fields | `src/main/java/.../domain/audit/AuditLogEntry.java` — add `agentName VARCHAR(255)`, `targetType VARCHAR(100)`, `targetId VARCHAR(36)`, `sourceSystem VARCHAR(100)` columns |
| Add schema migration | `src/main/resources/db/migration/` or `src/test/resources/schema.sql` — add `ALTER TABLE DA_AUDIT_LOG_ENTRY ADD ...` DDL for new columns |
| Update H2 test schema | `src/test/resources/schema.sql` — add the same columns so existing tests do not break |
| Update AuditLoggerService | `src/main/java/.../domain/audit/AuditLoggerService.java` — populate `agentName` = "deployment-agent" on all log calls |
| Document audit taxonomy | `docs/00-context/wwa-audit-taxonomy.md` (create) — table separating platform audit events from agent activity events |

**Change description:** The existing `agent` column (free-text, line 75 of `AuditLogEntry.java`) partially covers `agentName`. Rename or supplement it rather than add a duplicate. The new fields (`targetType`, `targetId`, `sourceSystem`) must be nullable so existing log calls do not need to be updated immediately.

---

### WWA-010 — Split Platform Audit from Agent Activity

**Effort:** M

| Action | File |
|--------|------|
| Add filter tabs to audit view | `frontend/src/views/AuditLogView.vue` — add "Platform Events" and "Agent Activity" tabs; platform tab filters by `actionType IN (LOGIN, LOGOUT, ACCESS_GRANT_*, PLATFORM_*)`, agent tab shows the rest |
| Update audit store | `frontend/src/stores/audit.ts` — add `scope` parameter to fetch calls |
| Update audit API | `src/main/java/.../web/controller/AuditLogController.java` — accept optional `scope=platform|agent` query param |

**Change description:** Keep all events in the same `DA_AUDIT_LOG_ENTRY` table for now. The split is a view-level filter, not a physical separation. This avoids breaking existing queries.

---

### WWA-011 — Classify Configuration Into Platform-Shared and Agent-Private

**Effort:** S

| Action | File |
|--------|------|
| Add config scope annotation | `src/main/java/.../contracts/enums/ConfigKey.java` — rename enum to add comments: `// Agent-private: Deployment Agent execution` above Jenkins/Ansible keys; add `// Platform-shared: reserved for future use` section |
| Update config UI wording | `frontend/src/views/ConfigAdminView.vue` — add section header "Deployment Agent Configuration" above the Jenkins/Ansible settings |

**Change description:** All 7 current `ConfigKey` values (`jenkins_url`, `jenkins_user`, `jenkins_api_token`, `ansible_url`, `ansible_user`, `ansible_api_token`, `execution_callback_endpoint`) are agent-private. No config key belongs to the platform layer yet. Document this as an explicit decision.

---

### WWA-012 — Keep Template Management Release-Agent-Scoped for Now

**Effort:** S — documentation decision only

| Action | File |
|--------|------|
| Add decision record | `docs/00-context/wwa-product-positioning.md` — append section: "Template Management stays Release-Agent-scoped until a second agent proves shared template reuse is needed" |
| Add comment to router | `frontend/src/router/index.ts` — add comment above the `template-management` route confirming it is agent-private |

**Change description:** No code movement. The `TemplateManagementView.vue` and its route remain exactly where they are. This task records the non-generalization decision so future developers do not premature-extract it.

---

### WWA-013 — Normalize Deployment Agent as the First Workspace

**Effort:** M

| Action | File |
|--------|------|
| Update route sectionTitle | `frontend/src/router/index.ts` — keep `sectionTitle: 'Deployment Agent'` but add `workspaceLabel: 'Deployment Agent'` meta for breadcrumb use |
| Update view page headers | `frontend/src/views/ReleaseFlowSummaryView.vue` — change any "Welcome to Deployment Agent" or similar copy to use "Deployment Agent" as a workspace name, not a product title |
| Update view page headers | `frontend/src/views/ReleaseFlowDetailView.vue` — same framing update |
| Update shell nav label ordering | `frontend/src/views/WorkspaceLayout.vue` — in flyout, place a "Workspaces" sub-header above agent items and "Platform" sub-header above shared capability items |
| Remove DA-centric sidebar assumption | `frontend/src/views/WorkspaceLayout.vue` line 88: `openWwaWorkspace()` should go to `/wwa/home`, not `/wwa/deployment-agent` (same fix as WWA-005) |

**Change description:** Do not rename or restructure the Deployment Agent domain logic. The change is purely at the shell framing level. Preserve all existing `/wwa/deployment-agent` routes and all controller mappings.

---

### WWA-014 — Update Supporting Documentation for the New Operating Model

**Effort:** M

| Action | File |
|--------|------|
| Update architecture overview section | `docs/04-architecture/architecture.md` — replace "Deployment Agent is a controlled deployment workflow system" opening with "Deployment Agent is the first workspace within the WWA Agent Workspace Hub" framing |
| Update design doc | `docs/05-design/design.md` — add "Platform Context" section describing WWA shell relationship |
| Update IMPLEMENTATION_PLAN.md | `docs/IMPLEMENTATION_PLAN.md` — annotate completed phases; add note that WWA Agent Workspace Hub transition tasks are tracked separately |
| Cross-document consistency check | Verify that architecture.md and design.md consistently use "WWA" as the platform name and "Deployment Agent" as the first workspace |

**Change description:** No code changes. Documentation only. Must be completed before Batch 5 tasks reference it.

---

### WWA-015 — Define New-Agent Onboarding Checklist

**Effort:** S

| Action | File |
|--------|------|
| Create checklist | `docs/00-context/agent-onboarding-checklist.md` (create) |

**Change description:** The checklist must cover: naming decision, agent metadata for registry (`agentRegistry.ts` entry), route registration in `frontend/src/router/index.ts`, access grant model, platform audit fields (`agentName` population), configuration ownership declaration, and return-from-agent navigation. Derived from integration standard section 15.

---

### WWA-016 — Add Acceptance Tests for WWA Entry and Shell Behavior

**Effort:** M

| Action | File |
|--------|------|
| Create shell acceptance test | `src/test/java/.../web/WwaShellAccessTest.java` (create) |
| Test: unauthenticated redirect | Assert GET `/wwa/home` (via API session) redirects to login |
| Test: authenticated home routing | Assert authenticated user with DEVELOPER role has access |
| Test: shared capability access | Assert AUDIT role can reach audit log but not access management |
| Test: DA reachability | Assert `/api/deployment-agent/release-flows` returns 200 for DEVELOPER role |

**Change description:** These are backend integration tests using the existing H2 + `HeaderAuthFilter` test pattern. They do not replace the existing 167 tests — they add coverage for Agent Workspace Hub behavior. Frontend E2E tests (Playwright or Cypress) are out of scope for this task.

---

### WWA-017 — Validate Deployment Agent Regression Baseline

**Effort:** S

| Action | File |
|--------|------|
| Run full test suite | `mvn test` — confirm all 167 tests pass after Batches 1–5 |
| Fix any regressions | Files vary — fix in place, do not restructure |
| Document baseline | Append pass count to `docs/06-tasks/wwa-migration-plan.md` Phase 3 gate |

**Change description:** This is a verification milestone, not a new feature. If any test fails after shell changes, fix the root cause before proceeding to WWA-018.

---

### WWA-018 — Prepare the Second Agent Pilot

**Effort:** S

| Action | File |
|--------|------|
| Add second agent to registry | `frontend/src/config/agentRegistry.ts` — add `Testing Agent` entry with `enabled: false` initially |
| Define access rules | `docs/00-context/agent-onboarding-checklist.md` — complete checklist for Testing Agent |
| Confirm platform audit participation | `docs/00-context/wwa-audit-taxonomy.md` — add Testing Agent audit event types |

**Change description:** Testing Agent is the obvious pilot candidate. No Testing Agent code is written in this task — this task is preparation and metadata only.

---

### WWA-019 — Onboard the Second Agent Through the Standardized Path

**Effort:** L

| Action | File |
|--------|------|
| Enable second agent in registry | `frontend/src/config/agentRegistry.ts` — set `enabled: true` for Testing Agent |
| Add Testing Agent route | `frontend/src/router/index.ts` — add `/wwa/testing-agent` route |
| Create stub Testing Agent view | `frontend/src/views/TestingAgentView.vue` (create) — minimal placeholder |
| Verify shell shows agent card | `frontend/src/views/WwaHomeView.vue` — confirm registry-driven card renders |
| Verify no custom shell changes needed | `frontend/src/views/WorkspaceLayout.vue` — if changes are required, treat as platform bugs to fix |

**Change description:** Success criterion is that the second agent appears in WWA home and navigation without requiring structural changes to the shell. If shell changes are needed, fix them as defects in WWA-005/WWA-013 before closing this task.

---

### WWA-020 — Review and Refine the Platform Model After the Second Agent

**Effort:** M

| Action | File |
|--------|------|
| Update integration standard | `docs/00-context/multi-agent-integration-standard.md` — promote from v0.1 Draft to v1.0; capture lessons learned |
| Update onboarding checklist | `docs/00-context/agent-onboarding-checklist.md` — refine based on Testing Agent experience |
| Identify remaining gaps | `docs/06-tasks/wwa-migration-plan.md` — append post-pilot gap notes to this section |

**Change description:** This is a retrospective and refinement task. No feature implementation. The output is an updated standard that reflects what actually worked rather than only what was planned.

---

## 3. Implementation Batches

Dependency notation: an arrow (→) means "must complete before."

```
WWA-001
  ├→ WWA-002 ──┐
  │  WWA-004 ──┤
  │  WWA-014   │
  │            ├→ WWA-005 ──┐
  │            │  WWA-006   │
  │            │            ├→ WWA-003 ──┐
  │            └────────────┤            │
  │                         └────────────┴→ WWA-013 → WWA-016 → WWA-017
  ├→ WWA-007 ──→ WWA-008
  ├→ WWA-009 ──→ WWA-010
  ├→ WWA-011
  └→ WWA-012
       All of (WWA-004, WWA-007, WWA-009) → WWA-015 → WWA-018 → WWA-019 → WWA-020
```

### Batch 1 — Product Foundation (serial)

**Tasks:** WWA-001

No parallelism possible. All other work depends on the naming and positioning decision.

**Deliverable:** `docs/00-context/wwa-product-positioning.md`

---

### Batch 2 — Parallel Foundation (parallel after Batch 1)

**Tasks:** WWA-002, WWA-004, WWA-014

All three depend only on WWA-001 and can be done by different engineers simultaneously.

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-002 | `frontend/src/views/WwaHomeView.vue` (create), `frontend/src/router/index.ts` | M |
| WWA-004 | `frontend/src/config/agentRegistry.ts` (create) | M |
| WWA-014 | `docs/04-architecture/architecture.md`, `docs/05-design/design.md` | M |

---

### Batch 3 — Shell and Governance Definitions (parallel, split by stream)

**Tasks:** WWA-005, WWA-006 (after WWA-002 + WWA-004) | WWA-007, WWA-009, WWA-011, WWA-012 (after WWA-001 only — can begin in parallel with Batch 2)

The governance definition tasks (WWA-007, WWA-009, WWA-011, WWA-012) depend only on WWA-001, so they can start at the same time as Batch 2.

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-005 | `frontend/src/views/WorkspaceLayout.vue` | M |
| WWA-006 | `frontend/src/views/WorkspaceLayout.vue`, `frontend/src/config/platformConfig.ts` (create) | S |
| WWA-007 | `src/main/java/.../contracts/enums/PermissionKey.java`, `PermissionResolver.java` | M |
| WWA-009 | `src/main/java/.../domain/audit/AuditLogEntry.java`, `AuditLoggerService.java` | M |
| WWA-011 | `src/main/java/.../contracts/enums/ConfigKey.java`, `frontend/src/views/ConfigAdminView.vue` | S |
| WWA-012 | `docs/00-context/wwa-product-positioning.md`, `frontend/src/router/index.ts` comment | S |

**Coordination note:** WWA-005 and WWA-006 both touch `WorkspaceLayout.vue`. Assign to one engineer or merge carefully.

---

### Batch 4 — Route Change and Governance Implementations (parallel pairs)

**Tasks:** WWA-003 (after WWA-002) | WWA-008 (after WWA-007) | WWA-010 (after WWA-009)

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-003 | `frontend/src/router/index.ts` (3 lines) | S |
| WWA-008 | `frontend/src/views/AccessManagementView.vue`, `AccessGrantController.java` | M |
| WWA-010 | `frontend/src/views/AuditLogView.vue`, `frontend/src/stores/audit.ts`, `AuditLogController.java` | M |

---

### Batch 5 — DA Normalization and Onboarding Checklist

**Tasks:** WWA-013 (after WWA-003 + WWA-005) | WWA-015 (after WWA-004 + WWA-007 + WWA-009)

Both can run in parallel since they touch different files.

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-013 | `frontend/src/views/ReleaseFlowSummaryView.vue`, `ReleaseFlowDetailView.vue`, `WorkspaceLayout.vue` | M |
| WWA-015 | `docs/00-context/agent-onboarding-checklist.md` (create) | S |

---

### Batch 6 — Acceptance Tests

**Tasks:** WWA-016 (after WWA-002, WWA-003, WWA-005, WWA-013)

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-016 | `src/test/java/.../web/WwaShellAccessTest.java` (create) | M |

---

### Batch 7 — Regression Validation and Pilot Prep (parallel)

**Tasks:** WWA-017 (after WWA-013 + WWA-016) | WWA-018 (after WWA-004 + WWA-015)

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-017 | Run `mvn test` — no new files | S |
| WWA-018 | `frontend/src/config/agentRegistry.ts`, `docs/00-context/agent-onboarding-checklist.md` | S |

---

### Batch 8 — Second Agent Pilot (sequential)

**Tasks:** WWA-019 → WWA-020

| Task | Primary file(s) | Effort |
|------|-----------------|--------|
| WWA-019 | `frontend/src/config/agentRegistry.ts`, `frontend/src/router/index.ts`, `frontend/src/views/TestingAgentView.vue` (create) | L |
| WWA-020 | `docs/00-context/multi-agent-integration-standard.md`, `docs/00-context/agent-onboarding-checklist.md` | M |

---

## 4. Phase Gates

### Phase 1 Gate — Platform Shell Established

**Completes after:** Batch 5 (WWA-001 through WWA-013, WWA-014)

Verification:
```bash
# Backend tests unchanged
mvn test
# Expected: 167 tests pass, 0 failures

# Frontend typecheck
cd frontend && npx vue-tsc --noEmit
# Expected: 0 errors
```

Manual checks:
- [ ] `/` redirects to `/wwa/home` (not `/wwa/deployment-agent`)
- [ ] WWA home page shows at least one agent card (Deployment Agent)
- [ ] Topbar breadcrumb shows `WWA > Deployment Agent` when inside DA
- [ ] Sidebar logo reads "WWA Platform" (not "Workspace Hub")
- [ ] `isWwaExpanded` bug is fixed — chevron reflects actual flyout state
- [ ] Flyout nav shows "Workspaces" and "Platform" section headers
- [ ] FinBlock return link is visible in the topbar
- [ ] Deployment Agent release flow summary, detail, upload, decision flows all work

---

### Phase 2 Gate — Governance Boundaries Clarified

**Completes after:** Batch 6 (WWA-007 through WWA-015 + WWA-016)

Verification:
```bash
mvn test
# Expected: ≥ 167 tests pass (new WWA-016 tests add to the count)

cd frontend && npx vue-tsc --noEmit
# Expected: 0 errors
```

Manual checks:
- [ ] New `platform.enter`, `platform.access.manage`, `platform.audit.view` permission keys exist in `PermissionKey.java`
- [ ] `AuditLogEntry` has `agentName`, `targetType`, `targetId`, `sourceSystem` columns
- [ ] H2 test schema includes the new audit columns
- [ ] `AuditLogView.vue` has "Platform Events" and "Agent Activity" tabs
- [ ] `ConfigAdminView.vue` shows "Deployment Agent Configuration" section header
- [ ] `AccessManagementView.vue` title reads "WWA Access Management"
- [ ] `docs/00-context/agent-onboarding-checklist.md` exists and covers all 12 checklist items from the integration standard

---

### Phase 3 Gate — Multi-Agent Model Validated

**Completes after:** Batch 8 (WWA-017 through WWA-020)

Verification:
```bash
mvn test
# Expected: all tests pass

cd frontend && npx vue-tsc --noEmit
# Expected: 0 errors
```

Manual checks:
- [ ] Testing Agent appears as a card on WWA home page
- [ ] Testing Agent appears in the shell flyout nav under "Workspaces"
- [ ] Navigating to Testing Agent does not require any changes to `WorkspaceLayout.vue` beyond what was already done
- [ ] Deployment Agent workflows are fully functional (regression check)
- [ ] `multi-agent-integration-standard.md` is updated to v1.0

---

## 5. Critical Files

Files touched by the most tasks across all batches:

| File | Tasks | Reason |
|------|-------|--------|
| `frontend/src/views/WorkspaceLayout.vue` | WWA-005, WWA-006, WWA-013 | Shell hub: nav, breadcrumbs, logo, FinBlock link, `isWwaExpanded` bug fix |
| `frontend/src/router/index.ts` | WWA-002, WWA-003, WWA-012, WWA-013, WWA-019 | Route definitions and redirect defaults |
| `frontend/src/config/agentRegistry.ts` | WWA-004, WWA-018, WWA-019 | Agent registry: all dynamic nav and home card rendering depends on this |
| `src/main/java/.../contracts/enums/PermissionKey.java` | WWA-007 | Platform permission keys affect `PermissionResolver`, `TaskPermissionService`, and all auth checks |
| `src/main/java/.../domain/auth/PermissionResolver.java` | WWA-007 | Role-to-permission mapping; changes here affect all 167 auth-related tests |
| `src/main/java/.../domain/audit/AuditLogEntry.java` | WWA-009 | New columns require H2 test schema update and `AuditLoggerService` changes |
| `src/test/resources/schema.sql` | WWA-009, WWA-016 | Test database schema must stay in sync with entity changes |
| `docs/00-context/multi-agent-integration-standard.md` | WWA-015, WWA-020 | The authoritative contract for agent onboarding |

---

## 6. Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| `WorkspaceLayout.vue` concurrent edits cause merge conflicts | High | Medium | Assign all WorkspaceLayout changes in Batch 3 to one engineer; split other Batch 3 work to separate engineers |
| `AuditLogEntry` schema change breaks existing H2 tests | Medium | High | Add new columns as nullable; update `src/test/resources/schema.sql` in the same commit as the entity change; verify `mvn test` before merging |
| `PermissionResolver` change causes silent auth regressions | Medium | High | Add new platform permission keys only; do not remove or rename existing keys until Phase 3; run `mvn test` after every change |
| Default route change (`/` → `/wwa/home`) breaks bookmarked URLs | Low | Low | Keep legacy redirects for `/release-flows` and `/release-flows/:id` (already present in router) |
| WWA home page is slow to render (agent cards async) | Low | Low | Use static registry in Phase 1; no API calls needed for home page |
| Second agent (WWA-019) requires unexpected shell changes | Medium | High | If shell changes are needed, fix as defects in WWA-005/WWA-013 regression, not as normal scope for WWA-019 |
| `isWwaExpanded` bug causes visual glitch until fixed | Current bug | Low | Fix in WWA-005 Batch 3 immediately; it is a one-line declaration |
| Template Management prematurely refactored | Low | Medium | WWA-012 decision record must be visible to all engineers before Batch 3 begins |

---

## 7. Effort Summary

| Task | Title | Phase | Batch | Effort |
|------|-------|-------|-------|--------|
| WWA-001 | Confirm product positioning | 1 | 1 | S |
| WWA-002 | Add WWA home page | 1 | 2 | M |
| WWA-003 | Change default routing | 1 | 4 | S |
| WWA-004 | Agent registry model | 1 | 2 | M |
| WWA-005 | Shell navigation refactor | 1 | 3 | M |
| WWA-006 | Return-to-FinBlock pattern | 1 | 3 | S |
| WWA-007 | Platform vs agent access definition | 2 | 3 | M |
| WWA-008 | Reframe Access Management | 2 | 4 | M |
| WWA-009 | Platform audit model | 2 | 3 | M |
| WWA-010 | Split platform audit from agent activity | 2 | 4 | M |
| WWA-011 | Config classification | 2 | 3 | S |
| WWA-012 | Template scope decision | 2 | 3 | S |
| WWA-013 | Normalize Deployment Agent as first workspace | 1 | 5 | M |
| WWA-014 | Update supporting documentation | 1 | 2 | M |
| WWA-015 | New-agent onboarding checklist | 2 | 5 | S |
| WWA-016 | Acceptance tests for shell behavior | 3 | 6 | M |
| WWA-017 | Validate DA regression baseline | 3 | 7 | S |
| WWA-018 | Prepare second agent pilot | 3 | 7 | S |
| WWA-019 | Onboard second agent | 3 | 8 | L |
| WWA-020 | Review and refine platform model | 3 | 8 | M |

**Totals:** 7 S · 10 M · 1 L · 2 M (documentation)

---

## 8. File Existence Verification

All file paths referenced in this plan have been verified against the current repository state as of 2026-03-26.

**Existing files modified by this plan:**

| File | Exists |
|------|--------|
| `frontend/src/router/index.ts` | ✓ |
| `frontend/src/views/WorkspaceLayout.vue` | ✓ |
| `frontend/src/views/ReleaseFlowSummaryView.vue` | ✓ |
| `frontend/src/views/ReleaseFlowDetailView.vue` | ✓ |
| `frontend/src/views/AuditLogView.vue` | ✓ |
| `frontend/src/views/AccessManagementView.vue` | ✓ |
| `frontend/src/views/ConfigAdminView.vue` | ✓ |
| `src/main/java/.../contracts/enums/PermissionKey.java` | ✓ |
| `src/main/java/.../domain/auth/PermissionResolver.java` | ✓ |
| `src/main/java/.../domain/audit/AuditLogEntry.java` | ✓ |
| `src/main/java/.../domain/audit/AuditLoggerService.java` | ✓ |
| `src/main/java/.../contracts/enums/ConfigKey.java` | ✓ |
| `src/main/java/.../web/controller/AccessGrantController.java` | ✓ |
| `src/main/java/.../web/controller/AuditLogController.java` | ✓ |
| `docs/04-architecture/architecture.md` | ✓ |
| `docs/05-design/design.md` | ✓ |
| `docs/00-context/multi-agent-integration-standard.md` | ✓ |

**New files created by this plan:**

| File | Status |
|------|--------|
| `frontend/src/views/WwaHomeView.vue` | To create (WWA-002) |
| `frontend/src/config/agentRegistry.ts` | To create (WWA-004) |
| `frontend/src/config/platformConfig.ts` | To create (WWA-006) |
| `frontend/src/views/TestingAgentView.vue` | To create (WWA-019) |
| `src/test/java/.../web/WwaShellAccessTest.java` | To create (WWA-016) |
| `docs/00-context/wwa-product-positioning.md` | To create (WWA-001) |
| `docs/00-context/wwa-permission-taxonomy.md` | To create (WWA-007) |
| `docs/00-context/wwa-audit-taxonomy.md` | To create (WWA-009) |
| `docs/00-context/agent-onboarding-checklist.md` | To create (WWA-015) |
