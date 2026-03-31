# Implementation Task Breakdown: Testing Agent

## Overview

This document breaks the Testing Agent design into implementation-ready tasks. The scope is well-bounded: add a second agent workspace that mirrors Deployment Agent using thin controller delegation, shared component extraction, and agent-scoped data isolation.

**Delivery objective**
- Add Testing Agent as the second workspace under WWA Agent Workspace Hub
- Achieve data isolation via `Request.agent = "testing-agent"`
- Zero impact on existing Deployment Agent functionality
- Minimize code duplication through factory patterns and shared component extraction

**Planning assumptions**
- All domain services, entities, repositories, and shared capabilities already exist and are agent-agnostic
- The `Request.agent` column already exists (`V6__add_request_agent_column.sql`)
- The agent registry already has a commented placeholder for Testing Agent
- `ReleaseFlowService` already supports `agent` as a filter parameter
- No database migrations are needed
- No new domain logic is needed

---

## Source Design

**System name:** Testing Agent (second workspace under WWA Agent Workspace Hub)

**Design scope summary**
- 3 thin backend controllers delegating to existing services
- Agent identity constants (backend + frontend)
- Frontend API client, store factory, and store instance
- Shared component extraction from existing Deployment Agent views
- Testing Agent thin view wrappers
- Agent registry and router updates
- Backend controller tests and data isolation integration tests

---

## Workstreams

### Major Implementation Streams

1. Backend controller layer
2. Frontend API and store infrastructure
3. Shared component extraction (refactoring existing code)
4. Testing Agent views and routing
5. Testing and verification

### Recommended Sequencing

1. Create agent identity constants (backend + frontend)
2. Create backend controllers and write tests
3. Create frontend API client and store infrastructure
4. Extract shared components from existing Deployment Agent views
5. Create Testing Agent thin view wrappers
6. Update agent registry and router
7. Full verification (backend tests + frontend build + manual smoke test)

### Parallel Work Opportunities

- Backend tasks (TA-TASK-001 through TA-TASK-005) can run in parallel with frontend infrastructure tasks (TA-TASK-006 through TA-TASK-009)
- Shared component extraction (TA-TASK-010, TA-TASK-011) can begin once the extraction strategy is agreed, independent of backend work
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

### TA-TASK-006: Create Frontend API Client and Factory

- **Objective**: Set up the API infrastructure for Testing Agent frontend.
- **Scope**:
  - Create `testingAgentClient.ts` — axios instance with `baseURL: '/api/testing-agent'` and same interceptors as existing client.
  - Create `agentApiFactory.ts` — extract shared API function factories (`createReleaseFlowApi`, `createUploadApi`, `createTaskApi`) that accept an axios instance and return typed API functions.
  - Refactor existing deployment agent API modules to use the factory (to prove the factory works).
  - Create Testing Agent API modules using the factory with `testingAgentClient`.
- **Dependencies**: TA-TASK-001
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: S (~80 lines new, ~30 lines refactored)
- **Notes**: The factory pattern eliminates API code duplication. Existing deployment agent API modules must continue to work identically after refactoring.

### TA-TASK-007: Create Frontend Store Factory and Testing Agent Store

- **Objective**: Set up the Pinia store infrastructure for Testing Agent.
- **Scope**:
  - Create `agentReleaseFlowFactory.ts` — extract shared store factory from existing `releaseFlow.ts` that accepts a store ID and API functions, returns a Pinia store definition.
  - Create `testingAgentReleaseFlow.ts` — instantiate the factory with `'testingAgentReleaseFlow'` ID and Testing Agent API functions.
  - Refactor existing `releaseFlow.ts` to use the factory (to prove it works).
- **Dependencies**: TA-TASK-006
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: M (~100 lines new, ~50 lines refactored)
- **Notes**: Each store instance must maintain independent state. Verify that navigating between agents does not corrupt either store.

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

### TA-TASK-010: Extract AgentSummaryView Shared Component

- **Objective**: Extract common summary view logic from `ReleaseFlowSummaryView.vue` into a reusable component.
- **Scope**:
  - Create `AgentSummaryView.vue` in `frontend/src/components/`
  - Accept props: `agentId`, `agentName`, `agentDescription`, `store`, `detailRouteName`
  - Move all layout, filter, table, and upload dialog logic into the shared component
  - Refactor `ReleaseFlowSummaryView.vue` to a thin wrapper (~20 lines) that passes Deployment Agent props
  - Verify Deployment Agent summary behavior is unchanged after refactoring
- **Dependencies**: TA-TASK-007
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: L (~400 lines extracted, ~20 lines wrapper)
- **Notes**: This is the highest-risk frontend task — it refactors an existing working view. Must be done carefully with before/after comparison. The `UploadDialog` must receive `agentId` to pass the correct agent value on upload.

### TA-TASK-011: Extract AgentDetailView Shared Component

- **Objective**: Extract common detail view logic from `ReleaseFlowDetailView.vue` into a reusable component.
- **Scope**:
  - Create `AgentDetailView.vue` in `frontend/src/components/`
  - Accept props: `agentId`, `agentName`, `store`, `summaryRouteName`
  - Move all layout, stage tabs, rundown panel, task table, and dialog logic into the shared component
  - Refactor `ReleaseFlowDetailView.vue` to a thin wrapper (~20 lines)
  - Verify Deployment Agent detail behavior is unchanged after refactoring
