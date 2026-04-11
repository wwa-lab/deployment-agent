# Implementation Task Breakdown: Build Agent

**Date:** 2026-04-11
**Status:** Draft (v3, aligned with `build-agent-design.md` v3 §10 Implementation Sequence)
**Source:** `build-agent-design.md` (primary, module-level contracts), `build-agent-architecture.md` (structural decisions), `build-agent-spec.md` (product intent)
**Supersedes:** v2 (2026-04-11). v2 broke down 20 tasks against the v2 "surgical shared-contract changes" model. v3 replaces them with 28 tasks against the Agent Module refactor, organized into the 10 phases defined in design §10.

---

## 1. Overview

### 1.1 Delivery Model

v3 delivers Build Agent as the first consumer of a **Platform Core refactor** that introduces the Agent Module pattern. Deployment Agent and Testing Agent are migrated into the same pattern in the same delivery so the codebase lands in a consistent end state.

This is structurally different from v2 ("four surgical shared-contract changes") — the total work is larger, but per-agent coupling drops to the minimum the shared release-flow domain model actually requires. See `build-agent-architecture.md` §"Why v3 Exists" for the full rationale.

### 1.2 Delivery objectives

- Deliver Build Agent as an Agent Module (`agents/build/` backend, `frontend/src/agents/build/` frontend) running on top of the new Platform Core pattern.
- Refactor Platform Core so stage vocabulary, stage ordering, and stitching are per-agent concerns (architecture PL-1 through PL-11).
- Migrate Deployment Agent and Testing Agent into the Agent Module pattern without runtime regression.
- Move platform capability routes from `/api/deployment-agent/*` to `/api/platform/*` in a hard cutover.
- Close pre-existing Testing Agent agent-boundary gap (v2 R-08) as a side effect of promoting `AgentBoundaryGuard` to Platform Core.
- Keep `mvn test` and `cd frontend && npm run build` green at every commit.

### 1.3 Planning Assumptions

- `Request.agent` column already exists on `DA_REQUEST`.
- `DA_RELEASE_FLOW` unique key `(project_id, normalized_release_id)` is unchanged; no Flyway migration.
- `spring.jpa.open-in-view=false` is set; each service method opens its own Hibernate session; `AgentBoundaryGuard` incurs real DB round trips (acceptable per architecture R-11).
- `JSESSIONID` cookie `Path` is `/`, so moving auth routes does not invalidate sessions.
- Testing Agent has no public users (internal testing only); migration does not require a behavior-preserving guarantee, only a code-correct one.
- No backfill of legacy `agent IS NULL` data; those rows become invisible until the platform Global View ships (architecture R-04).

---

## 2. Source Design

**System name:** Build Agent (Agent Module) + Platform Core refactor
**Design document:** `build-agent-design.md` v3
**Task mapping:** Each task below references the design module (M1–M11, D1–D5, T1–T3, B1–B6) and the architecture decision (PL-*/BA-*) it implements.

---

## 3. Phase Overview

The 28 tasks are organized into 10 phases. Phase ordering is mandatory; inter-phase dependencies are strict. Tasks within a phase may sometimes run in parallel — see §5 Parallel Work Opportunities.

| Phase | Title | Tasks | Purpose |
|---|---|---|---|
| A | Platform scaffolding | BA-T01, BA-T02 | Empty packages, `StagePipeline` interface — no runtime behavior change |
| B | Stage vocabulary migration | BA-T03, BA-T04, BA-T05, BA-T06, BA-T07 | Per-agent Stage enums; JPA attribute type change; delete shared `Stage` |
| C | DTO and aggregation refactor | BA-T08, BA-T09 | Generic `stageStatuses` map; observed-stage iteration |
| D | Stitching relocation | BA-T10, BA-T11, BA-T12 | `DeploymentStitchingService` + `listByAgent` |
| E | Guard + Audit | BA-T13, BA-T14 | `AgentBoundaryGuard` platform component; `AuditLoggerService` dynamic agentName |
| F | Platform capability routes | BA-T15, BA-T16 | `/api/platform/*` cutover + `SecurityConfig` + frontend platform client |
| G | Frontend factory | BA-T17, BA-T18 | `createAgentWorkspace` factory; `AgentSummaryView` / `AgentDetailView` |
| H | Agent Module migrations (backend) | BA-T19, BA-T20, BA-T21, BA-T22 | Deployment / Testing / Build Agent controllers |
| I | Frontend migration | BA-T23, BA-T24, BA-T25 | Per-agent `index.ts` + delete old flat files |
| J | Verification | BA-T26, BA-T27, BA-T28 | Full regression; manual smoke; release notes |

---

## 4. Task Details

### Phase A — Platform scaffolding

#### BA-T01: Create `platform/domain/StagePipeline.java` interface
- **Phase:** A
- **Design ref:** M1
- **Architecture ref:** PL-4
- **Effort:** S (small)
- **Description:** Create the public `StagePipeline` interface with three methods (`next`, `isTerminal`, `orderedStages`). No implementations yet. No callers yet. The only file created.
- **Files created:** `src/main/java/com/wwa/deploymentagent/platform/domain/StagePipeline.java`
- **Acceptance criteria:**
  - Interface compiles.
  - `mvn test` still green (no tests touched).
  - No existing code references the interface yet.
- **Depends on:** —
- **Blocks:** BA-T04, BA-T05

#### BA-T02: Create empty agent-module package scaffolds
- **Phase:** A
- **Design ref:** M1 precondition
- **Architecture ref:** PL-2
- **Effort:** S
- **Description:** Create empty package directories with `package-info.java` files for `com.wwa.deploymentagent.agents.deployment`, `.testing`, `.build` and their `domain/` / `web/` subpackages. Same for `com.wwa.deploymentagent.platform`. This gives ArchUnit rules targets to bind to before any real code lands.
- **Files created:** 12 `package-info.java` files
- **Acceptance criteria:**
  - `mvn test` still green.
  - ArchUnit rule `agents_do_not_depend_on_each_other` is added and passes trivially.
- **Depends on:** —
- **Blocks:** BA-T04 onwards (indirectly; every later task lands under these packages)

---

