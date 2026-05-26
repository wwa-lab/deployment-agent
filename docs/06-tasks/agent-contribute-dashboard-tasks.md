# Implementation Task Breakdown: Agent Contribute Dashboard

**Date:** 2026-05-26
**Status:** Backfilled / Implemented
**Source Design:** `docs/05-design/agent-contribute-dashboard-design.md`

---

## 1. Overview

Agent Contribute Dashboard has been implemented as a WWA shared-control dashboard. This task breakdown backfills the SDD task layer and records what is complete versus what remains before production release.

**Delivery objective:** Provide a concise dashboard for seven-stage SDLC contribution coverage, admin-maintained status, and stage-level Confluence guidance/feedback links.

---

## 2. Workstreams

1. SDD documentation.
2. Frontend dashboard data and route.
3. Frontend dashboard UI.
4. Backend persisted status API.
5. Tests and verification.
6. Production readiness follow-up.

---

## 3. Task Details

### ACD-TASK-001: Create SDD Requirement and Story Baseline

- **Objective:** Capture product goals, scope, actors, and user stories.
- **Owner type:** product / platform
- **Priority:** Must
- **Status:** Complete
- **Artifacts:** `docs/01-requirements/agent-contribute-dashboard-requirement.md`, `docs/02-user-stories/agent-contribute-dashboard-user-stories.md`

### ACD-TASK-002: Create SDD Spec, Architecture, and Design

- **Objective:** Capture implementation-facing requirements, architecture decisions, and detailed design.
- **Owner type:** platform
- **Priority:** Must
- **Status:** Complete
- **Artifacts:** `docs/03-spec/agent-contribute-dashboard-spec.md`, `docs/04-architecture/agent-contribute-dashboard-architecture.md`, `docs/05-design/agent-contribute-dashboard-design.md`

### ACD-TASK-003: Register Dashboard Route and Navigation Entry

- **Objective:** Make the dashboard reachable under WWA shared controls.
- **Owner type:** frontend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `frontend/src/router/index.ts`, `frontend/src/config/agentRegistry.ts`

### ACD-TASK-004: Add Static Dashboard Baseline Data

- **Objective:** Define seven stages, statuses, owner model, contribution items, gates, and resource links.
- **Owner type:** frontend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `frontend/src/config/agentContributionDashboard.json`

### ACD-TASK-005: Build Dashboard View

- **Objective:** Render summary metrics, SDLC coverage map, filterable matrix, selected-stage panel, Confluence links, admin status controls, accountability, and contribution coverage.
- **Owner type:** frontend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `frontend/src/views/AgentContributionDashboardView.vue`

### ACD-TASK-006: Add Frontend API Contract

- **Objective:** Provide typed calls for reading and updating dashboard stage status overrides.
- **Owner type:** frontend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `frontend/src/api/agentContributionDashboard.ts`

### ACD-TASK-007: Add Backend Status Configuration Contract

- **Objective:** Add config key and DTO for dashboard status overrides.
- **Owner type:** backend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `ConfigKey`, `AgentContributionDashboardStatusDto`

### ACD-TASK-008: Add Backend Status Service and Controller

- **Objective:** Expose read/update APIs with allowed key/status validation and admin authorization.
- **Owner type:** backend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `AgentContributionDashboardConfigService`, `AgentContributionDashboardController`

### ACD-TASK-009: Add Tests

- **Objective:** Validate dashboard data, UI constraints, route/API wiring, admin status update, invalid payload rejection, unknown stage key rejection, and non-admin forbidden behavior.
- **Owner type:** QA / frontend / backend
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `frontend/tests/agentContributionDashboard.test.mjs`, `AgentContributionDashboardControllerTest`
- **Notes:** Frontend tests run via `npm test` (Node built-in runner). Backend has 5 cases: read default, admin persist, invalid status value, unknown stage key, non-admin forbidden.

### ACD-TASK-010: Update Changelog

- **Objective:** Record user-facing dashboard capability in Unreleased changelog.
- **Owner type:** platform
- **Priority:** Must
- **Status:** Complete
- **Implementation:** `CHANGELOG.md`

### ACD-TASK-011: Replace Placeholder Confluence Links

- **Objective:** Replace `confluence.example.com` URLs with real internal Confluence guideline and feedback pages.
- **Owner type:** product / platform
- **Priority:** Must before production release
- **Status:** Pending
- **Notes:** No code change required if URL shape remains simple href replacement.

### ACD-TASK-012: Production Readiness Verification

- **Objective:** Run complete frontend build, targeted backend tests, and visual smoke check before merge/release.
- **Owner type:** QA / platform
- **Priority:** Must
- **Status:** In progress
- **Done definition:**
  - `cd frontend && npm test` passes (4 Node tests).
  - `cd frontend && npm run build` passes (vue-tsc + vite build).
  - `mvn test -Dtest=AgentContributionDashboardControllerTest` passes (5 cases).
  - Full backend regression (`mvn test`) passes with no new failures.
  - Visual smoke: open `/wwa/agent-contribute-dashboard`, confirm seven stages, admin status panel, Confluence links.
  - ACD-TASK-011 complete (real Confluence URLs in place).
- **Blocking on:** ACD-TASK-011 (Confluence link replacement).

---

## 4. Dependency Plan

- Critical path: ACD-TASK-001 -> ACD-TASK-002 -> ACD-TASK-003/004/007 -> ACD-TASK-005/006/008 -> ACD-TASK-009 -> ACD-TASK-010 -> ACD-TASK-011/012.
- Frontend and backend implementation streams can run in parallel after the spec/design layer exists.
- Confluence link replacement is independent of code once real URLs are known.

---

## 5. Risks / Blockers

- Real Confluence URLs are not yet available.
- Existing implementation was built before the SDD baseline; future changes should not repeat this ordering.
- Full backend regression has not been rerun after the latest UI-only simplification.

---

## 6. Open Questions

These are the canonical pending decisions for this feature. Other SDD documents reference this section rather than maintaining separate lists.

| # | Question | Status |
|---|---|---|
| OQ-1 | Replace `confluence.example.com` placeholder URLs with real internal pages. | Pending (tracked as ACD-TASK-011) |
| OQ-2 | Should Configuration Management expose dashboard status as a first-class admin item, or keep it in the dashboard panel? | Open |
| OQ-3 | Should Discovery and Maintenance implementation become separate SDD changes when work starts? | Open |
| OQ-4 | Should future dashboard content (owners, contribution items) become backend-configurable if it changes frequently? | Open |
| OQ-5 | Should Confluence link ownership live with each stage owner or a central platform owner? | Open |
