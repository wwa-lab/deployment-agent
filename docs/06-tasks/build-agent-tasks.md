# Implementation Task Breakdown: Build Agent

## Overview

This document breaks the Build Agent design (v2) into implementation-ready tasks. Build Agent is the third agent workspace under WWA Agent Workspace Hub, scoped to the `DEV` stage of the SDLC.

**Delivery approach: Duplicate First, Extract Later (for frontend); Surgical Additive Changes (for shared contracts)**

Frontend modules are duplicated from Testing Agent rather than refactored into shared components — the shared extraction is deferred to a follow-up PR. Shared-contract modifications (Stage enum, FamilyKey, ListItemDto, AuditLoggerService) are surgical additive changes carried out up-front so Build Agent controllers can compile against them.

**Delivery objective**
- Add Build Agent as the third workspace under WWA Agent Workspace Hub, scoped to the `DEV` stage
- Achieve data isolation via `Request.agent = "build-agent"` + a new `AgentBoundaryGuard` on task and flow operations
- Introduce `DEV` as a first-class terminal stage (`Stage.DEV.next() == null`)
- Fix the pre-existing `AuditLoggerService.agentName` hardcoding as a shared side effect (forward-only)
- Zero behavioral regression in Deployment Agent and Testing Agent

**Planning assumptions**
- The `Request.agent` column already exists
- `ReleaseFlowService.listStitchedSummaries` pre-filters by agent; Build Agent stitching is within-agent only (R-14)
- `ImportService.importFile(byte[], Stage, UserContext, ..., String agent)` already accepts both stage and agent as parameters
- Deployment Agent summary visibility is unchanged by this MVP (AD-12); build-only flows appear in it as rows with empty SIT/UAT/PROD columns
- No database migrations are needed — the `stage` column stores enum names as VARCHAR and accepts the new `DEV` value
- No new domain services or entities are needed

---

## Source Design

**System name:** Build Agent (third workspace under WWA Agent Workspace Hub)

**Design scope summary**
- 5 surgical shared-contract / service changes (`Stage`, `ReleaseFlowFamilyKey`, `ReleaseFlowListItemDto`, `AgentId`, `AuditLoggerService`)
- 1 new controller-layer component (`AgentBoundaryGuard`)
- 4 thin backend controllers (release-flow, upload, task, decision) — all guard-protected
- Frontend API client, API modules, Pinia store (duplicated from Testing Agent)
- 2 frontend views (summary + detail, single `DEV` stage)
- Agent registry and router updates
- Backend regression, guard, controller, isolation, progression, and audit tests

---

## Workstreams

### Major Implementation Streams

1. **Shared contracts & audit fix** — enum / regex / DTO / constant / audit one-liner
2. **AgentBoundaryGuard** — new security component
3. **Build Agent backend controllers** — 4 controllers + integration tests
4. **Frontend infrastructure** — client, API modules, store (duplicated from TA)
5. **Build Agent views & routing** — 2 views, registry, router
6. **Testing & verification** — full regression + new test suites + manual smoke

### Recommended Sequencing

1. Shared-contract and audit changes first — keep all existing tests green
2. `AgentBoundaryGuard` component + unit tests
3. Build Agent backend controllers (4) + integration tests + data isolation test
4. Frontend client, API modules, store
5. Build Agent views + registry + routes
6. Full backend test suite + frontend build + manual smoke

### Parallel Work Opportunities

- Shared-contract tasks (BA-TASK-001 through BA-TASK-005) can run in parallel once `Stage` enum lands
- Backend work (BA-TASK-007 through BA-TASK-011b) can run in parallel with frontend infrastructure (BA-TASK-012 through BA-TASK-014)
- Agent registry and routes (BA-TASK-017) can be done anytime
- Test fixture prep can start alongside implementation

---

## Task Details

### BA-TASK-001: Extend `Stage` Enum with `DEV`

- **Objective:** Add `DEV` as the first enum value and rewrite `Stage.next()` as an explicit switch so `DEV` is terminal (`DEV.next() == null`).
- **Scope:**
  - Modify `src/main/java/com/wwa/deploymentagent/contracts/enums/Stage.java`
  - Declare `DEV, SIT, UAT, PROD` in that order
  - Replace the ordinal-based `next()` with a switch: `DEV → null`, `SIT → UAT`, `UAT → PROD`, `PROD → null`
  - Add `StageTest` assertions for all four values of `next()` and `Stage.valueOf("DEV")`