### Phase B — Stage vocabulary migration

#### BA-T03: Create per-agent Stage enums
- **Phase:** B
- **Design ref:** D1, T1, B1
- **Architecture ref:** PL-3
- **Effort:** S
- **Description:** Create `DeploymentStage { SIT, UAT, PROD }`, `TestingStage { UAT }`, `BuildStage { DEV }` enums in their respective agent domain packages. Each enum is a simple value enum with no methods beyond optional `fromString`.
- **Files created:** `agents/deployment/domain/DeploymentStage.java`, `agents/testing/domain/TestingStage.java`, `agents/build/domain/BuildStage.java`
- **Acceptance criteria:**
  - Three unit test files, each asserting the enum's declared values and `fromString` round-trip.
  - `mvn test` green.
- **Depends on:** BA-T02
- **Blocks:** BA-T04

#### BA-T04: Create per-agent `StagePipeline` implementations + `StagePipelineRegistry`
- **Phase:** B
- **Design ref:** M1 (interface + registry), D1, T1, B1
- **Architecture ref:** PL-4
- **Effort:** M (medium)
- **Description:** Create `DeploymentStagePipeline`, `TestingStagePipeline`, `BuildStagePipeline` `@Component` beans implementing `StagePipeline`. Each exposes `String agentId()` returning its `AgentId` constant; `next(...)` and `isTerminal(...)` throw `IllegalArgumentException` for stages not in `orderedStages()` (fail-loud — do NOT silently treat unknown stages as terminal). Also create `StagePipelineRegistry` `@Component` in Platform Core: injects `List<StagePipeline>`, builds an immutable `agentId → pipeline` map, throws at startup on duplicate `agentId()` values, throws `IllegalStateException` from `forAgent(...)` on missing agent.
- **Files created:** 3 pipeline files + `StagePipelineRegistry.java`
- **Acceptance criteria:**
  - `StagePipelineContractTest` (parameterized for all 3 impls) passes all 11 rows per design §M1.3, including the **fail-loud rows**: `next("totally-unknown")` throws `IllegalArgumentException`, `isTerminal("totally-unknown")` throws `IllegalArgumentException`.
  - `StagePipelineRegistryTest` passes 4 cases: known-agent lookup returns the right impl, unknown-agent lookup throws `IllegalStateException`, duplicate-`agentId()` registration fails at Spring context startup, non-empty registry invariant.
  - Each concrete pipeline has a focused unit test with its specific ordering assertions (including `agentId()` returning the expected `AgentId` constant).
- **Depends on:** BA-T01, BA-T03
- **Blocks:** BA-T05

#### BA-T05: Rewrite `ReleaseFlowProgressionService` terminal check via `StagePipelineRegistry` (signature unchanged)
- **Phase:** B
- **Design ref:** M4
- **Architecture ref:** PL-4
- **Effort:** M (medium)
- **Description:** Add `StagePipelineRegistry` as a constructor dependency on `ReleaseFlowProgressionService`. Rewrite the terminal-stage branch at `ReleaseFlowProgressionService.java:72` to resolve the pipeline from `request.getAgent()` via `stagePipelineRegistry.forAgent(...)`, then call `pipeline.isTerminal(releaseFlow.getCurrentStage())` instead of `releaseFlow.getCurrentStage().next() == null`. **Method signature `progressAfterDecision(String taskId)` is unchanged.** **No caller changes** — all 5 existing call sites continue working as-is.
- **Critical constraint:** Do NOT thread `StagePipeline` as a method parameter through `progressAfterDecision`. An earlier v3 draft proposed this; it is impossible because the method is called from `RecordResultService.java:98`, `AutoExecutionService.java:159`, and `ExternalExecutionMonitorService.java:207` — the last of which runs on a Jenkins/Ansible callback thread with no HTTP or agent context. Parameter threading would push agent semantics deep into Platform Core services and violate PL-2.
- **Files modified:** `ReleaseFlowProgressionService.java` (constructor adds `StagePipelineRegistry`; body at line 72 rewrites the terminal check)
- **Files that MUST NOT be modified by this commit (grep verification gate):** `DecisionController.java`, `TestingAgentTaskController.java`, `RecordResultService.java`, `AutoExecutionService.java`, `ExternalExecutionMonitorService.java` — none of their call sites for `progressAfterDecision` should change in any way
- **Acceptance criteria:**
  - `ReleaseFlowProgressionServiceTest.terminalStage_marksCompleted_perAgent` passes parameterized over all 3 agents (DeploymentStage PROD, TestingStage UAT, BuildStage DEV all resolve to `Completed`).
  - `ReleaseFlowProgressionServiceTest.nonTerminalStage_advances` passes for Deployment Agent SIT → UAT → PROD via registry-resolved pipeline.
  - `ReleaseFlowProgressionServiceTest.unknownAgent_failsLoud` — Request row with `agent = "ghost-agent"` triggers `IllegalStateException` from registry, transaction rolls back, flow state unchanged.
  - `ReleaseFlowProgressionServiceTest.mismatchedStage_failsLoud` — data-integrity scenario where flow's `currentStage` is not declared in the resolved pipeline → `IllegalArgumentException` from the pipeline, transaction rolls back.
  - `ReleaseFlowProgressionServiceAllCallersTest` — integration test exercising each of the 5 call sites (decision controller, testing controller, record result, auto execution, external monitor callback) through the `progressAfterDecision(taskId)` signature; all paths succeed.
  - `git diff --stat` on this commit shows `ReleaseFlowProgressionService.java` modified but NONE of the 5 caller files modified.
- **Depends on:** BA-T04
- **Blocks:** BA-T06

