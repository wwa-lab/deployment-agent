# Implementation Task Breakdown: Testing Agent

## Overview

This document breaks the Testing Agent design into implementation-ready tasks. The scope is well-bounded: add a second agent workspace that mirrors Deployment Agent using thin controller delegation, duplicated frontend modules, and agent-scoped data isolation.

**Delivery approach: Duplicate First, Extract Later**

This task breakdown uses Approach B — duplicate existing frontend modules (API, store, views) rather than extracting shared components. This eliminates the HIGH risk of breaking Deployment Agent during shared component extraction. Shared component extraction is deferred to a follow-up PR as pure refactoring with no functional changes.

**Delivery objective**
- Add Testing Agent as the second workspace under WWA Agent Workspace Hub
- Achieve data isolation via `Request.agent = "testing-agent"`
- Zero impact on existing Deployment Agent functionality — no existing files are modified except `agentRegistry.ts` and `router/index.ts`

**Planning assumptions**
- All domain services, entities, repositories, and shared capabilities already exist and are agent-agnostic
- The `Request.agent` column already exists (`V6__add_request_agent_column.sql`)
- The agent registry already has a commented placeholder for Testing Agent
- `ReleaseFlowService` already supports `agent` as a filter parameter
- `ImportService.importFile()` already accepts `agent` as a parameter
- No database migrations are needed
- No new domain logic is needed

---

## Source Design

**System name:** Testing Agent (second workspace under WWA Agent Workspace Hub)

**Design scope summary**
- 3 thin backend controllers delegating to existing services
- Agent identity constants (backend + frontend)
- Duplicated frontend API modules, store, and views (with Testing Agent imports/config)
- Duplicated Testing Agent views (summary + detail)
- Agent registry and router updates
- Backend controller tests and data isolation integration tests

---

## Workstreams

### Major Implementation Streams

1. Backend controller layer
2. Frontend API and store infrastructure (duplicated modules)
3. Testing Agent views (duplicated from Deployment Agent)
4. Agent registry and routing
5. Testing and verification

### Recommended Sequencing

1. Create agent identity constants (backend + frontend)
2. Create backend controllers and write tests
3. Create frontend API client, duplicated API modules, and duplicated store
4. Create Testing Agent views (duplicated from DA, modified imports/titles/routes)
5. Update agent registry and router
6. Full verification (backend tests + frontend build + manual smoke test)

### Parallel Work Opportunities

- Backend tasks (TA-TASK-001 through TA-TASK-005) can run in parallel with frontend infrastructure tasks (TA-TASK-006 through TA-TASK-009)
- Agent registry update (TA-TASK-008) can be done anytime
- Test tasks can begin preparing fixtures while implementation is in progress

---

## Task Details

### TA-TASK-001: Create Agent Identity Constants

- **Objective**: Define agent identity strings as constants to prevent string literal scatter.
- **Scope**:
  - Backend: Create `AgentId.java` in `src/main/java/com/wwa/deploymentagent/contracts/` with `DEPLOYMENT_AGENT = "deployment-agent"` and `TESTING_AGENT = "testing-agent"`.
  - Frontend: Create `agentId.ts` in `frontend/src/config/` with `AGENT_ID.DEPLOYMENT` and `AGENT_ID.TESTING`.
- **Dependencies**: None
- **Owner type**: backend + frontend
- **Priority**: Must
- **Estimated size**: XS (~20 lines total)
- **Notes**: These constants are referenced by all subsequent tasks. Keep the class/module minimal — no logic, just values.

### TA-TASK-002: Create TestingAgentReleaseFlowController

- **Objective**: Expose `/api/testing-agent/release-flows` endpoints that delegate to existing `ReleaseFlowService` with agent-scoped filtering.
- **Scope**:
  - `GET /api/testing-agent/release-flows` — delegates to `ReleaseFlowService.listStitchedSummaries()` with `agent = TESTING_AGENT`. MUST override any client-supplied `agent` parameter.
  - `GET /api/testing-agent/release-flows/{id}` — delegates to `ReleaseFlowService.findByIdWithFullHierarchy()`.
  - Apply same `@PreAuthorize` annotations as `ReleaseFlowController`.
- **Dependencies**: TA-TASK-001
- **Owner type**: backend
- **Priority**: Must
- **Estimated size**: S (~50 lines)
- **Notes**: The controller must NOT contain any business logic. Agent parameter override is a security requirement to prevent cross-agent data leakage.

### TA-TASK-003: Create TestingAgentUploadController

- **Objective**: Expose `/api/testing-agent/upload` endpoint that delegates to existing `ImportService` with `agent = "testing-agent"` tagging.
- **Scope**:
  - `POST /api/testing-agent/upload` — delegates to `ImportService.importExcel()` with `agent = TESTING_AGENT` injected. Overrides any client-supplied agent value.
  - `GET /api/testing-agent/upload/template` — returns the same static template file as the deployment agent upload controller.
  - Apply same `@PreAuthorize` annotations as `UploadController`.
