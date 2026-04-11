# Build Agent — Implementation Plan

**Date:** 2026-04-11
**Branch:** `build-agent-leo`
**Source:** `docs/06-tasks/build-agent-tasks.md` v3 (28 tasks + BA-T26.1 sub-task, 10 phases)
**Execution cadence:** Single-dev serial (per user directive)
**Brownfield baseline:** `mvn test` 318/0/0, `npm run build` exit 0

> **Plan Authority:** This document is a delta execution plan derived from the source tasks file above.
> Where this plan's acceptance criteria, execution notes, or task descriptions differ from the source
> tasks file, **this plan takes precedence** — those differences reflect grounding corrections against
> the actual repo state (e.g., BA-T19 P-01 correction, BA-T26.1 verification-only scope). Executors
> must not revert to source-tasks wording without updating this plan first.

---

## Pre-Implementation Status

| Gate | Status |
|------|--------|
| P-01 (null-agent backfill) | RESOLVED — V13 migration landed |
| Q-01 (Phase H ordering) | Approved default: concurrent |
| Q-02 (release notes audience) | Approved default: internal |
| Q-03 (AgentBoundaryGuard perf) | Approved default: informational |
| Skill/docs alignment | DONE — `tasks-to-code`/`tasks-to-implementation` synced |
| Brownfield baseline | GREEN — 318 tests, frontend build clean |

---

## Grounding Anchors (verified against codebase)

These are the critical call-site and file references from tasks.md verified against the actual repo:

| Anchor | tasks.md claim | Actual | Status |
|--------|---------------|--------|--------|
| `Stage.java` | `contracts/enums/Stage.java` with SIT/UAT/PROD + `next()` | Verified: `Stage { SIT, UAT, PROD }` with `next()` returning null at PROD | OK |
| Terminal check | `ReleaseFlowProgressionService.java:72` — `releaseFlow.getCurrentStage().next() == null` | Verified at line 72 | OK |
| Stitching methods | `ReleaseFlowService.listStitchedSummaries(...)` and `getStitchedDetail(...)` | Verified: lines 172 and 241 | OK |
| Audit hardcode | `AuditLoggerService.java:61` — `entry.setAgentName("deployment-agent")` | Verified at line 61 | OK |
| SecurityConfig whitelist | Line 36 — `.requestMatchers("/api/deployment-agent/auth/login").permitAll()` | Verified | OK |
| ReleaseFlowFamilyKey | `domain/releaseflow/ReleaseFlowFamilyKey.java` | Verified | OK |
| AgentId constants | `DEPLOYMENT_AGENT`, `TESTING_AGENT` only | Verified — `BUILD_AGENT` not yet present | OK |
| `platform/` package | Does not exist yet | Confirmed — needs creation | OK |
| `agents/` package | Does not exist yet | Confirmed — needs creation | OK |
| Testing Agent controllers | `TestingAgentReleaseFlowController`, `TestingAgentTaskController`, `TestingAgentUploadController` | Verified — no `TestingAgentDecisionController` exists (Testing Agent has no decision endpoint in v2) | NOTE |
| Frontend `UploadDialog` | Shared component, props-injected | Verified in `components/UploadDialog.vue` | OK |
| `ReleaseFlowListItemDto` | Has positional `sitStatus/uatStatus/prodStatus/sitPresent/uatPresent/prodPresent` | Verified | OK |
| `ReleaseFlowAggregation` | Iterates `Stage.values()` | Verified | OK |

### Grounding findings that affect execution

1. **Testing Agent has no DecisionController in v2.** BA-T20 calls for `TestingDecisionController` creation — this is a new controller, not a migration. The task description says "Delete old `TestingAgent*Controller` files" but there is no `TestingAgentDecisionController` to delete.

2. **`ReleaseFlowProgressionService` callers:** tasks.md lists 5 call sites: `DecisionController`, `TestingAgentTaskController`, `RecordResultService`, `AutoExecutionService`, `ExternalExecutionMonitorService`. Need to verify all 5 still exist at execution time for BA-T05.

3. **V13 migration already landed** (P-01 backfill). This changes the BA-T19 acceptance criterion: "Updated Deployment Agent summary test asserts `agent IS NULL` rows are invisible" — after backfill, there are no null-agent rows. The test should assert that `agent = 'deployment-agent'` rows are visible and other-agent rows are not.

---

## Execution Batches

Serial execution, one batch per phase. Each batch ends with `mvn test` and/or `npm run build`.

### Batch 1: Phase A — Platform scaffolding
**Tasks:** BA-T01, BA-T02
**Validation gate:** `mvn test` green