#### BA-T06: `Request.stage` and `ReleaseFlow.currentStage` JPA attribute type change to `String`
- **Phase:** B
- **Design ref:** M5
- **Architecture ref:** PL-3
- **Effort:** L (large — biggest mechanical commit in the delivery)
- **Description:** Change JPA attribute type from `Stage` enum to `String`. Remove `@Enumerated(EnumType.STRING)`. Update every repository method, service method, DTO call site, and test that previously passed `Stage`. The touched files span Platform Core domain, DTOs, repositories, tests.
- **Files modified:** `ReleaseFlow.java`, `Request.java`, `ReleaseFlowService.java`, `ImportService.java`, `TaskService.java`, `ReleaseFlowAggregation.java`, `ReleaseFlowListItemDto.java`, `ReleaseFlowRepository.java`, `RequestRepository.java`, and ~15 test files
- **Acceptance criteria:**
  - `mvn test` green. All existing repository and service integration tests pass after mechanical `Stage.SIT` → `"SIT"` replacement.
  - `grep` confirms no class outside `contracts/enums/Stage.java` references the `Stage` import. (Stage.java itself still exists at this step; deleted in BA-T07.)
- **Depends on:** BA-T05
- **Blocks:** BA-T07

#### BA-T07: Delete `contracts/enums/Stage.java`
- **Phase:** B
- **Design ref:** M5 follow-up
- **Architecture ref:** PL-3
- **Effort:** S
- **Description:** Delete the shared Stage enum. After BA-T06 no code should import it; this commit only removes the file.
- **Files deleted:** `src/main/java/com/wwa/deploymentagent/contracts/enums/Stage.java`
- **Acceptance criteria:**
  - `mvn test` green.
  - ArchUnit rule `platform_does_not_reference_stage_enums` activates and passes.
- **Depends on:** BA-T06
- **Blocks:** —

---

### Phase C — DTO and aggregation refactor

#### BA-T08: Rewrite `ReleaseFlowListItemDto` to generic stage map
- **Phase:** C
- **Design ref:** M6
- **Architecture ref:** PL-7
- **Effort:** M
- **Description:** Replace positional `sitStatus / uatStatus / prodStatus / sitPresent / uatPresent / prodPresent` fields with `Map<String, RequestStatus> stageStatuses` and `Set<String> stagesPresent`. Update both positional call sites (`ReleaseFlowListItemDto.from` factory and `ReleaseFlowService.buildStitchedSummary`). Update every test assertion that reads the old fields.
- **Files modified:** `ReleaseFlowListItemDto.java`, `ReleaseFlowService.java` (stitched summary builder), `ReleaseFlowListItemDtoTest.java`, `ReleaseFlowServiceTest.java`, any other test asserting on the positional fields
- **Acceptance criteria:**
  - `ReleaseFlowListItemDtoTest` 5 new cases from design §M6 pass (DEV-only, SIT+UAT, PROD-only, empty, archived-filtered).
  - Frontend snapshot tests for summary views break temporarily (will be fixed when the frontend factory lands in Phase G).
- **Depends on:** BA-T07
- **Blocks:** BA-T09, BA-T10

#### BA-T09: Rewrite `ReleaseFlowAggregation` to iterate observed stages
- **Phase:** C
- **Design ref:** M7
- **Architecture ref:** PL-3 consequence
- **Effort:** S
- **Description:** Replace `Stage.values()` iteration with iteration over the observed-stage set per design §M7.
- **Files modified:** `ReleaseFlowAggregation.java`
- **Acceptance criteria:**
  - `ReleaseFlowAggregationTest` passes unchanged semantics (see design §M7 proof note).
- **Depends on:** BA-T08
- **Blocks:** —

---

### Phase D — Stitching relocation

#### BA-T10: Move `ReleaseFlowFamilyKey` to `agents/deployment/domain/`
- **Phase:** D
- **Design ref:** D2
- **Architecture ref:** PL-5
- **Effort:** S
- **Description:** Move the file; update package declaration; change visibility to package-private. Do not change the regex. The test file also moves.
- **Files moved:** `ReleaseFlowFamilyKey.java`, `ReleaseFlowFamilyKeyTest.java`
- **Acceptance criteria:**
  - `mvn test` green.
  - Test file passes in new location.
- **Depends on:** BA-T02
- **Blocks:** BA-T11

#### BA-T11: Create `DeploymentStitchingService`
- **Phase:** D
- **Design ref:** D3, A1
- **Architecture ref:** PL-5
- **Effort:** L
- **Description:** Create `agents/deployment/domain/DeploymentStitchingService.java`. Port `listStitchedSummaries` and `getStitchedDetail` method bodies from platform `ReleaseFlowService` into this new service. Adapt the bodies to use `String stage` and the new `stageStatuses` map from M6. The old `ReleaseFlowService` methods still exist at this step — they will be deleted in BA-T12.
- **Files created:** `DeploymentStitchingService.java`, `DeploymentStitchingServiceTest.java`
- **Acceptance criteria:**
  - `DeploymentStitchingServiceTest` ports all v2 `ReleaseFlowServiceTest` stitched-summary and stitched-detail assertions and passes.
  - `mvn test` green (because the old platform methods still exist).
- **Depends on:** BA-T10, BA-T08
- **Blocks:** BA-T12

#### BA-T12: Delete stitching from `ReleaseFlowService`; add `listByAgent`
- **Phase:** D
- **Design ref:** M3
- **Architecture ref:** PL-5
- **Effort:** M
- **Description:** Delete `listStitchedSummaries` and `getStitchedDetail` from platform `ReleaseFlowService`. Add new method `Page<ReleaseFlow> listByAgent(String agentId, ReleaseFlowFilter filter, Pageable pageable)`. Update all callers:
  - Old `ReleaseFlowController` (Deployment Agent path) → call `DeploymentStitchingService` instead. Temporary state; the controller itself moves in Phase H.
  - Old `TestingAgentReleaseFlowController` → call `releaseFlowService.listByAgent("testing-agent", ...)`. Temporary state.
  - `ReleaseFlowFilter` value record is created.
- **Files modified:** `ReleaseFlowService.java`, old `ReleaseFlowController.java`, old `TestingAgentReleaseFlowController.java`
- **Files created:** `ReleaseFlowFilter.java`
- **Acceptance criteria:**
  - `mvn test` green.
  - `ReleaseFlowServiceTest` has new tests for `listByAgent_scopesByAgentColumn`, `listByAgent_excludesNullAgent`, `listByAgent_filtersByStageString`.
  - Old platform stitching method references no longer exist.
- **Depends on:** BA-T11
- **Blocks:** Phase H

---