- **Dependencies**: TA-TASK-001
- **Owner type**: backend
- **Priority**: Must
- **Estimated size**: S (~40 lines)
- **Notes**: Verify that `ImportService` already accepts `agent` as a parameter. If not, add agent parameter support to the import context (minor service change).

### TA-TASK-004: Create TestingAgentTaskController

- **Objective**: Expose `/api/testing-agent/tasks` endpoints that delegate to existing task services.
- **Scope**:
  - `PUT /api/testing-agent/tasks/{id}/input` → `TaskService.updateInput()`
  - `GET /api/testing-agent/tasks/{id}/executions` → `TaskExecutionHistoryService.findByTaskId()`
  - `POST /api/testing-agent/tasks/{id}/start-manual` → `TaskService.startManual()`
  - `POST /api/testing-agent/tasks/{id}/record-result` → `RecordResultService.recordResult()`
  - `POST /api/testing-agent/tasks/{id}/submit-auto` → `AutoExecutionService.submitAuto()`
  - `POST /api/testing-agent/tasks/{id}/decision` → `DecisionEngine.applyDecision()`
  - Pass `agentName = TESTING_AGENT` to audit context for all audit-producing operations.
  - Apply same `@PreAuthorize` annotations as `TaskController` and `DecisionController`.
- **Dependencies**: TA-TASK-001
- **Owner type**: backend
- **Priority**: Must
- **Estimated size**: S (~80 lines)
- **Notes**: Task-level endpoints do not need agent filtering — tasks are accessed by ID and inherit agent from parent request. The audit logger must receive the correct agent name.

### TA-TASK-005: Write Backend Controller Tests and Data Isolation Tests

- **Objective**: Verify Testing Agent controllers delegate correctly and data isolation works.
- **Scope**:
  - `TestingAgentReleaseFlowControllerTest` — verify list returns only testing-agent flows, verify agent parameter override
  - `TestingAgentUploadControllerTest` — verify upload creates requests with `agent = "testing-agent"`
  - `TestingAgentTaskControllerTest` — verify task operations delegate correctly, verify audit entries have `agentName = "testing-agent"`
  - `TestingAgentDataIsolationTest` — cross-agent integration test:
    1. Upload via Testing Agent → list via Testing Agent → flow appears
    2. Upload via Testing Agent → list via Deployment Agent → flow does NOT appear (unless DA data also exists)
    3. Upload same project via both agents → each agent sees only its own requests
    4. Legacy null-agent data → NOT visible in Testing Agent
- **Dependencies**: TA-TASK-002, TA-TASK-003, TA-TASK-004
- **Owner type**: QA / backend
- **Priority**: Must
- **Estimated size**: M (~200 lines across 4 test files)
- **Notes**: Follow existing test patterns in `src/test/java/com/wwa/deploymentagent/web/`. Use `@SpringBootTest` with H2 for integration tests. Target 80%+ coverage on new controller code.

### TA-TASK-006: Create Frontend API Client and Duplicated API Modules

- **Objective**: Set up the API infrastructure for Testing Agent frontend.
- **Scope**:
  - Create `testingAgentClient.ts` — axios instance with `baseURL: '/api/testing-agent'` and same interceptors as existing `client.ts`.
  - Duplicate `releaseFlows.ts` → `testingAgentReleaseFlows.ts` — replace `import client from './client'` with `import client from './testingAgentClient'`.
  - Duplicate `upload.ts` → `testingAgentUpload.ts` — same client swap.
  - Duplicate `tasks.ts` → `testingAgentTasks.ts` — same client swap.
- **Dependencies**: TA-TASK-001
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: S (~220 lines, mostly duplicated)
- **Notes**: Existing deployment agent API modules are NOT modified. The duplication is intentional (Approach B) and will be replaced by a shared API factory in a follow-up refactoring PR.

### TA-TASK-007: Create Duplicated Testing Agent Store

- **Objective**: Set up a dedicated Pinia store for Testing Agent release flow state.
- **Scope**:
  - Duplicate `releaseFlow.ts` → `testingAgentReleaseFlow.ts`.
  - Change store ID: `'testingAgentReleaseFlow'` (was `'releaseFlow'`).
  - Change API imports: from `../api/testingAgentReleaseFlows` and `../api/testingAgentUpload` (was `../api/releaseFlows` and `../api/upload`).
  - Export `useTestingAgentReleaseFlowStore`.
- **Dependencies**: TA-TASK-006
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: S (~130 lines, duplicated)
- **Notes**: Existing `releaseFlow.ts` is NOT modified. Each store instance has an independent Pinia ID preventing state collision. The duplication will be replaced by a shared store factory in a follow-up refactoring PR.