- **Dependencies**: TA-TASK-007
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: L (~400 lines extracted, ~20 lines wrapper)
- **Notes**: Same risk profile as TA-TASK-010. Dialog components (`RecordResultDialog`, `TaskEditDialog`, `DecisionDialog`) must receive API functions through props or provide/inject.

### TA-TASK-012: Create TestingAgentSummaryView

- **Objective**: Create the Testing Agent summary page as a thin wrapper around `AgentSummaryView`.
- **Scope**:
  - Create `TestingAgentSummaryView.vue` in `frontend/src/views/`
  - Pass Testing Agent props: `agentId = AGENT_ID.TESTING`, `agentName = 'Testing Agent'`, `store = useTestingAgentReleaseFlowStore()`, `detailRouteName = 'wwa-testing-agent-detail'`
- **Dependencies**: TA-TASK-007, TA-TASK-010
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: XS (~20 lines)

### TA-TASK-013: Create TestingAgentDetailView

- **Objective**: Create the Testing Agent detail page as a thin wrapper around `AgentDetailView`.
- **Scope**:
  - Create `TestingAgentDetailView.vue` in `frontend/src/views/`
  - Pass Testing Agent props: `agentId = AGENT_ID.TESTING`, `agentName = 'Testing Agent'`, `store = useTestingAgentReleaseFlowStore()`, `summaryRouteName = 'wwa-testing-agent'`
- **Dependencies**: TA-TASK-007, TA-TASK-011
- **Owner type**: frontend
- **Priority**: Must
- **Estimated size**: XS (~20 lines)

### TA-TASK-014: Frontend Build Verification

- **Objective**: Verify the complete frontend builds and typechecks without errors.
- **Scope**:
  - Run `cd frontend && npm run build` — must succeed
  - Run `cd frontend && npx vue-tsc --noEmit` — must succeed
  - Verify no TypeScript errors in new or refactored files
- **Dependencies**: TA-TASK-008, TA-TASK-009, TA-TASK-012, TA-TASK-013
- **Owner type**: frontend / QA
- **Priority**: Must
- **Estimated size**: XS

### TA-TASK-015: End-to-End Verification

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
TA-TASK-001 → TA-TASK-006 → TA-TASK-007 → TA-TASK-010/011 → TA-TASK-012/013 → TA-TASK-009 → TA-TASK-014 → TA-TASK-015
```

### Prerequisite Clusters

| Cluster | Tasks | Description |
|---|---|---|
| **Backend controllers** | TA-TASK-001, 002, 003, 004, 005 | Agent constants + 3 controllers + tests |
| **Frontend infrastructure** | TA-TASK-001, 006, 007 | API factory + store factory |
| **Shared component extraction** | TA-TASK-010, 011 | Refactor existing views |
| **Testing Agent views** | TA-TASK-008, 009, 012, 013 | Registry + routes + thin wrappers |
| **Verification** | TA-TASK-014, 015 | Build check + E2E |

### Parallel Workstreams

- **Backend** (TA-TASK-001 → 002/003/004 → 005) can run in parallel with **Frontend infrastructure** (TA-TASK-006 → 007)
- **Agent registry** (TA-TASK-008) can be done anytime
- **Shared component extraction** (TA-TASK-010/011) can begin once the store factory is ready, independent of backend
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
| TA-TASK-006 | Frontend API client + factory | S | Must | frontend |
| TA-TASK-007 | Frontend store factory + instance | M | Must | frontend |
| TA-TASK-008 | Update agent registry | XS | Must | frontend |
| TA-TASK-009 | Add Testing Agent routes | XS | Must | frontend |
| TA-TASK-010 | Extract AgentSummaryView | L | Must | frontend |
| TA-TASK-011 | Extract AgentDetailView | L | Must | frontend |
| TA-TASK-012 | TestingAgentSummaryView wrapper | XS | Must | frontend |
| TA-TASK-013 | TestingAgentDetailView wrapper | XS | Must | frontend |
| TA-TASK-014 | Frontend build verification | XS | Must | QA |
| TA-TASK-015 | End-to-end verification | M | Must | QA |

**Total: 15 tasks** — 6 XS, 3 S, 3 M, 2 L, 1 M (manual)

---

## Risks / Blockers

| Risk | Severity | Mitigation |
|---|---|---|
| Shared component extraction breaks existing Deployment Agent views | HIGH | Careful before/after testing; keep refactoring as a separate commit |
| `ImportService` may not accept `agent` as a parameter today | LOW | Check existing API; add parameter if missing (minor change) |
| Store factory may not support all current store features | MEDIUM | Extract incrementally; verify each getter/action still works |
| Dialog components may have hardcoded API references | MEDIUM | Audit dialog imports and convert to props/inject pattern |
| Concurrent navigation between agents may cause state issues | LOW | Each store has a unique Pinia ID; test explicitly |

---

## Open Questions

1. Should the shared component extraction (TA-TASK-010/011) happen before or after the Testing Agent thin wrappers? (Recommendation: before, so wrappers can reference the shared component immediately)
2. Should the existing Deployment Agent API modules be refactored to use the factory in this phase, or deferred? (Recommendation: do it now to validate the factory and reduce future tech debt)
3. Should an agent filter dropdown be added to the Audit Log page as part of this delivery? (Recommendation: optional enhancement, not blocking)