- **Dependencies:** None
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** XS (~15 lines changed + ~20 test lines)
- **Notes:** Run full `mvn test` immediately after this change. No external code uses `Stage.ordinal()` math (grep confirmed). `Stage.values()` iterations in `ReleaseFlowService` are filter-based and tolerate the additional enum value.
  - **Regression surface to explicitly verify** (because `DEV` becomes ordinal 0 and `Stage` natural order changes from `[SIT, UAT, PROD]` to `[DEV, SIT, UAT, PROD]`):
    - `ReleaseFlowService.highestPresentStage` (line 727) — uses `Comparator.naturalOrder()` to pick the max stage; behavior for pure Deployment Agent data is unchanged, but the semantics around mixed data must be re-read
    - `ReleaseFlowService.sortRequests` (line 654) — uses `Comparator.comparing(Request::getStage)` ascending; DEV will sort before SIT
    - `ReleaseFlowService.representativeFlow` (line 710) — picks a representative via `highestPresentStage`
    - `ReleaseFlowService.determineCurrentStage` (line 617) — stage progression detection
    - `ReleaseFlowService.aggregateFlowStatus` (line 600) — iterates `Stage.values()` via flatMap
  - BA-TASK-019 (full regression) must assert Deployment Agent's representative-flow selection, stage sorting, and aggregate flow status remain identical to pre-change behavior.

### BA-TASK-002: Extend `ReleaseFlowFamilyKey` Conservatively for `DEV`

- **Objective:** Recognize `dev` as a stage token only in narrow, unambiguous cases. Preserve legitimate project identifiers like `dev-tools`.
- **Scope:**
  - Modify `src/main/java/com/wwa/deploymentagent/domain/releaseflow/ReleaseFlowFamilyKey.java`
  - Extend `STAGE_PREFIX_WITH_DIGITS` to include `dev`
  - Add a new `DEV_PREFIX_WITH_DIGIT_SEPARATOR` pattern: `^(dev)([^a-z0-9]+)(\\d.+)$`
  - Extend `isStageToken()` to recognize `"dev"` (used inside `stripInfixStageToken`)
  - Update `stripStageToken` to try the new DEV-specific pattern alongside existing patterns
  - Do **NOT** add `dev` to the existing aggressive `STAGE_PREFIX_WITH_SEPARATOR` regex
  - Add test cases for `DEV-1234`, `dev1234`, `DEV_HCC_AMH_1234` (infix), `dev-tools` (preserved), `dev-portal` (preserved), asymmetric DEV-vs-SIT behavior
- **Dependencies:** None — `ReleaseFlowFamilyKey` is pure string processing; tests use string inputs, not `Stage` enum constants. Can run fully in parallel with BA-TASK-001.
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** S (~30 lines changed + ~40 test lines)
- **Notes:** Tracked to design §1.2 binding rules. `dev` is a common project-name prefix; asymmetric treatment is intentional per spec BFR-22a.

### BA-TASK-003: Append `devStatus` / `devPresent` Fields to `ReleaseFlowListItemDto`

- **Objective:** Extend the DTO record with two additive fields and update both existing positional constructor call sites.
- **Scope:**
  - Modify `src/main/java/com/wwa/deploymentagent/contracts/dto/ReleaseFlowListItemDto.java`
  - Append `RequestStatus devStatus` after `prodStatus` and `boolean devPresent` after `prodPresent` in the record header
  - Update `ReleaseFlowListItemDto.from(...)` factory at `ReleaseFlowListItemDto.java:52` to populate the new fields via `requestStatusFor(requests, Stage.DEV, attemptView)` and `hasStage(requests, Stage.DEV)`
  - Update `ReleaseFlowService.buildStitchedSummary(...)` at `ReleaseFlowService.java:675` to append the same two new arguments to its positional constructor call
  - Add `ReleaseFlowListItemDtoTest` cases for DEV-only, DEV+SIT, and legacy (no DEV) inputs
- **Dependencies:** BA-TASK-001
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** S (~30 lines changed across 2 source files + ~40 test lines)
- **Notes:** Append-only per design decision. Prepending would shift all existing positional arguments and introduce a higher risk of silent miswiring. Deployment Agent and Testing Agent summary renderers must NOT read the new fields (verified by existing frontend regression).

### BA-TASK-004: Add `AgentId.BUILD_AGENT` Constant

- **Objective:** Add the new agent identity constant alongside the existing ones.
- **Scope:**
  - Modify `src/main/java/com/wwa/deploymentagent/contracts/AgentId.java`
  - Add `public static final String BUILD_AGENT = "build-agent";`
  - Frontend mirror: update `frontend/src/config/agentId.ts` (create if not present) to add `BUILD: 'build-agent'`
- **Dependencies:** None
- **Owner type:** backend + frontend
- **Priority:** Must
- **Estimated size:** XS (~5 lines total)

### BA-TASK-005: Fix `AuditLoggerService.agentName` Hardcoding (Shared Fix)