### Phase E — Guard and Audit

#### BA-T13: Create `platform/web/security/AgentBoundaryGuard.java`
- **Phase:** E
- **Design ref:** M2
- **Architecture ref:** PL-9
- **Effort:** M
- **Description:** Create the guard component with three `@Transactional(readOnly = true)` assertion methods per design §M2. Unit tests per the 12-row matrix. No controllers call it yet in this commit; it is introduced to the codebase as a dormant component.
- **Files created:** `AgentBoundaryGuard.java`, `AgentBoundaryGuardTest.java`
- **Acceptance criteria:**
  - 12 unit test cases from design §M2 all pass.
  - `mvn test` green.
- **Depends on:** BA-T02
- **Blocks:** Phase H

#### BA-T14: `AuditLoggerService` dynamic `agentName`
- **Phase:** E
- **Design ref:** M8
- **Architecture ref:** PL-11
- **Effort:** S
- **Description:** Replace the hardcoded `"deployment-agent"` literal at `AuditLoggerService.java:61` with `scope.agent()`. Remove the v2 null fallback. Add an `IllegalStateException` guard for null scope agent. Update `AuditLoggerServiceTest` to include all four scenarios in design §M8 plus the null-scope guard assertion.
- **Files modified:** `AuditLoggerService.java`, `AuditLoggerServiceTest.java`
- **Acceptance criteria:**
  - All 4 test rows from design §M8 pass.
  - The null-scope case throws `IllegalStateException` with a clear message.
  - `mvn test` green. **Note:** Existing audit tests that rely on the old hardcoded value will fail here if they assert `agentName = "deployment-agent"` unconditionally. Fix them in the same commit.
- **Depends on:** —
- **Blocks:** —

---

### Phase F — Platform capability route migration

#### BA-T15: Move capability controllers to `/api/platform/*` + update `SecurityConfig` + update UAT runbook
- **Phase:** F
- **Design ref:** M9
- **Architecture ref:** PL-2, §API Boundaries Cutover Strategy
- **Effort:** M
- **Description:** Move `AuthController`, `AuditLogController`, `ConfigurationController`, `AccessGrantController` to `platform/web/shared/` package and update their `@RequestMapping` prefixes to `/api/platform/*`. Extract the template download endpoint from the current `UploadController` into a new `TemplateDownloadController` under `platform/web/shared/`. Update `SecurityConfig.java:36` whitelist to `/api/platform/auth/login`. No route aliases — this is the hard cutover. Also update `docs/UAT_RUNBOOK.md` — it currently contains 6+ curl examples against `/api/deployment-agent/auth/*`, `/api/deployment-agent/audit-logs`, and `/api/deployment-agent/access-grants` that will silently break after the cutover.
- **Files moved:** 4 controller files
- **Files created:** `TemplateDownloadController.java`
- **Files modified:** `SecurityConfig.java`; existing unit tests for the 5 capability controllers (update expected URL paths); `docs/UAT_RUNBOOK.md` (update all `/api/deployment-agent/auth/*`, `/api/deployment-agent/audit-logs`, and `/api/deployment-agent/access-grants` references to `/api/platform/*`)
- **Acceptance criteria:**
  - `PlatformRouteMigrationTest` (new integration test, design §M9 gate test) passes all 4 scenarios:
    - Unauthenticated `POST /api/platform/auth/login` → 2xx
    - Unauthenticated `POST /api/deployment-agent/auth/login` → 401 (old route removed)
    - Cookie from platform login works on `/api/deployment-agent/*`
    - Cookie from platform login works on `/api/build-agent/*` (when those routes land in Phase H)
  - All existing capability controller tests updated to new paths and passing.
  - `docs/UAT_RUNBOOK.md` grep for `/api/deployment-agent/(auth|audit-logs|config|access-grants|templates)` returns zero matches.
- **Depends on:** —
- **Blocks:** BA-T16

#### BA-T16: Frontend platform API client and migrated capability modules
- **Phase:** F
- **Design ref:** M10
- **Architecture ref:** PL-2
- **Effort:** M
- **Description:** Create `frontend/src/platform/api/platformClient.ts` (baseURL `/api/platform`, 401 interceptor). Move `auth.ts`, `audit.ts`, `config.ts`, `accessGrants.ts`, and create `templates.ts` — all bound to `platformClient`. Move platform stores (`user.ts`, `audit.ts`, `config.ts`, `accessGrants.ts`) to `frontend/src/platform/stores/`. Move platform shell views (`LoginView`, `WwaHomeView`, `WorkspaceLayout`, `AuditLogView`, `ConfigAdminView`, `AccessManagementView`, `TemplateManagementView`) to `frontend/src/platform/views/`. Update all imports throughout the frontend.
- **Files created:** `platformClient.ts`, `templates.ts`
- **Files moved:** 4 API modules, 4 stores, 7 views
- **Files modified:** Every file that imported the moved modules
- **Acceptance criteria:**
  - `cd frontend && npm run build` passes.
  - Manual smoke: local login via `/api/platform/auth/login` works and lands on the home page.
  - `LoginView` posts to the new URL (verify by inspecting the compiled code or via browser DevTools).
- **Depends on:** BA-T15
- **Blocks:** Phase I

---

### Phase G — Frontend factory

#### BA-T17: Create `createAgentWorkspace` factory and sub-factories
- **Phase:** G
- **Design ref:** M11, A4
- **Architecture ref:** PL-8
- **Effort:** L
- **Description:** Create `frontend/src/platform/composables/createAgentWorkspace.ts` with the full public signature from design §M11. Create sub-factories `createReleaseFlowApi.ts`, `createReleaseFlowStore.ts`. The factory does not yet replace any existing agent's code; it is introduced as a new tool.
- **Files created:** `createAgentWorkspace.ts`, `createReleaseFlowApi.ts`, `createReleaseFlowStore.ts`
- **Acceptance criteria:**
  - `createAgentWorkspace.test.ts` 7 cases from design §M11 all pass (baseURL binding, route generation, props for SummaryView, `?linked=` gating, distinct Pinia store IDs across two workspaces).
  - `cd frontend && npm run build` green.