| Task | What | Files |
|------|------|-------|
| BA-T01 | `StagePipeline` interface (4 methods, javadoc, fail-loud contract) | `platform/domain/StagePipeline.java` |
| BA-T02 | Empty package scaffolds (12 `package-info.java`) + ArchUnit rule `agents_do_not_depend_on_each_other` | 12 `package-info.java` files across `platform/`, `agents/{deployment,testing,build}/{domain,web}/` |

**Risk:** None. Pure scaffolding, no behavior change.

---

### Batch 2: Phase B — Stage vocabulary migration
**Tasks:** BA-T03 → BA-T04 → BA-T05 → BA-T06 → BA-T07 (strictly serial)
**Validation gate:** `mvn test` green after each task

| Task | What | Key risk |
|------|------|----------|
| BA-T03 | 3 per-agent Stage enums + 3 unit test files | None |
| BA-T04 | 3 `StagePipeline` impls + `StagePipelineRegistry` + tests (11-row contract, 4-row registry) | Registry startup validation |
| BA-T05 | Rewrite terminal check at `ReleaseFlowProgressionService:72` via registry lookup | Must NOT change 5 caller signatures (grep gate) |
| BA-T06 | JPA attribute `Stage` → `String` across ~15 files | **Largest mechanical commit.** X-01 risk: unexpected test breakage. Run `mvn test` before pushing. |
| BA-T07 | Delete `Stage.java` + activate ArchUnit rule | Must confirm zero imports remain first |

**Critical constraint for BA-T05:** Method signature `progressAfterDecision(String taskId)` is unchanged. The 5 caller files (`DecisionController`, `TestingAgentTaskController`, `RecordResultService`, `AutoExecutionService`, `ExternalExecutionMonitorService`) must NOT appear in `git diff --stat`.

---

### Batch 3: Phase C — DTO and aggregation refactor
**Tasks:** BA-T08, BA-T09
**Validation gate:** `mvn test` green

| Task | What | Key risk |
|------|------|----------|
| BA-T08 | `ReleaseFlowListItemDto` → generic `stageStatuses` map + `stagesPresent` set | All summary-view tests break temporarily; fix in same commit |
| BA-T09 | `ReleaseFlowAggregation` iterate observed stages instead of `Stage.values()` | Semantics should be unchanged (design §M7 proof) |

---

### Batch 4: Phase D — Stitching relocation
**Tasks:** BA-T10 → BA-T11 → BA-T12
**Validation gate:** `mvn test` green

| Task | What | Key risk |
|------|------|----------|
| BA-T10 | Move `ReleaseFlowFamilyKey` to `agents/deployment/domain/` | Package-private visibility change |
| BA-T11 | Create `DeploymentStitchingService` (port from `ReleaseFlowService`) | X-02: subtle edge-case loss; port tests alongside service |
| BA-T12 | Delete stitching from `ReleaseFlowService`; add `listByAgent`; create `ReleaseFlowFilter` | Temporary caller rewiring of old controllers |

---

### Batch 5: Phase E — Guard + Audit
**Tasks:** BA-T13, BA-T14 (independent, can land in either order)
**Validation gate:** `mvn test` green

| Task | What | Key risk |
|------|------|----------|
| BA-T13 | `AgentBoundaryGuard` (3 assertion methods, 12-row test matrix) | Dormant component — no controllers call it yet |
| BA-T14 | `AuditLoggerService` dynamic `agentName` (replace hardcoded `"deployment-agent"` at line 61) | Existing audit tests that assert hardcoded value will break — fix in same commit |

---

### Batch 6: Phase F — Platform capability route migration
**Tasks:** BA-T15 → BA-T16 (strictly serial; X-03 risk: must land together)
**Validation gate:** `mvn test` green + `npm run build` green

| Task | What | Key risk |
|------|------|----------|
| BA-T15 | Move 4 capability controllers to `/api/platform/*` + `TemplateDownloadController` + `SecurityConfig` update + UAT runbook update | X-04: forgotten SecurityConfig whitelist → login unreachable |
| BA-T16 | Frontend `platformClient.ts` + move 4 API modules, 4 stores, 7 views to `platform/` | Import-path update across entire frontend |

**Hard rule:** BA-T15 and BA-T16 land in the same commit/PR pair. Do not merge BA-T15 alone.

**Rollback notes (Phase F):**