- **Objective:** Replace the hardcoded `entry.setAgentName("deployment-agent")` with a dynamic derivation from `scope.agent()`, with legacy fallback.
- **Scope:**
  - Modify `src/main/java/com/wwa/deploymentagent/domain/audit/AuditLoggerService.java`
  - Line 61 (`entry.setAgentName("deployment-agent")`) → `entry.setAgentName(scope.agent() != null ? scope.agent() : AgentId.DEPLOYMENT_AGENT);`
  - Update `AuditLoggerServiceTest` with four assertions:
    - Build Agent action → `agentName = "build-agent"`
    - Testing Agent action → `agentName = "testing-agent"` (forward-only correction)
    - Deployment Agent action → `agentName = "deployment-agent"`
    - Legacy null-agent request → `agentName = "deployment-agent"` (fallback)
  - Verify existing Deployment Agent audit tests still pass unchanged
- **Dependencies:** None — the `AgentId.DEPLOYMENT_AGENT` constant used in the fallback already exists today; this task does not need BA-TASK-004's new `BUILD_AGENT` constant to land first (though the full multi-agent test coverage in `AuditLoggerServiceTest` is easier to write once `BUILD_AGENT` exists).
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** XS (1-line change + ~25 test lines)
- **Notes:** This is a shared fix that simultaneously repairs the pre-existing Testing Agent defect (R-12). Historical rows are not backfilled. Document the forward-only behavior in release notes.

### BA-TASK-006: Create `AgentBoundaryGuard` Component + Unit Tests

- **Objective:** Implement the controller-layer guard that validates agent ownership for task/request/flow operations.
- **Scope:**
  - Create `src/main/java/com/wwa/deploymentagent/web/security/AgentBoundaryGuard.java`
  - Implement three methods:
    - `assertTaskBelongsToAgent(String taskId, String expectedAgent)`
    - `assertRequestBelongsToAgent(String requestId, String expectedAgent)`
    - `assertFlowBelongsToAgent(String flowId, String expectedAgent)` — uses `requestRepository.findByReleaseFlowIds(List.of(flowId), true)` for the agent membership check
  - On mismatch throw `NotFoundAppException` → HTTP 404
  - Annotate methods `@Transactional(readOnly = true)`
  - Create `AgentBoundaryGuardTest` with the full scenario matrix from design Module 2
- **Dependencies:** BA-TASK-004
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** S (~60 lines component + ~120 test lines)
- **Notes:** Uses the existing `requestRepository.findByReleaseFlowIds` to avoid adding a new repository method. `includeArchived = true` so archived requests still count toward agent ownership.

### BA-TASK-007: Create `BuildAgentReleaseFlowController`

- **Objective:** Expose `/api/build-agent/release-flows` endpoints that delegate to shared services with agent forcing and flow-level boundary guard.
- **Scope:**
  - Create `src/main/java/com/wwa/deploymentagent/web/controller/BuildAgentReleaseFlowController.java`
  - `GET /` — force `effectiveAgent = AgentId.BUILD_AGENT`; delegate to `releaseFlowService.listStitchedSummaries(...)` mirroring the Testing Agent controller pattern
  - `GET /{id}` — call `agentBoundaryGuard.assertFlowBelongsToAgent(id, BUILD_AGENT)` before loading; then delegate to `releaseFlowService.getById(id, includeArchived)` + `findRequestsForFlow(id, includeArchived)` and assemble the standard `ReleaseFlowDetailDto`
  - **Do NOT declare** a `@RequestParam linked` — AD-10 disables stitched linked detail; any `?linked=` query parameter is silently ignored
  - Reuse existing imperative validation helpers: `validateArchivedViewer`, `filterVisibleRequests`, `ForbiddenAppException`-on-empty
- **Dependencies:** BA-TASK-001, BA-TASK-003, BA-TASK-004, BA-TASK-006
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** S (~80 lines)

### BA-TASK-008: Create `BuildAgentUploadController`

- **Objective:** Expose `/api/build-agent/upload` endpoints with server-side forced `stage = DEV` and `agent = BUILD_AGENT`.
- **Scope:**
  - Create `src/main/java/com/wwa/deploymentagent/web/controller/BuildAgentUploadController.java`
  - `POST /` — delegate to `importService.importFile(fileBytes, Stage.DEV, user, ..., AgentId.BUILD_AGENT)` using the existing overload; discard any client-supplied stage/agent values
  - `GET /template` — invoke the shared `uploadTemplateService.generateTemplate()` generator (same template content as Testing Agent) and return it with `Content-Disposition: attachment; filename="build-request-template.xlsx"`, matching Testing Agent's per-agent naming pattern (which uses `testing-request-template.xlsx`)
  - Apply the same validation and role helpers as the Testing Agent upload controller