- **Depends on:** BA-T16
- **Blocks:** BA-T18, Phase I

#### BA-T18: Create `AgentSummaryView` and `AgentDetailView` generic components
- **Phase:** G
- **Design ref:** M10, M11
- **Architecture ref:** PL-8
- **Effort:** M
- **Description:** Create the two generic view components in `frontend/src/platform/components/`. They read from a store and a config object injected by `createAgentWorkspace`. `AgentSummaryView` reads `stageStatuses` from DTO per M6; `AgentDetailView` passes `?linked=` through to the API when `supportsStitching` is true.
- **Files created:** `AgentSummaryView.vue`, `AgentDetailView.vue`, their `.test.ts` files
- **Acceptance criteria:**
  - Component tests pass.
  - `cd frontend && npm run build` green.
- **Depends on:** BA-T17
- **Blocks:** Phase I

---

### Phase H — Agent Module migrations (backend)

#### BA-T19: Migrate Deployment Agent controllers to `agents/deployment/web/`
- **Phase:** H
- **Design ref:** D4
- **Architecture ref:** PL-2, PL-6, PL-9
- **Effort:** L
- **Description:** Create `DeploymentReleaseFlowController`, `DeploymentUploadController`, `DeploymentTaskController`, `DeploymentDecisionController` under `agents/deployment/web/`. Each controller:
  - Forces `agent = "deployment-agent"` server-side.
  - Invokes `AgentBoundaryGuard` on every ID-bearing endpoint.
  - `DeploymentReleaseFlowController.list` scopes by `agent = "deployment-agent"` (PL-6 — removes the implicit global view).
  - `DeploymentDecisionController` calls `progressAfterDecision(taskId)` unchanged; pipeline resolution happens inside the service via `StagePipelineRegistry`.
  - Stitched linked detail is delegated to `DeploymentStitchingService`.
  Delete the old `web/controller/ReleaseFlowController`, `UploadController`, `TaskController`, `DecisionController`.
- **Files created:** 4 new controllers
- **Files deleted:** 4 old controllers
- **Files modified:** Controller integration tests rebind to new class names; add assertions for agent-scoped summary and guard-blocked cross-agent access.
- **Acceptance criteria:**
  - All existing Deployment Agent integration tests pass (after class-name rebinding).
  - Updated Deployment Agent summary test asserts `agent IS NULL` rows are invisible.
  - New test: cross-agent task probe with a Testing Agent task ID returns 404.
  - `mvn test` green.
- **Depends on:** BA-T12, BA-T13
- **Blocks:** BA-T23

#### BA-T20: Migrate Testing Agent controllers to `agents/testing/web/`
- **Phase:** H
- **Design ref:** T2
- **Architecture ref:** PL-2, PL-9
- **Effort:** M
- **Description:** Create `TestingReleaseFlowController`, `TestingUploadController`, `TestingTaskController`, `TestingDecisionController` under `agents/testing/web/`. Each invokes `AgentBoundaryGuard` (closes v2 R-08). `TestingReleaseFlowController.list` calls `releaseFlowService.listByAgent("testing-agent", ...)`. `TestingDecisionController` calls `progressAfterDecision(taskId)` with the unchanged signature; pipeline resolution happens inside the service. Delete old `TestingAgent*Controller` files.
- **Files created:** 4 new controllers
- **Files deleted:** `TestingAgentReleaseFlowController.java`, `TestingAgentTaskController.java`, `TestingAgentUploadController.java`
- **Acceptance criteria:**
  - Testing Agent integration tests pass after class-name rebinding.
  - New test: cross-agent task probe with a Deployment Agent task ID returns 404 (proof that v2 R-08 is closed).
  - `mvn test` green.
- **Depends on:** BA-T12, BA-T13
- **Blocks:** BA-T24

#### BA-T21: Create Build Agent controllers under `agents/build/web/`
- **Phase:** H
- **Design ref:** B2, B3, B4, B5
- **Architecture ref:** PL-10, BA-1, BA-2, BA-3
- **Effort:** L
- **Description:** Create `BuildReleaseFlowController`, `BuildUploadController`, `BuildTaskController`, `BuildDecisionController` per design §B2–B5 code skeletons. Every ID-bearing endpoint calls `AgentBoundaryGuard` with `AgentId.BUILD_AGENT`. `BuildReleaseFlowController.getById` does not read `?linked=`. `BuildUploadController` forces `agent = "build-agent"` and `stage = "DEV"`. `BuildDecisionController` calls `progressAfterDecision(taskId)` unchanged; pipeline resolution happens inside the service.
- **Files created:** 4 controllers
- **Acceptance criteria (integration tests, ~25 cases across 4 test files):**
  - `BuildReleaseFlowControllerTest`: list scoped to build-agent (6 cases)
  - `BuildUploadControllerTest`: upload forces agent + stage (5 cases)
  - `BuildTaskControllerTest`: guard on every endpoint (10 cases)
  - `BuildDecisionControllerTest`: guard + pipeline threading (4 cases)
  - `mvn test` green.
- **Depends on:** BA-T12, BA-T13, BA-T14
- **Blocks:** BA-T22, BA-T25

#### BA-T22: End-to-end Build Agent data isolation tests
- **Phase:** H
- **Design ref:** §9 (13 critical integration scenarios)
- **Architecture ref:** PL-5, PL-6, PL-9
- **Effort:** M
- **Description:** Create `BuildDataIsolationTest` covering the 13 critical integration scenarios in design §9.
- **Files created:** `BuildDataIsolationTest.java`
- **Acceptance criteria:**
  - All 13 scenarios pass.
  - `mvn test` green.
- **Depends on:** BA-T21
- **Blocks:** Phase J

---

### Phase I — Frontend migration