| Failure mode | First response | Full rollback |
|---|---|---|
| Login unreachable (SecurityConfig whitelist gap) | Add `/api/platform/auth/login` to `permitAll()` without reverting routes | If not resolved in < 30 min: `git revert` BA-T15 + BA-T16 together |
| Session cookie lost across route boundary | Verify `server.servlet.session.cookie.path=/` in `application.properties`; hot-fix property | Revert both commits if cookie path cannot be set without restart in local dev |
| Frontend 404 on platform API calls | Verify `platformClient.ts` `baseURL` and Vite proxy entry for `/api/platform` | Revert BA-T16 first, then BA-T15 |
| Partial merge (BA-T15 merged, BA-T16 blocked) | Do NOT ship. BA-T15 moves `@RequestMapping` on controllers — SecurityConfig cannot restore deleted handler mappings. The only valid response is `git revert <BA-T15-sha>` immediately. | Revert BA-T15; do not attempt dual-route aliases (tasks.md: "No route aliases — this is the hard cutover") |

Revert command: `git revert <BA-T16-sha> <BA-T15-sha>` (revert newest first to avoid conflict).

---

### Batch 7: Phase G — Frontend factory
**Tasks:** BA-T17 → BA-T18
**Validation gate:** `npm run build` green

| Task | What | Key risk |
|------|------|----------|
| BA-T17 | `createAgentWorkspace` factory + sub-factories (`createReleaseFlowApi`, `createReleaseFlowStore`) | Core abstraction — 7 test cases from design §M11 |
| BA-T18 | `AgentSummaryView.vue` + `AgentDetailView.vue` generic components | Must consume `stageStatuses` map from M6 DTO |

---

### Batch 8: Phase H — Agent Module migrations (backend)
**Tasks:** BA-T19, BA-T20, BA-T21, BA-T22 (serial per single-dev cadence; H tasks depend on D+E)
**Validation gate:** `mvn test` green after each task

| Task | What | Key risk |
|------|------|----------|
| BA-T19 | Deployment Agent: 4 new controllers in `agents/deployment/web/`; delete 4 old controllers | X-05: "global view" test assertions; X-06: archived-flow guard |
| BA-T20 | Testing Agent: 4 new controllers in `agents/testing/web/`; delete 3 old controllers | X-07: branch collision; NOTE: TestingDecisionController is NEW |
| BA-T21 | Build Agent: 4 new controllers in `agents/build/web/` | New agent — no deletions. Forces `agent="build-agent"`, `stage="DEV"` |
| BA-T22 | `BuildDataIsolationTest` — 13 critical scenarios | Pure test task |

**BA-T19 note on P-01:** After V13 backfill, there are no `agent IS NULL` rows. The "null rows invisible" test becomes "only `deployment-agent` rows visible, other-agent rows excluded."

---

### Batch 9: Phase I — Frontend migration
**Tasks:** BA-T23, BA-T24, BA-T25 (serial per single-dev; X-08 suggests testing/build first)
**Validation gate:** `npm run build` green after each task

| Task | What | Key risk |
|------|------|----------|
| BA-T24 | Testing Agent `index.ts` via factory; delete 7 old files | Simpler agent — validates factory first (X-08 mitigation) |
| BA-T25 | Build Agent `index.ts` + `agentRegistry.ts` registration + router | New agent — simplest factory call |
| BA-T23 | Deployment Agent `index.ts` via factory; delete 8 old files | Most complex (stitching, 3 stages) — last to validate factory |

**Execution order override:** BA-T24 → BA-T25 → BA-T23 (per X-08 risk mitigation: simpler agents first).

---

### Batch 10: Phase J — Verification
**Tasks:** BA-T26 → BA-T26.1 → BA-T27 → BA-T28
**Validation gate:** Full regression

| Task | What |
|------|------|
| BA-T26 | `mvn test` clean + ArchUnit 6 rules |
| BA-T26.1 | Verify V13 backfill migration (already landed — confirm `agent IS NULL` count = 0) |
| BA-T27 | `npm run build` + 13 manual smoke scenarios (see table below) |
| BA-T28 | Release notes + follow-up ticket creation |

**BA-T27 — Smoke Scenario Definitions**

These scenarios correspond 1:1 to design §9 "Critical Integration Test Scenarios" (`docs/05-design/build-agent-design.md` lines 1523–1541). BA-T22 (`BuildDataIsolationTest`) covers them as automated integration tests; BA-T27 re-validates them as manual end-to-end smoke against the running app with the local profile.

Pass/fail results must be recorded as a checklist comment on the PR before BA-T28 is started.