### TA-TASK-008: Update Agent Registry

- **Objective**: Register Testing Agent so it appears on the WWA Home page and sidebar flyout.
- **Scope**:
  - Replace the commented Testing Agent placeholder in `agentRegistry.ts` with an enabled entry:
    ```
    key: 'testing-agent', name: 'Testing Agent', enabled: true, category: 'testing'
    ```
- **Dependencies**: None
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: XS (~8 lines changed)
- **Notes**: This is a one-line change (uncomment and set `enabled: true`). The home page and flyout already render from the registry — no shell code changes needed.

### TA-TASK-009: Add Testing Agent Routes

- **Objective**: Register Testing Agent routes in Vue Router.
- **Scope**:
  - Add `/wwa/testing-agent` route → `TestingAgentSummaryView` with meta `{ section: 'testing-agent', sectionTitle: 'Testing Agent' }`
  - Add `/wwa/testing-agent/release-flows/:id` route → `TestingAgentDetailView` with same meta
  - Use lazy loading (`() => import(...)`)
- **Dependencies**: TA-TASK-012, TA-TASK-013
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: XS (~10 lines)
- **Notes**: Routes must be children of the `/wwa` parent route.

### TA-TASK-010: Create TestingAgentSummaryView (Duplicated)

- **Objective**: Create the Testing Agent summary page by duplicating the Deployment Agent summary view.
- **Scope**:
  - Duplicate `ReleaseFlowSummaryView.vue` → `TestingAgentSummaryView.vue` in `frontend/src/views/`
  - Replace `useReleaseFlowStore` → `useTestingAgentReleaseFlowStore`
  - Replace API imports → Testing Agent API modules (`testingAgentReleaseFlows`, `testingAgentUpload`)
  - Replace page title → `"Testing Agent"`
  - Replace page description → testing-specific introductory text
  - Replace detail route path → `/wwa/testing-agent/release-flows/`
- **Dependencies**: TA-TASK-007
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: M (~496 lines, duplicated with modifications)
- **Notes**: Existing `ReleaseFlowSummaryView.vue` is NOT modified. Zero DA regression risk.

### TA-TASK-011: Create TestingAgentDetailView (Duplicated)

- **Objective**: Create the Testing Agent detail page by duplicating the Deployment Agent detail view.
- **Scope**:
  - Duplicate `ReleaseFlowDetailView.vue` → `TestingAgentDetailView.vue` in `frontend/src/views/`
  - Replace `useReleaseFlowStore` → `useTestingAgentReleaseFlowStore`
  - Replace API imports → Testing Agent API modules (`testingAgentReleaseFlows`, `testingAgentTasks`)
  - Replace page title / breadcrumb → `"Testing Agent"`
  - Replace summary route path → `/wwa/testing-agent`
- **Dependencies**: TA-TASK-007
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: L (~600 lines, duplicated with modifications)
- **Notes**: Existing `ReleaseFlowDetailView.vue` is NOT modified. Dialog components are reused as-is. Zero DA regression risk.

### TA-TASK-012: Frontend Build Verification

- **Objective**: Verify the complete frontend builds and typechecks without errors.
- **Scope**:
  - Run `cd frontend && npm run build` — must succeed
  - Run `cd frontend && npx vue-tsc --noEmit` — must succeed
  - Verify no TypeScript errors in new files
- **Dependencies**: TA-TASK-008, TA-TASK-009, TA-TASK-010, TA-TASK-011
- **Owner type**: frontend / QA
- **Priority**: Must
- **Estimated size**: XS

### TA-TASK-013: End-to-End Verification

- **Objective**: Verify the complete Testing Agent workflow works end-to-end.
- **Scope**:
  - Start backend (`mvn spring-boot:run -Dspring-boot.run.profiles=local`)
  - Start frontend (`cd frontend && npm run dev`)
  - Verify:
    1. Testing Agent card appears on WWA Home page
    2. Testing Agent appears in sidebar flyout
    3. Navigate to `/wwa/testing-agent` — Testing Agent summary loads
    4. Upload Excel via Testing Agent — request created with `agent = "testing-agent"`
    5. Release flow appears in Testing Agent summary
    6. Release flow does NOT appear in Deployment Agent summary (unless DA data also exists)
    7. Navigate to Testing Agent detail page — tasks visible
    8. Task actions work (edit, run, record result, decision)
    9. Audit log shows entries with `agentName = "testing-agent"`
    10. All existing Deployment Agent functionality unchanged
- **Dependencies**: All previous tasks
- **Owner type**: QA
- **Priority**: Must
- **Estimated size**: M (manual testing)
- **Notes**: This is the final acceptance gate before the feature is considered complete.