#### BA-T23: Create `frontend/src/agents/deployment/index.ts`; delete old flat Deployment Agent frontend files
- **Phase:** I
- **Design ref:** D5
- **Architecture ref:** PL-8
- **Effort:** M
- **Description:** Create `frontend/src/agents/deployment/index.ts` calling `createAgentWorkspace({ key: 'deployment-agent', supportsStitching: true, stageFilter: 'dropdown', stages: ['SIT','UAT','PROD'] })`. Wire the returned routes into the platform router. Delete `frontend/src/api/client.ts`, `releaseFlows.ts`, `tasks.ts`, `upload.ts`; `frontend/src/stores/releaseFlow.ts`, `task.ts`; `frontend/src/views/ReleaseFlowSummaryView.vue`, `ReleaseFlowDetailView.vue`.
- **Files created:** `index.ts`
- **Files deleted:** 4 api files, 2 stores, 2 views
- **Acceptance criteria:**
  - `cd frontend && npm run build` green.
  - Manual smoke: Deployment Agent summary loads, filters work, detail page works, stitched linked detail (via `?linked=`) still works.
- **Depends on:** BA-T17, BA-T18, BA-T19
- **Blocks:** BA-T25

#### BA-T24: Create `frontend/src/agents/testing/index.ts`; delete old Testing Agent frontend files
- **Phase:** I
- **Design ref:** T3
- **Architecture ref:** PL-8
- **Effort:** M
- **Description:** Create `frontend/src/agents/testing/index.ts`. Wire routes into the platform router. Delete `testingAgentClient.ts`, `testingAgentReleaseFlows.ts`, `testingAgentTasks.ts`, `testingAgentUpload.ts`, `testingAgentReleaseFlow.ts` (store), `TestingAgentSummaryView.vue`, `TestingAgentDetailView.vue`.
- **Files created:** `index.ts`
- **Files deleted:** 4 api files, 1 store, 2 views
- **Acceptance criteria:**
  - `cd frontend && npm run build` green.
  - Manual smoke: Testing Agent summary loads, upload dialog shows UAT as disabled input.
- **Depends on:** BA-T17, BA-T18, BA-T20
- **Blocks:** BA-T25

#### BA-T25: Create `frontend/src/agents/build/index.ts`; register Build Agent in `agentRegistry.ts` and router
- **Phase:** I
- **Design ref:** B6
- **Architecture ref:** PL-8
- **Effort:** S
- **Description:** Create `frontend/src/agents/build/index.ts` (~20 lines). Add a Build Agent entry to `platform/config/agentRegistry.ts`. Extend `AgentCategory` type to include `'build'`. Wire the factory-returned routes into the platform router at `/wwa/build-agent` and `/wwa/build-agent/release-flows/:id`. Add `AgentId.BUILD_AGENT` to frontend constants file.
- **Files created:** `index.ts`
- **Files modified:** `agentRegistry.ts`, router config, `agentId.ts`
- **Acceptance criteria:**
  - `cd frontend && npm run build` green.
  - Manual smoke: Build Agent card appears on home page; clicking it lands on `/wwa/build-agent`; upload dialog shows DEV as disabled input; summary shows the DEV column.
- **Depends on:** BA-T17, BA-T18, BA-T21
- **Blocks:** Phase J

---

### Phase J — Verification

#### BA-T26: Full backend regression (`mvn test`) + ArchUnit fitness checks
- **Phase:** J
- **Design ref:** §7 (ArchUnit rules), §8.1 (backend test matrix)
- **Architecture ref:** R-01 mitigation
- **Effort:** S
- **Description:** Run `mvn test` clean. Confirm all 6 ArchUnit rules in design §7 pass. Confirm all existing Deployment Agent and Testing Agent integration tests pass after Phase H migration.
- **Acceptance criteria:**
  - `mvn test` exits 0.
  - ArchUnit report shows 0 violations.
  - No flaky tests introduced.
- **Depends on:** BA-T22, BA-T23, BA-T24, BA-T25
- **Blocks:** BA-T27