| ID | Design §9 # | Scenario | Expected result |
|----|-------------|----------|----------------|
| SM-01 | 1 | Upload via Build Agent → Build Agent summary | Row visible, `stageStatuses["DEV"]` populated |
| SM-02 | 2 | Same upload → Deployment Agent summary | Row **not** visible (PL-6 isolation) |
| SM-03 | 3 | Same upload → Testing Agent summary | Row not visible |
| SM-04 | 4 | Upload `DEV-1234` twice through Build Agent | Single row; second upload upserts into first (§5.8) |
| SM-05 | 5 | Build Agent `DEV-1234` + Deployment Agent `SIT-1234` | Two separate `DA_RELEASE_FLOW` rows; neither stitches into the other |
| SM-06 | 6 | `GET /api/build-agent/tasks/{deployment-agent-task-id}` | HTTP 404 |
| SM-07 | 7 | `POST /api/build-agent/tasks/{testing-agent-task-id}/decision` | HTTP 404; underlying task unmodified |
| SM-08 | 8 | Approve all tasks in a Build Agent DEV flow | Flow becomes `Completed`, does not auto-advance to next stage |
| SM-09 | 9 | `GET /api/build-agent/release-flows/{id}?linked=abc,def` | Build Agent ignores `linked`; returns single flow |
| SM-10 | 10 | Audit trail after Build Agent action | `agentName = "build-agent"` in `AUDIT_LOG` |
| SM-11 | 11 | Log in via `/api/platform/auth/login` → call any agent endpoint | Same `JSESSIONID` works across prefixes |
| SM-12 | 12 | Log in via `/api/platform/auth/login` → `GET /api/deployment-agent/auth/login` | HTTP 404 (old route removed) |
| SM-13 | 13 | Testing Agent cross-agent task probe (was R-08 in v2) | HTTP 404 (closed by PL-9) |

---

## Dependency Graph (execution order)

```
Batch 1 (A):  BA-T01, BA-T02
     ↓
Batch 2 (B):  BA-T03 → BA-T04 → BA-T05 → BA-T06 → BA-T07
     ↓
Batch 3 (C):  BA-T08 → BA-T09
     ↓
Batch 4 (D):  BA-T10 → BA-T11 → BA-T12
     ↓
Batch 5 (E):  BA-T13, BA-T14
     ↓
Batch 6 (F):  BA-T15 → BA-T16
     ↓
Batch 7 (G):  BA-T17 → BA-T18
     ↓
Batch 8 (H):  BA-T19 → BA-T20 → BA-T21 → BA-T22
     ↓
Batch 9 (I):  BA-T24 → BA-T25 → BA-T23  (order override per X-08)
     ↓
Batch 10 (J): BA-T26 → BA-T26.1 → BA-T27 → BA-T28
```

---

## Validation Gates Summary

| After batch | Backend gate | Frontend gate |
|-------------|-------------|---------------|
| 1 (A) | `mvn test` | — |
| 2 (B) | `mvn test` after each task | — |
| 3 (C) | `mvn test` | — |
| 4 (D) | `mvn test` | — |
| 5 (E) | `mvn test` | — |
| 6 (F) | `mvn test` | `npm run build` |
| 7 (G) | — | `npm run build` |
| 8 (H) | `mvn test` after each task | — |
| 9 (I) | — | `npm run build` after each task |
| 10 (J) | `mvn test` (full) | `npm run build` + 13 manual smoke |

---

## Risk Register (execution-level)

| ID | Risk | Phase | Mitigation |
|----|------|-------|------------|
| X-01 | BA-T06 JPA String migration breaks unexpected tests | B | Dedicated branch; fix in same commit |
| X-02 | `DeploymentStitchingService` port loses edge case | D | Port tests alongside service |
| X-03 | BA-T15 route cutover before BA-T16 frontend | F | Land together in same PR |
| X-04 | SecurityConfig whitelist forgotten | F | `PlatformRouteMigrationTest` gate |
| X-05 | "Global view" test assertions survive BA-T19 | H | Grep before starting |
| X-06 | Guard breaks archived-flow visibility | H | Verify `includeArchived=true` in guard |
| X-07 | Testing Agent branch collision | H | Coordinate merge order |
| X-08 | Factory edge cases in Deployment Agent migration | I | Migrate simpler agents first |
| X-09 | Session cookie failure across route cutover | J | Check `cookie.path` config |

---

## Planning Assumptions Carried Forward

- `Request.agent` column already exists (V6 migration) — verified
- V13 backfill migration resolves P-01 — landed
- `JSESSIONID` cookie `Path=/` — will verify in BA-T15
- `spring.jpa.open-in-view=false` — will verify in BA-T05
- No backfill needed beyond V13 (all legacy rows now have `agent = 'deployment-agent'`)
- Testing Agent TestingDecisionController is new (not a migration) — grounded

---

## Stop Conditions

Halt and escalate if any of the following occur:

1. `mvn test` fails after a batch and the failure is not attributable to the batch's changes
2. A grounding anchor (method signature, file path, line number) has drifted since this plan was written
3. BA-T06 JPA migration affects more than ~20 files (indicates unexpected coupling)
4. Phase F route cutover breaks session cookie propagation
5. Phase I factory migration requires >2 new factory config options not in the design