---

## Dependency Plan

### Critical Path

```
TA-TASK-001 → TA-TASK-002/003/004 → TA-TASK-005
                                         ↓
TA-TASK-001 → TA-TASK-006 → TA-TASK-007 → TA-TASK-010/011 → TA-TASK-008/009 → TA-TASK-012 → TA-TASK-013
```

### Prerequisite Clusters

| Cluster | Tasks | Description |
|---|---|---|
| **Backend controllers** | TA-TASK-001, 002, 003, 004, 005 | Agent constants + 3 controllers + tests |
| **Frontend infrastructure** | TA-TASK-001, 006, 007 | Duplicated API modules + store |
| **Testing Agent views** | TA-TASK-010, 011 | Duplicated summary + detail views |
| **Routing** | TA-TASK-008, 009 | Registry + routes |
| **Verification** | TA-TASK-012, 013 | Build check + E2E |

### Parallel Workstreams

- **Backend** (TA-TASK-001 → 002/003/004 → 005) can run in parallel with **Frontend infrastructure** (TA-TASK-006 → 007)
- **Agent registry** (TA-TASK-008) can be done anytime
- **Backend tests** (TA-TASK-005) can run in parallel with frontend work

---

## Task Summary

| Task | Description | Size | Priority | Owner |
|---|---|---|---|---|
| TA-TASK-001 | Agent identity constants | XS | Must | backend + frontend |
| TA-TASK-002 | TestingAgentReleaseFlowController | S | Must | backend |
| TA-TASK-003 | TestingAgentUploadController | S | Must | backend |
| TA-TASK-004 | TestingAgentTaskController | S | Must | backend |
| TA-TASK-005 | Backend controller + isolation tests | M | Must | QA / backend |
| TA-TASK-006 | Frontend API client + duplicated API modules | S | Must | frontend |
| TA-TASK-007 | Duplicated Testing Agent store | S | Must | frontend |
| TA-TASK-008 | Update agent registry | XS | Must | frontend |
| TA-TASK-009 | Add Testing Agent routes | XS | Must | frontend |
| TA-TASK-010 | TestingAgentSummaryView (duplicated) | M | Must | frontend |
| TA-TASK-011 | TestingAgentDetailView (duplicated) | L | Must | frontend |
| TA-TASK-012 | Frontend build verification | XS | Must | QA |
| TA-TASK-013 | End-to-end verification | M | Must | QA |

**Total: 13 tasks** — 3 XS, 4 S, 3 M, 1 L, 2 M (verification)

---

## Risks / Blockers

| Risk | Severity | Status |
|---|---|---|
| ~~Shared component extraction breaks existing DA views~~ | ~~HIGH~~ | **ELIMINATED** — using Approach B (duplicate, don't extract) |
| ~~Store factory may not support all current store features~~ | ~~MEDIUM~~ | **ELIMINATED** — duplicating store instead of factory |
| ~~Dialog components have hardcoded API references~~ | ~~MEDIUM~~ | **ELIMINATED** — duplicated views use their own imports |
| `ImportService` may not accept `agent` as a parameter today | LOW | Confirmed: `ImportService.importFile()` already accepts agent at line 70 |
| Concurrent navigation between agents may cause state issues | LOW | Each store has a unique Pinia ID; test explicitly |
| `matchesContains` uses substring matching for agent filter | LOW | Acceptable for MVP — no false matches with current agent values |

---

## Follow-Up Tasks (Separate PR — Pure Refactoring)

After Testing Agent is working, the following refactoring tasks should be completed in a separate PR to eliminate duplication:

| ID | Task | Description |
|---|---|---|
| FU-001 | Extract `AgentSummaryView.vue` | Extract shared component from `ReleaseFlowSummaryView.vue` and `TestingAgentSummaryView.vue`; both become thin wrappers |
| FU-002 | Extract `AgentDetailView.vue` | Extract shared component from `ReleaseFlowDetailView.vue` and `TestingAgentDetailView.vue`; both become thin wrappers |
| FU-003 | Extract `agentApiFactory.ts` | Replace duplicated API modules with a factory that accepts an axios instance |
| FU-004 | Extract `agentReleaseFlowFactory.ts` | Replace duplicated stores with a factory that accepts a store ID and API functions |
| FU-005 | Refactor dialog components | Accept API functions via props instead of hardcoded imports (required for FU-001/002) |

These are pure refactoring tasks with **no functional changes** — lower risk when done independently after the feature is proven working.

---

## Open Questions

1. Should an agent filter dropdown be added to the Audit Log page as part of this delivery? (Recommendation: optional enhancement, not blocking)
2. When should the follow-up refactoring PR (FU-001 through FU-005) be scheduled? (Recommendation: immediately after Testing Agent is merged and verified)