- **Dependencies:** BA-TASK-001, BA-TASK-004
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** S (~60 lines)
- **Notes:**
  - No new `ImportService` overload needed — the existing signature already accepts both stage and agent.
  - **Prerequisite verification (done):** `UploadTemplateService.generateTemplate()` at `src/main/java/com/wwa/deploymentagent/domain/fileimport/UploadTemplateService.java:28` exists and returns `byte[]`. Build Agent controller can inject it directly; no new resource-access strategy is needed.

### BA-TASK-009: Create `BuildAgentTaskController`

- **Objective:** Expose `/api/build-agent/tasks` endpoints for task read and mutation operations, each wrapped by the boundary guard.
- **Scope:**
  - Create `src/main/java/com/wwa/deploymentagent/web/controller/BuildAgentTaskController.java`
  - Endpoints (all guard-protected):
    - `GET /?requestId=X` → `assertRequestBelongsToAgent(requestId, BUILD_AGENT)` then `taskService.listByRequestId(requestId)`
    - `GET /{id}` → `assertTaskBelongsToAgent` then `taskService.getById(id)`
    - `PUT /{id}/input` → `assertTaskBelongsToAgent` then `taskService.editInput(id, newInput, user)`
    - `GET /{id}/executions` → `assertTaskBelongsToAgent` then `taskExecutionHistoryService.findByTaskId(id)`
    - `POST /{id}/start-manual` → `assertTaskBelongsToAgent` then `taskService.startManualExecution(id, user)`
    - `POST /{id}/record-result` → `assertTaskBelongsToAgent` then `recordResultService.recordResult(...)`
    - `POST /{id}/submit-auto` → `assertTaskBelongsToAgent` then `autoExecutionService.submitAutoExecution(id, user)`
- **Dependencies:** BA-TASK-004, BA-TASK-006
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** M (~150 lines)
- **Notes:** Body validation, DTO assembly, and error responses mirror the existing `TaskController` exactly.

### BA-TASK-010: Create `BuildAgentDecisionController`

- **Objective:** Expose `POST /api/build-agent/tasks/{id}/decision` with the boundary guard, mirroring the existing Deployment Agent `DecisionController`.
- **Scope:**
  - Create `src/main/java/com/wwa/deploymentagent/web/controller/BuildAgentDecisionController.java`
  - `POST /{id}/decision` — call `assertTaskBelongsToAgent(id, BUILD_AGENT)` first; then `decisionEngine.applyDecision(id, decision, user, comment)`; then `progressionService.progressAfterDecision(id)`; return updated `TaskDto`
- **Dependencies:** BA-TASK-004, BA-TASK-006
- **Owner type:** backend
- **Priority:** Must
- **Estimated size:** XS (~50 lines)

### BA-TASK-011a: Build Agent Controller Unit Integration Tests

- **Objective:** Verify each Build Agent controller delegates correctly and enforces the agent boundary. One test file per controller.
- **Scope:**
  - `BuildAgentReleaseFlowControllerTest` — list forces agent filter; detail returns 404 for non-build-agent flows; detail ignores `?linked=`; happy path DTO shape
  - `BuildAgentUploadControllerTest` — upload forces `agent=build-agent`, `stage=DEV`; template download returns shared content with `build-request-template.xlsx` Content-Disposition; invalid Excel → 422
  - `BuildAgentTaskControllerTest` — each task endpoint returns 404 for cross-agent tasks; happy path delegates correctly; legacy null-agent tasks → 404
  - `BuildAgentDecisionControllerTest` — 404 for cross-agent task; happy path applies decision then calls progression
- **Dependencies:** BA-TASK-007, BA-TASK-008, BA-TASK-009, BA-TASK-010
- **Owner type:** QA / backend
- **Priority:** Must
- **Estimated size:** M (~300 lines across 4 test files)
- **Notes:** Follow existing `@SpringBootTest` + H2 patterns in `src/test/java/com/wwa/deploymentagent/web/`. Target 80%+ coverage on new controller code.

### BA-TASK-011b: Build Agent Cross-Cutting Integration Tests

- **Objective:** Verify end-to-end behavior that spans multiple controllers and services: cross-agent isolation, within-agent stitching, and DEV-stage progression.
- **Scope:**
  - `BuildAgentDataIsolationTest` — end-to-end cross-agent behavior:
    1. Upload via Build Agent → only Build Agent summary shows it
    2. Upload via Build Agent → Testing Agent summary does NOT show it
    3. Upload via Build Agent → Deployment Agent summary DOES show it (global view) with empty SIT/UAT/PROD columns
    4. Two `DEV-1234` uploads via Build Agent → single stitched row within Build Agent
    5. Build Agent `DEV-1234` + Deployment Agent `SIT-1234` → separate rows in both agents (R-14 assertion)
    6. Legacy null-agent request → not shown in Build Agent
  - `BuildAgentProgressionTest` — approve all tasks in a DEV flow → flow `Completed` without advancing to SIT; verify `ReleaseFlowProgressionService.progressAfterDecision` takes the `next() == null` branch