#### BA-T27: Frontend build + manual smoke of 13 critical scenarios + P-01 gate check
- **Phase:** J
- **Design ref:** §9
- **Architecture ref:** R-09 mitigation, P-01 (legacy null-agent visibility precondition)
- **Effort:** M
- **Description:** Run `cd frontend && npm run build` clean. Manually execute the 13 critical integration scenarios from design §9 against a running local instance (`mvn spring-boot:run -Dspring-boot.run.profiles=local`). Record any UX regressions. **Before marking this task complete, verify P-01 (§10 hard precondition) is resolved** — either via recorded product sign-off on the legacy null-agent visibility change, or via a landed backfill migration, or via Global View being pulled into scope. If P-01 is still open, this task cannot be marked complete and the delivery cannot merge to main.
- **Acceptance criteria:**
  - Frontend build exits 0.
  - All 13 manual scenarios pass.
  - Session cookie preservation scenario (#11) verified end-to-end: login → access Deployment Agent → access Build Agent without re-login.
  - **P-01 is resolved** (§10 hard precondition). Resolution is recorded in either a linked decision document, a product-owner PR approval, or a landed backfill migration task before this gate.
- **Depends on:** BA-T26, **P-01 resolution**
- **Blocks:** BA-T28

#### BA-T28: Release notes + follow-up ticket creation
- **Phase:** J
- **Design ref:** —
- **Architecture ref:** R-02, R-04, R-08, R-12
- **Effort:** S
- **Description:** Write release notes covering:
  - Breaking platform route change (`/api/deployment-agent/*` capabilities → `/api/platform/*`) with the full route mapping.
  - Legacy `agent IS NULL` data visibility removed until Global View ships (architecture R-04).
  - `AuditLoggerService.agentName` forward-only fix for Testing Agent historical defect (architecture R-08).
  - Per-agent Stage enum removal (no user-visible effect; documented for developers).
  Create follow-up tickets for:
  - Global View feature (architecture R-04)
  - Template download file-name unification (architecture R-12)
  - `createAgentWorkspace` factory enhancements if any were deferred during Phase I.
- **Acceptance criteria:**
  - Release notes PR created and approved.
  - Follow-up tickets exist in the issue tracker.
- **Depends on:** BA-T27
- **Blocks:** —

---

## 5. Parallel Work Opportunities

Tasks within a phase can sometimes run concurrently. The following groups are independent and can be worked by separate engineers / branches:

- **Phase A:** BA-T01 and BA-T02 are fully independent.
- **Phase B:** BA-T03 and BA-T01 are independent once the empty packages exist. BA-T04 depends on both.
- **Phase E:** BA-T13 (guard) and BA-T14 (audit) are fully independent and can land in parallel.
- **Phase F:** BA-T15 (backend) and BA-T16 (frontend) must be sequential because BA-T16 depends on the new routes existing.
- **Phase G:** BA-T17 and BA-T18 are sequential (components depend on factory sub-types).
- **Phase H:** BA-T19, BA-T20, BA-T21 are fully independent of each other once BA-T12 and BA-T13 have landed. Three engineers can migrate the three agents concurrently.
- **Phase I:** BA-T23, BA-T24, BA-T25 are fully independent once Phase G + their matching Phase H task has landed. Again, three concurrent streams are possible.

**Serialization-mandatory boundaries:**
- Phase B must serialize end-to-end (each task depends on the previous).
- Phase C must complete before Phase D (stitching) and Phase H (controllers).
- Phase E must complete before Phase H (controllers invoke the guard).
- Phase F must complete before Phase I (frontend needs `/api/platform/*` routes to exist).
- Phase G must complete before Phase I (frontend factory must exist before agents use it).

---

## 6. Dependency Graph (Critical Path)

```
BA-T01 ──┐
         ├─► BA-T04 ─► BA-T05 ─► BA-T06 ─► BA-T07 ─► BA-T08 ─► BA-T09
BA-T02 ──┤                                                      │
BA-T03 ──┘                                                      ▼
                                                      BA-T10 ─► BA-T11 ─► BA-T12 ┐
                                                                                  │
                                                      BA-T13 ───────────────────► ┤
                                                      BA-T14 ───────────────────► ┤
                                                                                  ▼
                                                      BA-T15 ─► BA-T16            │
                                                                  │                │
                                                                  ▼                │
                                                      BA-T17 ─► BA-T18             │
                                                                  │                │
                                                                  ▼                ▼
                                                     ┌─ BA-T19 ─► BA-T23 ┐
                                                     ├─ BA-T20 ─► BA-T24 ┼─► BA-T22 ─► BA-T26 ─► BA-T27 ─► BA-T28
                                                     └─ BA-T21 ─► BA-T25 ┘
```

**Critical path (longest chain):** BA-T01 → BA-T04 → BA-T05 → BA-T06 → BA-T07 → BA-T08 → BA-T09 → BA-T12 → BA-T19 → BA-T23 → BA-T26 → BA-T27 → BA-T28

This path has 13 tasks and represents the minimum serial length of the delivery. Parallelism in Phases B/H/I does not shorten the critical path; it reduces wall-clock time for the team overall.

---

## 7. Task Summary

| Task | Phase | Effort | Depends on | Blocks |
|---|---|---|---|---|
| BA-T01 | A | S | — | BA-T04, BA-T05 |
| BA-T02 | A | S | — | (all later) |
| BA-T03 | B | S | BA-T02 | BA-T04 |
| BA-T04 | B | S | BA-T01, BA-T03 | BA-T05 |
| BA-T05 | B | M | BA-T04 | BA-T06 |
| BA-T06 | B | L | BA-T05 | BA-T07 |
| BA-T07 | B | S | BA-T06 | — |
| BA-T08 | C | M | BA-T07 | BA-T09, BA-T10 |
| BA-T09 | C | S | BA-T08 | — |
| BA-T10 | D | S | BA-T02 | BA-T11 |
| BA-T11 | D | L | BA-T10, BA-T08 | BA-T12 |
| BA-T12 | D | M | BA-T11 | Phase H |
| BA-T13 | E | M | BA-T02 | Phase H |
| BA-T14 | E | S | — | — |
| BA-T15 | F | M | — | BA-T16 |
| BA-T16 | F | M | BA-T15 | Phase I |
| BA-T17 | G | L | BA-T16 | BA-T18, Phase I |
| BA-T18 | G | M | BA-T17 | Phase I |
| BA-T19 | H | L | BA-T12, BA-T13 | BA-T23 |
| BA-T20 | H | M | BA-T12, BA-T13 | BA-T24 |
| BA-T21 | H | L | BA-T12, BA-T13, BA-T14 | BA-T22, BA-T25 |
| BA-T22 | H | M | BA-T21 | Phase J |
| BA-T23 | I | M | BA-T17, BA-T18, BA-T19 | BA-T25 (parallel-safe) |
| BA-T24 | I | M | BA-T17, BA-T18, BA-T20 | (parallel-safe) |
| BA-T25 | I | S | BA-T17, BA-T18, BA-T21 | Phase J |
| BA-T26 | J | S | BA-T22, BA-T23, BA-T24, BA-T25 | BA-T27 |
| BA-T27 | J | M | BA-T26 | BA-T28 |
| BA-T28 | J | S | BA-T27 | — |

**Effort legend:** S = < 0.5 day, M = 0.5–2 days, L = 2–5 days. Numbers are engineer-day estimates for a developer familiar with the codebase; multiply if onboarding a new contributor.

**Rollup by phase:**

| Phase | Tasks | S | M | L |
|---|---|---|---|---|
| A | 2 | 2 | 0 | 0 |
| B | 5 | 2 | 1 | 2 |
| C | 2 | 1 | 1 | 0 |
| D | 3 | 1 | 1 | 1 |
| E | 2 | 1 | 1 | 0 |
| F | 2 | 0 | 2 | 0 |
| G | 2 | 0 | 1 | 1 |
| H | 4 | 0 | 2 | 2 |
| I | 3 | 1 | 2 | 0 |
| J | 3 | 2 | 1 | 0 |
| **Total** | **28** | **10** | **12** | **6** |

---

## 8. Risks and Blockers (Execution-Level)

These are execution risks that show up while running the task list. Architecture-level risks (R-01 through R-12) live in `build-agent-architecture.md` §Open Architecture Risks and are not repeated here — this section only tracks mitigations that are actionable at task-execution time.

| ID | Risk | Triggering phase | Mitigation (who does what) |
|---|---|---|---|
| X-01 | BA-T06 (JPA attribute String migration) breaks a test class we did not anticipate | Phase B | Land BA-T06 on a dedicated feature branch; run `mvn test` locally before pushing; if a test fails, fix it in the same commit — do not split |
| X-02 | `DeploymentStitchingService` port loses a subtle edge case from the v2 `listStitchedSummaries` (e.g. attempt-view semantics) | Phase D (BA-T11) | Port the test file at the same time as the service; confirm the ported test file still passes before starting BA-T12 |
| X-03 | BA-T15 platform route cutover takes effect before BA-T16 frontend migration ships, locking out local developers | Phase F | Land BA-T15 and BA-T16 in the same PR/commit pair; do not merge BA-T15 alone to main |
| X-04 | `SecurityConfig.java:36` whitelist update is forgotten alongside BA-T15, making login unreachable in test environments | Phase F (BA-T15) | `PlatformRouteMigrationTest` scenario #1 (unauthenticated POST to `/api/platform/auth/login` → 2xx) is the gate; CI blocks the commit if it fails |
| X-05 | Deployment Agent integration tests that assert "global view" behavior still exist after BA-T19 and fail | Phase H (BA-T19) | Grep the test suite for `agent IS NULL` or "all flows visible" assertions before starting BA-T19; fix or delete them in the same commit |
| X-06 | `AgentBoundaryGuard` retroactively breaks Deployment Agent archived-flow visibility if `includeArchived=true` is not set | Phase H (BA-T19) | Design §M2 specifies `includeArchived=true` in the guard; verify the implementation matches; add a test for archived-flow access |
| X-07 | Testing Agent has an active development branch touching `TestingAgent*Controller` that collides with BA-T20 | Phase H (BA-T20) | Coordinate merge order with `Testing-Agent/Develop-leo` (or whatever branch exists at merge time); rebase before BA-T20 lands |
| X-08 | Frontend factory edge cases surface during Phase I Deployment Agent migration (BA-T23) that the factory does not support | Phase I (BA-T23) | Migrate Testing Agent (BA-T24) or Build Agent (BA-T25) first — simpler agents validate the factory; only then tackle Deployment Agent. Add missing factory config options as discovered, keeping BA-T17 on a branch until BA-T23 confirms coverage |
| X-09 | Phase J manual smoke reveals a session-cookie failure across the route cutover | Phase J (BA-T27) | If reproduced, check `application.properties` for a stray `server.servlet.session.cookie.path` override and remove; re-test. If not reproduced, ship |

---

## 9. Follow-Up Tasks (Separate PRs)

The following items are intentionally out of scope for this delivery and are tracked as separate PRs to keep the scope bounded.

| ID | Title | Source | Blocks future work? |
|---|---|---|---|
| FU-01 | Platform-level Global View page (cross-agent flow listing) | Architecture R-04, PL-8 | Blocks visibility of legacy null-agent data |
| FU-02 | Template download file-name unification (`request-template.xlsx` across all agents) | Spec §10.1 note, Architecture R-12 | No |
| FU-03 | Controller-level `@Transactional` for `AgentBoundaryGuard` performance mitigation (if Phase J benchmarks reveal a problem) | Architecture R-11 mitigation path | No (contingent on measurement) |
| FU-04 | Backfill migration for legacy `Request.agent IS NULL` rows | Architecture R-04 follow-up | No |
| FU-05 | Agent Module Maven multi-module split (if ArchUnit boundary discipline proves insufficient) | Architecture PL-2 alternative | No |
| FU-06 | `createAgentWorkspace` factory enhancements for agent-specific view overrides via slots, if a 4th agent surfaces requirements not expressible in config | PL-8 scaling | No |
| FU-07 | Extract shared `Jenkins`/`Ansible` execution adapter enhancements to support Build Agent-specific build artifacts (when product requirements appear) | Out of scope this delivery | No |

---

## 10. Open Questions and Preconditions

The first entry is a **hard precondition** that must be resolved before this delivery ships. The others are soft defaults that can proceed without explicit answers.

### Hard precondition

- **P-01. Product sign-off on legacy `agent IS NULL` visibility (BLOCKER).** Under PL-6, legacy `Request` rows with `agent IS NULL` become invisible from every agent workspace — Deployment Agent no longer shows them (it used to, in v2), Build Agent and Testing Agent never did. The rows remain in the database untouched; they become visible again only when the platform-level Global View ships (FU-01, not part of this delivery). This is a user-visible behavior change for anyone who currently relies on Deployment Agent's historical "global view" of pre-agent-column data.

  This delivery **cannot merge to main** until one of the following is true:
  1. Product and operations explicitly sign off that hiding null-agent rows until Global View ships is acceptable. Captured as a linked decision document or PR approval from a named product owner. Recorded in BA-T28 release notes.
  2. OR a backfill migration task is added to this delivery (set `agent = "deployment-agent"` on all null rows) and landed before BA-T27. This changes the scope of the delivery and adds roughly one S-effort task.
  3. OR Global View (FU-01) is pulled into this delivery's scope. This is a significant scope increase (new platform endpoint, new page, new query, new permission check) and is not recommended.

  **Status:** OPEN. Not yet resolved. Owner: needs assignment to a named product owner. This entry is tracked as a merge-blocker for BA-T27 and BA-T28 — neither task can be marked complete until P-01 has an explicit resolution.

### Soft defaults

- **Q-01.** Phase H ordering: should BA-T19 (Deployment Agent migration) land before BA-T20 and BA-T21, or simultaneously? The critical path assumes they are independent; if the team prefers to land one agent at a time for confidence, BA-T20 and BA-T21 can wait until BA-T19's migration has been in main for a short soak period. **Default:** concurrent.
- **Q-02.** BA-T28 release-notes audience: is this an internal engineering change-log or also a user-facing release note? The breaking route change (`/api/platform/*`) has no user-visible effect (login still works via the same URL from the user's perspective), but developers operating the system need to know. **Default:** internal engineering change-log only.
- **Q-03.** `AgentBoundaryGuard` performance benchmark (BA-T27 / architecture R-11) — is it a blocking gate or an informational measurement? **Default:** informational; ship if functional correctness is verified. Mitigation FU-03 is contingent on measured regression.

Answers to P-01 must be explicit. Answers to Q-01/Q-02/Q-03 can default.
