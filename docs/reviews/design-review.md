# Design Review — Deployment Agent Frontend (2026-03-23)

## Scope

Review of the current frontend implementation against the updated user stories (1–20), CHANGELOG, and architectural intent. Covers code quality, design gaps, and alignment issues.

---

## Overall Assessment

The frontend is well-structured with clear separation of concerns (views / components / stores / API / types). The core deployment workflow (upload → summary → detail → task actions → decisions) is solid. However, several design gaps emerged as the scope expanded beyond the original 11 user stories.

**Verdict**: **PASS with issues** — core workflow is functional; template management and navigation have known gaps that need resolution before production.

---

## HIGH Severity Issues

### H1. Template Management has no backend API — all operations are local-only

**Location**: `frontend/src/views/TemplateManagementView.vue`, `frontend/src/api/` (no `templates.ts`)

**Problem**: Template CRUD (create, edit, clone, delete) and task authoring all mutate local `ref()` state. No API service file exists. All template data is lost on page refresh.

**Impact**: User Story 12 and 13 are frontend-only — no persistence, no multi-user visibility, no audit trail.

**Recommendation**:
1. Define backend REST endpoints for templates (`POST/GET/PUT/DELETE /api/deployment-agent/templates`).
2. Create `frontend/src/api/templates.ts` with CRUD functions.
3. Add a `template` Pinia store similar to `releaseFlow` store.
4. Remove hardcoded sample template data from the view component.

---

### H2. `isWwaExpanded` variable referenced but not defined in WorkspaceLayout

**Location**: `frontend/src/views/WorkspaceLayout.vue` (lines ~175, ~196)

**Problem**: The template references `isWwaExpanded` but the actual reactive variable is `isWwaFlyoutOpen`. This causes a silent rendering failure — the flyout expansion indicator and CSS class never activate.

**Recommendation**: Replace `isWwaExpanded` with `isWwaFlyoutOpen` in the template bindings.

---

## MEDIUM Severity Issues

### M1. No route-level role guards — authorization is component-level only

**Location**: `frontend/src/router/index.ts`

**Problem**: The router `beforeEach` guard only checks authentication (logged in or not). Role-based access is enforced purely at the component level via computed properties like `canEdit`. This means:
- All authenticated users can navigate to any route via URL
- Authorization relies entirely on UI disabling, not route blocking
- Template Management has no role check at all

**Recommendation**:
1. Add `meta.roles` to protected routes.
2. Extend the router guard to check `userStore.role` against `to.meta.roles`.
3. Redirect unauthorized users to a 403 page or the summary view.

Note: This is a defense-in-depth concern. Backend API authorization is the primary control.

---

### M2. Template Management view contains ~260 lines of hardcoded sample data

**Location**: `frontend/src/views/TemplateManagementView.vue` (lines ~11–268)

**Problem**: Four sample templates (tpl-001 through tpl-004) are defined inline in the component as initial state. This is acceptable for prototyping but should be removed before production.

**Recommendation**: Extract sample data to a separate fixture file for dev/test, or remove entirely once the API integration is in place.

---

### M3. No Pinia store for Template Management

**Problem**: Unlike releaseFlow, task, config, and audit — which all have dedicated Pinia stores — template management operates directly in the view component's local state. This makes it impossible for other components to access template data.

**Recommendation**: Create `frontend/src/stores/template.ts` following the same pattern as other stores.

---

## LOW Severity Issues

### L1. Status badge CSS class mapping is a hardcoded Record, not derived from types

**Location**: `frontend/src/views/ReleaseFlowDetailView.vue` (lines ~37–51)

**Problem**: The `statusBadgeClass` function uses a hardcoded `Record<string, string>` that must be manually kept in sync with the `TaskStatus` type.

**Recommendation**: Generate the mapping from the type union or add a comment referencing the type to reduce drift risk.

---

### L2. Error message extraction in API client may miss backend error formats

**Location**: `frontend/src/api/client.ts` (lines ~22–27)

**Problem**: The interceptor checks `data.message` then `data.error` then `error.message`. If the backend returns errors in a different shape (e.g., Spring Boot's `{timestamp, status, error, message, path}`), the extraction may not pick the right field.

**Recommendation**: Add extraction for Spring Boot's default error response structure explicitly.

---

### L3. ConfigAdminView has no explicit "read-only" banner for non-admin users

**Location**: `frontend/src/views/ConfigAdminView.vue`

**Problem**: Non-DEVOPS_ADMIN users can view the page but edit buttons are simply hidden. There's no message explaining why edits are unavailable.

**Recommendation**: Add a banner or inline text: "Configuration is read-only. Contact a DevOps Admin to make changes."

---

## Alignment Check: User Stories vs Implementation

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| 1 | Workspace Access | Implemented | Two-level nav with flyout |
| 2 | Upload via Excel | Implemented | Upload dialog + template download |
| 3 | Create/Update Release Flow | Implemented | Backend import flow |
| 4 | Release Flow Summary | Implemented | With stage status columns (Story 20) |
| 5 | Release Flow Details | Implemented | With rundown panel (Story 16) |
| 6 | Task Details and Results | Implemented | View Result wired to execution history |
| 7 | Task Input Editing | Implemented | Result submission moved into Edit dialog |
| 8 | Decision Control | Implemented | Approve/Reject/Rerun/Skip |
| 9 | Audit Logging | Implemented | Backend audit + activity dialog |
| 10 | Configuration Management | Implemented | Component workspace for Jenkins/Ansible/callback |
| 11 | Audit Log View | Implemented | Action-record view with Staff Id search |
| 12 | Template Management CRUD | **Partial** | Frontend only — no backend API |
| 13 | Template Task Authoring | **Partial** | Frontend only — no persistence |
| 14 | WWA Platform Navigation | Implemented | Possible `isWwaExpanded` bug (H2) |
| 15 | Task Activity History | Implemented | Combined audit + execution dialog |
| 16 | Rundown Information | Implemented | Edit dialog + request-level actions |
| 17 | Critical Task Gate | Implemented | Badge + tooltip; backend gate logic pending verification |
| 18 | Task Action Permissions | Implemented | Owner/DEVOPS_ADMIN with tooltips |
| 19 | Execution Mix + Category | Implemented | Counts + percentages + category column |
| 20 | Stage Status on Summary | Implemented | SIT/UAT/PROD columns on summary table |

---

## Architecture Observations

### Strengths
- Clean separation: API layer → Pinia stores → Views → Components
- Consistent pattern: each domain has its own store, API module, and types
- Good error resilience in TaskActivityDialog (partial load with warnings)
- Polling lifecycle properly managed (start on mount, stop on unmount)
- Type-safe with full TypeScript interfaces

### Risks
- **Template Management is an island** — it doesn't follow the established pattern (no store, no API, no backend). If this ships without backend integration, users will lose all template work on refresh.
- **No offline/draft concept** — the frontend has no general mechanism for local drafts. Template Management created one ad-hoc, but it's not reusable.
- **Backend contract uncertainty** — Template API, critical gate enforcement, and Team Book auth provider are all pending. The frontend is ahead of the backend on these features.

---

## Recommended Next Steps (Priority Order)

1. **Fix H2** — Replace `isWwaExpanded` with `isWwaFlyoutOpen` in WorkspaceLayout.vue
2. **Design and implement Template API** — backend endpoints + frontend API service + Pinia store
3. **Add route-level role guards** — extend router `beforeEach` with role checks
4. **Verify critical task gate** — confirm backend enforces the workflow block
5. **Remove hardcoded template sample data** — replace with API-fetched data
6. **Add read-only banner** — for non-admin users on Config and Template pages