- **Dependencies:** BA-TASK-011a
- **Owner type:** QA / backend
- **Priority:** Must
- **Estimated size:** M (~200 lines across 2 test files)
- **Notes:** These tests are larger-scope scenarios that set up multi-agent state and assert cross-controller outcomes. Splitting them from BA-TASK-011a keeps PR review manageable.

### BA-TASK-012: Create Frontend API Client + Duplicated API Modules

- **Objective:** Set up the API infrastructure for Build Agent frontend.
- **Scope:**
  - Create `frontend/src/api/buildAgentClient.ts` — axios instance with `baseURL: '/api/build-agent'` and the same interceptors as Testing Agent
  - Duplicate `testingAgentReleaseFlows.ts` → `buildAgentReleaseFlows.ts`; swap client import; **remove** any `linked` parameter from `getById`
  - Duplicate `testingAgentUpload.ts` → `buildAgentUpload.ts`; swap client import; upload function does not send `stage`
  - Duplicate `testingAgentTasks.ts` → `buildAgentTasks.ts`; swap client import
- **Dependencies:** BA-TASK-004
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** S (~250 lines, mostly duplicated)

### BA-TASK-013: Create Duplicated Build Agent Store

- **Objective:** Set up a dedicated Pinia store for Build Agent release flow state.
- **Scope:**
  - Duplicate `testingAgentReleaseFlow.ts` → `buildAgentReleaseFlow.ts`
  - Change store ID: `'buildAgentReleaseFlow'`
  - Change API imports to `buildAgentReleaseFlows`, `buildAgentUpload`, `buildAgentTasks`
  - **Remove** any code path that handles a `linked` query parameter
  - Export `useBuildAgentReleaseFlowStore`
- **Dependencies:** BA-TASK-012
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** S (~130 lines, duplicated)

### BA-TASK-014: Create `BuildAgentSummaryView.vue`

- **Objective:** Create the Build Agent summary page by duplicating `TestingAgentSummaryView.vue` and adapting it for the DEV-only stage scope.
- **Scope:**
  - Duplicate `frontend/src/views/TestingAgentSummaryView.vue` → `BuildAgentSummaryView.vue`
  - Replace `useTestingAgentReleaseFlowStore` → `useBuildAgentReleaseFlowStore`
  - Replace API imports → `buildAgentReleaseFlows`, `buildAgentUpload`
  - Page title: `"Build Agent"`
  - Page description and WWA Today text: reference the DEV phase only
  - `const stages = ['DEV']`
  - `UploadDialog` prop: `:allowed-stages="['DEV']"`
  - Stage filter: disabled input showing `DEV`
  - Summary row renderer: read `row.devStatus` / `row.devPresent` for the single DEV column (do NOT render SIT/UAT/PROD columns)
  - Detail route path: `/wwa/build-agent/release-flows/`
- **Dependencies:** BA-TASK-013
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** M (~500 lines, duplicated with modifications)
- **Notes:** Existing Deployment Agent and Testing Agent views are NOT modified.

### BA-TASK-015: Create `BuildAgentDetailView.vue`

- **Objective:** Create the Build Agent detail page by duplicating `TestingAgentDetailView.vue` and restricting it to the DEV stage.
- **Scope:**
  - Duplicate `frontend/src/views/TestingAgentDetailView.vue` → `BuildAgentDetailView.vue`
  - Replace store and API imports
  - Page title / breadcrumb: `"Build Agent"`
  - Summary route path: `/wwa/build-agent`
  - Stage tabs: only render the DEV tab
  - **Remove** the `linkedFlowQuery` computed and any `route.query.linked` usage per AD-10
  - Fetch calls do not pass `linked` to the store or API
- **Dependencies:** BA-TASK-013
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** L (~600 lines, duplicated with modifications)

### BA-TASK-016: Extend `AgentCategory` and Register Build Agent

- **Objective:** Register Build Agent so it appears on the WWA Home page and sidebar flyout.
- **Scope:**
  - Modify `frontend/src/config/agentRegistry.ts`
  - Extend `AgentCategory` type: add `'build'` to the union
  - Add Build Agent entry: `{ key: 'build-agent', name: 'Build Agent', description: '...DEV phase...', route: '/wwa/build-agent', icon: '🔨', enabled: true, category: 'build' }`
- **Dependencies:** None
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** XS (~10 lines)

### BA-TASK-017: Add Build Agent Routes

- **Objective:** Register Build Agent routes in Vue Router.
- **Scope:**
  - Modify `frontend/src/router/index.ts`
  - Add `path: 'build-agent'` → `BuildAgentSummaryView` (lazy import) with meta `{ section: 'build-agent', sectionTitle: 'Build Agent' }`
  - Add `path: 'build-agent/release-flows/:id'` → `BuildAgentDetailView` (lazy import) with same meta
- **Dependencies:** BA-TASK-014, BA-TASK-015
- **Owner type:** frontend
- **Priority:** Must
- **Estimated size:** XS (~10 lines)

### BA-TASK-018a: Frontend Component Tests

- **Objective:** Add runtime component-level tests covering Build Agent views and regression-protecting Deployment/Testing Agent renderers from the additive DTO fields.
- **Scope:**
  - `BuildAgentSummaryView` snapshot / unit test: assert the stage filter is a disabled input showing `DEV`; assert the summary row renders only the DEV column (reads `row.devStatus` / `row.devPresent`) and does not emit SIT/UAT/PROD cells
  - `BuildAgentDetailView` test: assert the stage tab set contains only `DEV`; assert the view does not read `route.query.linked` (no network call carries `linked`)
  - **Regression** `ReleaseFlowSummaryView` (Deployment Agent): assert the summary row still renders only `sitStatus` / `uatStatus` / `prodStatus` columns and does NOT render `devStatus` / `devPresent`, even when the DTO contains those fields
  - **Regression** `TestingAgentSummaryView`: same assertion as above — only SIT/UAT/PROD columns, no DEV
  - Use the project's existing frontend test framework if present; otherwise add a minimal Vitest setup alongside the new tests
- **Dependencies:** BA-TASK-014, BA-TASK-015
- **Owner type:** frontend / QA
- **Priority:** Must
- **Estimated size:** S (~150 lines across 4 test files)
- **Notes:** This task specifically guards against the R-09 risk (additive DTO fields accidentally rendering in other agents' views). BA-TASK-018 is only a build gate and will not catch runtime renderer behavior.

### BA-TASK-018: Frontend Build and Type Check Verification

- **Objective:** Verify the complete frontend builds and typechecks without errors.
- **Scope:**
  - Run `cd frontend && npm run build` — must succeed
  - Run `cd frontend && npx vue-tsc --noEmit` — must succeed
  - No TypeScript errors in new or existing files
  - Verify Deployment Agent and Testing Agent views still render only their own stage columns
- **Dependencies:** BA-TASK-014, BA-TASK-015, BA-TASK-016, BA-TASK-017
- **Owner type:** frontend / QA
- **Priority:** Must
- **Estimated size:** XS

### BA-TASK-019: Full Backend Test Regression

- **Objective:** Verify the full backend test suite passes after all shared-contract, guard, and controller changes.
- **Scope:**
  - Run `mvn test` from the repository root
  - All existing Deployment Agent and Testing Agent tests must pass unchanged
  - All new tests from BA-TASK-001, 002, 003, 005, 006, 011a, 011b must pass
  - **Explicitly verify enum-order regression surface** (per BA-TASK-001 Notes): Deployment Agent flow-level tests that exercise `ReleaseFlowService.representativeFlow`, `highestPresentStage`, `sortRequests`, `determineCurrentStage`, and `aggregateFlowStatus` must produce identical results to pre-change behavior for pure SIT/UAT/PROD data
  - Investigate and fix any regression before proceeding
- **Dependencies:** BA-TASK-001 through BA-TASK-011b
- **Owner type:** QA / backend
- **Priority:** Must
- **Estimated size:** S

### BA-TASK-020: End-to-End Manual Verification

- **Objective:** Verify the complete Build Agent workflow works end-to-end and existing agents remain unaffected.
- **Scope:**
  - Start backend (`mvn spring-boot:run -Dspring-boot.run.profiles=local`)
  - Start frontend (`cd frontend && npm run dev`)
  - Verify:
    1. Build Agent card appears on WWA Home page with icon `🔨`
    2. Build Agent appears in sidebar flyout
    3. Navigate to `/wwa/build-agent` — Build Agent summary loads with `DEV` stage filter disabled input
    4. Upload Excel via Build Agent → request created with `agent = "build-agent"`, `stage = "DEV"`
    5. Release flow appears in Build Agent summary with a single DEV column
    6. Release flow DOES NOT appear in Testing Agent summary
    7. Release flow DOES appear in Deployment Agent summary (global view) with empty SIT/UAT/PROD columns
    8. Navigate to Build Agent detail page — only the DEV stage tab is shown; tasks visible
    9. Task actions work (edit input, start manual, record result, submit auto, decision)
    10. Audit log shows entries with `agentName = "build-agent"` (Testing Agent audit forward-only correction is covered by `AuditLoggerServiceTest` in BA-TASK-005, not manual verification)
    11. Approve the final task in the DEV flow → flow transitions to `Completed` without advancing to SIT
    12. Attempt cross-agent URL probing (e.g. `/api/build-agent/tasks/{deployment-agent-task-id}`) → 404
    13. Attempt `?linked=` on Build Agent detail URL → behaves the same as without `linked`
    14. Deployment Agent stage filter still shows only `SIT / UAT / PROD` (no `DEV`)
    15. Testing Agent stage filter still shows only `UAT`
    16. Deployment Agent and Testing Agent summary renderers do NOT show any DEV column
    17. All existing Deployment Agent and Testing Agent workflows still work
- **Dependencies:** All previous tasks
- **Owner type:** QA
- **Priority:** Must
- **Estimated size:** M (manual testing)
- **Notes:** This is the final acceptance gate before Build Agent is considered ready for review.

---

## Dependency Plan

### Critical Path

```
BA-TASK-001 (Stage) ─> BA-TASK-003 (ListItemDto) ─> BA-TASK-007 (ReleaseFlow ctrl)
BA-TASK-002 (FamilyKey)    ← parallel with 001, no hard dep
BA-TASK-005 (Audit fix)    ← parallel with 001/002/004, no hard dep

BA-TASK-004 (AgentId) ─┬─> BA-TASK-006 (Guard)
                      ├─> BA-TASK-008 (Upload ctrl)
                      ├─> BA-TASK-009 (Task ctrl)
                      ├─> BA-TASK-010 (Decision ctrl)
                      └─> BA-TASK-012 (Frontend API)

BA-TASK-006 (Guard) ─> BA-TASK-007/009/010

BA-TASK-007..010 ─> BA-TASK-011a (Controller unit tests) ─> BA-TASK-011b (Isolation + Progression) ─> BA-TASK-019 (mvn test)

BA-TASK-012 (Frontend API) ─> BA-TASK-013 (Store) ─> BA-TASK-014/015 (Views) ─┬─> BA-TASK-017 (Routes) ─> BA-TASK-018 (Frontend build)
                                                                              └─> BA-TASK-018a (Frontend component tests)

BA-TASK-019 + BA-TASK-018 + BA-TASK-018a ─> BA-TASK-020 (E2E manual)
```

### Prerequisite Clusters

| Cluster | Tasks | Description |
|---|---|---|
| **Shared contracts & audit** | 001, 002, 003, 004, 005 | Stage enum / FamilyKey / ListItemDto / AgentId / Audit fix |
| **Agent boundary guard** | 006 | New security component + unit tests |
| **Build Agent controllers** | 007, 008, 009, 010 | Four thin controllers |
| **Backend tests** | 011a, 011b, 019 | Controller unit tests + cross-cutting integration + full regression |
| **Frontend infrastructure** | 012, 013 | API modules + store |
| **Build Agent views** | 014, 015 | Summary + detail |
| **Routing** | 016, 017 | Registry + routes |
| **Verification** | 018, 018a, 020 | Build gate + component tests + E2E |

### Parallel Workstreams

- BA-TASK-001, 002, 004, 005 have **no mutual hard dependencies** and can all proceed in parallel from day 1
- BA-TASK-003 depends on 001 (needs `Stage.DEV` constant in `from()` factory)
- BA-TASK-006 depends on 004 (uses `AgentId` constants in tests)
- Frontend (012 → 013 → 014/015 → 018a) can run fully in parallel with backend controller work (007 through 011b)
- Agent registry (016) is independent and can be done any time
- BA-TASK-018a and BA-TASK-018 are independent of each other; 018a can run once views exist, 018 once routes are wired

---

## Task Summary

| Task | Description | Size | Priority | Owner |
|---|---|---|---|---|
| BA-TASK-001 | Extend `Stage` enum with `DEV` (explicit `next()` switch) | XS | Must | backend |
| BA-TASK-002 | Conservative `ReleaseFlowFamilyKey` DEV recognition | S | Must | backend |
| BA-TASK-003 | Append `devStatus`/`devPresent` to `ReleaseFlowListItemDto` | S | Must | backend |
| BA-TASK-004 | Add `AgentId.BUILD_AGENT` constant (backend + frontend) | XS | Must | backend + frontend |
| BA-TASK-005 | Fix `AuditLoggerService.agentName` hardcoding (shared) | XS | Must | backend |
| BA-TASK-006 | `AgentBoundaryGuard` component + unit tests | S | Must | backend |
| BA-TASK-007 | `BuildAgentReleaseFlowController` | S | Must | backend |
| BA-TASK-008 | `BuildAgentUploadController` | S | Must | backend |
| BA-TASK-009 | `BuildAgentTaskController` | M | Must | backend |
| BA-TASK-010 | `BuildAgentDecisionController` | XS | Must | backend |
| BA-TASK-011a | Build Agent controller unit integration tests (4 files) | M | Must | QA / backend |
| BA-TASK-011b | Build Agent cross-cutting integration tests (isolation + progression) | M | Must | QA / backend |
| BA-TASK-012 | Frontend API client + duplicated API modules | S | Must | frontend |
| BA-TASK-013 | Duplicated Build Agent store | S | Must | frontend |
| BA-TASK-014 | `BuildAgentSummaryView.vue` (duplicated, DEV-only) | M | Must | frontend |
| BA-TASK-015 | `BuildAgentDetailView.vue` (duplicated, no linked) | L | Must | frontend |
| BA-TASK-016 | Extend `AgentCategory` + register Build Agent | XS | Must | frontend |
| BA-TASK-017 | Add Build Agent routes | XS | Must | frontend |
| BA-TASK-018 | Frontend build + type check verification | XS | Must | QA |
| BA-TASK-018a | Frontend component tests (summary / detail / DA+TA regression) | S | Must | frontend / QA |
| BA-TASK-019 | Full `mvn test` regression (incl. enum-order surface) | S | Must | QA / backend |
| BA-TASK-020 | End-to-end manual verification | M | Must | QA |

**Total: 22 tasks** — 7 XS, 9 S, 5 M, 1 L

- XS (7): 001, 004, 005, 010, 016, 017, 018
- S (9): 002, 003, 006, 007, 008, 012, 013, 018a, 019
- M (5): 009, 011a, 011b, 014, 020
- L (1): 015

---

## Risks / Blockers

| Risk | Severity | Status / Mitigation |
|---|---|---|
| `Stage.next()` rewrite breaks tests assuming `{SIT, UAT, PROD}` | MEDIUM | Full `mvn test` gate after BA-TASK-001; grep confirmed no external `Stage.ordinal()` usage |
| `ReleaseFlowFamilyKey` DEV extension accidentally strips legitimate `dev-*` project names | LOW | Conservative regex strategy per BA-TASK-002; explicit test case for `dev-tools` preservation |
| `ReleaseFlowListItemDto` positional constructor miswired | LOW | Append-only; two call sites updated in lockstep; `ReleaseFlowListItemDtoTest` covers |
| `AuditLoggerService` fix retroactively changes Testing Agent audit rows | MEDIUM (R-12) | Accepted as forward-only fix; documented in spec R-12; release notes |
| Deployment Agent summary now visibly shows build-only rows with empty columns | LOW (R-13) | Accepted per AD-12; explicit E2E check in BA-TASK-020 step 7 |
| Cross-agent stitching is not supported (R-14) | LOW (R-14) | Accepted as MVP scope; documented in spec; BA-TASK-011b data isolation test asserts separate rows |
| Build Agent detail does not support `?linked=` (AD-10) | LOW (R-11) | Accepted; BA-TASK-015 removes `linkedFlowQuery`; BA-TASK-020 step 14 verifies silent ignore |
| Testing Agent pre-existing cross-agent task mutation gap is NOT closed by Build Agent MVP | MEDIUM (R-08) | Accepted; tracked as follow-up FU-006 |
| Family key `findByReleaseFlowIds` method must exist in `RequestRepository` | LOW | Verified: used by `ReleaseFlowService.findRequestsByReleaseFlowIds` |

---

## Follow-Up Tasks (Separate PR)

After Build Agent is working and verified, the following should be scheduled in separate PRs:

| ID | Task | Description |
|---|---|---|
| FU-001 | Extract `AgentSummaryView.vue` | Shared component parameterized by `stageColumns: Stage[]`; Deployment/Testing/Build views become thin wrappers |
| FU-002 | Extract `AgentDetailView.vue` | Shared component parameterized by stage tabs and linked-detail support flag |
| FU-003 | Extract `agentApiFactory.ts` | Replace duplicated API modules with a factory taking an axios instance |
| FU-004 | Extract `agentReleaseFlowFactory.ts` | Replace duplicated stores with a factory |
| FU-005 | Refactor dialog components | Accept API functions via props instead of hardcoded imports (prerequisite for FU-001/002) |
| FU-006 | Back-patch Testing Agent with `AgentBoundaryGuard` | Close R-08: apply the same guard to Testing Agent task mutation and flow detail endpoints |
| FU-007 | Cross-agent family view (optional) | Refactor `listStitchedSummaries` so a single stitched row can span multiple agents; addresses R-14 |
| FU-008 | Unify upload template download file names | Change Deployment / Testing / Build Agent Content-Disposition to a single neutral `request-template.xlsx`, per the CLAUDE.md rule; addresses R-15. User-visible change to Testing Agent download name |

---

## Open Questions

1. Should BA-TASK-020 step 11 (verifying Testing Agent audit fix) be elevated to a formal regression test rather than manual verification?
2. When should FU-006 (Testing Agent boundary back-patch) be scheduled? Recommendation: immediately after Build Agent merges, since the guard component already exists
3. Should BA-TASK-015 reject `?linked=` with HTTP 400 instead of silently ignoring it? MVP chooses silent ignore; open for future tightening
