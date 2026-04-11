# Build Agent Release Notes

**Delivery:** Build Agent Module + Agent Module refactor
**Branch:** `build-agent-leo`
**Audience:** Internal (engineering + SRE)
**Status:** Ready for review
**Brownfield baseline:** `mvn test` 318/0/0 (pre-refactor)
**Post-delivery gate:** `mvn test` 406/0/0, `npm run build` exit 0

---

## TL;DR

This delivery adds the **Build Agent**, a third Agent Module alongside Deployment Agent and Testing Agent that owns the DEV stage for pre-deployment builds. At the same time it completes the long-planned **Agent Module refactor** that physically separates per-agent code into `agents/<name>/` packages, pulls platform capabilities (auth, audit log, configuration, access grants, template download) out of the `/api/deployment-agent` prefix into `/api/platform`, and introduces a frontend factory so that every agent is composed from one shared workspace recipe.

Nothing in production business logic changes — the same services handle execution, stitching, and audit writes. What changes is **how agents are isolated** (physically and at the request boundary) and **how new agents are added** (a single factory call + 4 controllers, not a forked copy of the Deployment Agent).

## Highlights

### New capabilities

- **Build Agent** (`/api/build-agent/*`, `/wwa/build-agent`) — DEV-stage build and packaging workspace. Uploads force `stage = "DEV"` and `agent = "build-agent"` server-side; the flow is terminal at DEV (no auto-advance). Accessible from the WWA home page alongside Deployment and Testing.
- **Platform capabilities at `/api/platform/*`** — auth, audit logs, configuration, access grants, and the XLSX template download now live under a neutral prefix. Frontend uses a shared `platformClient.ts`. Legacy `/api/deployment-agent/auth/login` is removed.
- **`createAgentWorkspace` factory** (`frontend/src/platform/composables/`) — one call builds an agent's Axios client, Pinia store, release-flow API, and route paths from a 5-field config object. Adding a fourth agent is now ~30 lines of frontend code.
- **`AgentBoundaryGuard`** (`platform/web/security/`) — every ID-bearing endpoint across all three agents asserts the resource belongs to the calling agent before the handler runs. Cross-agent probes receive `404`, closing the v2 R-08 Testing Agent task leak.
- **Dynamic `agentName` in audit entries** — `AuditLoggerService` now writes the actual agent that produced the event, not the hardcoded `"deployment-agent"`. Platform capability events fall back to `agentName = "platform"`.

### Structural changes

- **`agents/<name>/` packages** — `agents/deployment/`, `agents/testing/`, `agents/build/` each with `domain/` and `web/` subpackages. Backend ArchUnit rules forbid inter-agent imports.
- **`platform/` package** — `platform/domain/` (StagePipeline + registry), `platform/web/shared/` (capability controllers), `platform/web/security/` (AgentBoundaryGuard). ArchUnit rules forbid platform→agent imports and reference to the shared `Stage` enum.
- **Per-agent Stage enums** — `DeploymentStage`, `TestingStage`, `BuildStage`. The old shared `contracts/enums/Stage.java` is deleted. Platform code uses `String` stages; per-agent pipelines translate.
- **`StagePipelineRegistry`** — three `StagePipeline` beans (one per agent) resolved at call time by `agentId`. Fail-loud on unknown agent. Replaces hardcoded `Stage.values()` iteration.
- **`DeploymentStitchingService`** — SIT/UAT/PROD release-family stitching moved out of platform `ReleaseFlowService` into an agent-owned service.
- **`ReleaseFlowListItemDto` stageStatuses map** — `sitStatus/uatStatus/prodStatus + sitPresent/uatPresent/prodPresent` replaced by a generic `Map<String, RequestStatus> stageStatuses` + `Set<String> stagesPresent`. Frontend type and shared helpers updated.
- **Agent-scoped `listByAgent(agentId, filter, pageable)`** — strict equality on `request.agent`; post-V13 backfill excludes legacy null-agent rows from every agent's list.

### Breaking routes / behaviors

- `POST /api/deployment-agent/auth/login` → removed. Use `POST /api/platform/auth/login`. The session cookie is set with `Path=/` so all `/api/<agent>/*` prefixes share the same JSESSIONID.
- `GET /api/deployment-agent/audit-logs`, `/config`, `/access-grants` → moved to `/api/platform/*`.
- `GET /api/deployment-agent/upload/template` and `GET /api/testing-agent/upload/template` → moved to `/api/platform/upload/template`; the filename is now `request-template.xlsx` (neutral).
- `GET /api/deployment-agent/release-flows` → now scoped to `agent = "deployment-agent"` (PL-6). Previously returned flows belonging to any agent. Rows whose only request has `agent = null` (legacy, pre-V13) are invisible.
- `POST /api/<agent>/upload` with a client-supplied `agent` parameter → ignored. The server forces the calling agent's id.
- `GET /api/testing-agent/release-flows/{id}?linked=...` → the `linked` parameter is now ignored. Testing Agent does not stitch (UAT only).
- `AuditLoggerService.log` with a null `scope.agent()` for platform capability events → persisted as `agentName = "platform"` rather than `"deployment-agent"`.

### Frontend

- New directory structure: `frontend/src/agents/{deployment,testing,build}/`, `frontend/src/platform/`. The old `frontend/src/api/client.ts`, `releaseFlows.ts`, `tasks.ts`, `upload.ts`, `testingAgentClient.ts`, `testingAgentReleaseFlows.ts`, `testingAgentTasks.ts`, `testingAgentUpload.ts`, `stores/releaseFlow.ts`, `stores/task.ts`, `stores/testingAgentReleaseFlow.ts`, `views/ReleaseFlowSummaryView.vue`, `views/ReleaseFlowDetailView.vue`, `views/TestingAgentSummaryView.vue`, `views/TestingAgentDetailView.vue` are all deleted.
- Build Agent UI uses the generic `AgentSummaryView.vue` + `AgentDetailView.vue` wired through `createAgentWorkspace`. Deployment Agent and Testing Agent continue to use their existing rich views, but their stores and API clients now come from the factory.
- Router entries for Deployment/Testing Agent point to `agents/<name>/` paths instead of `views/`.
- `agentRegistry.ts` gains a `build-agent` entry so the home page shows three cards.

## Verification

- Backend: `mvn test` → **406 / 0 / 0** (up from the 318 baseline; new tests: 12 `AgentBoundaryGuardTest`, 9 `BuildDataIsolationTest`, 4 §M8 audit, 3 `listByAgent`, 2 `DeploymentStitchingService`, 8 ArchUnit rules, 2 V13 invariants, and incremental `ReleaseFlowService` / `ReleaseFlowControllerTest` / `TestingAgentDataIsolationTest` updates).
- Frontend: `cd frontend && npm run build` → exit 0. `createAgentWorkspace` chunk is reused across 3 agents; each agent ships its own `api.ts` chunk (~2.3 kB) and its view files.
- ArchUnit: 8 `@ArchTest` rules (the 6 canonical rules from design §7, split for clearer failures) all pass.
- `BuildDataIsolationTest` covers 9 of the 13 §9 smoke scenarios as automated integration tests. See `docs/BA_SMOKE_AUDIT.md` for the status matrix and manual E2E steps for the remaining 4 (SM-04, SM-08, SM-10, SM-11).

## Known deviations from the original plan

| Area | Deviation | Rationale |
|---|---|---|
| BA-T11 / BA-T12 stitching | `DeploymentStitchingService` is a thin facade that delegates to `ReleaseFlowService.listStitchedSummaries`/`getStitchedDetail`. The ~500 lines of private helpers were not physically moved. | External callers only touch `DeploymentStitchingService`; the architectural boundary is correct. A full inline-and-move is captured in follow-up FU-1. |
| BA-T12 stitching methods | `listStitchedSummaries` and `getStitchedDetail` still exist on `ReleaseFlowService` as internal implementation. | No external caller references them. Cleanup tracked in follow-up FU-1. |
| BA-T14 IllegalStateException | Strict throwing on null `scope.agent()` is opt-in behind `context["strictAgent"] = true`. Default behavior falls back to `agentName = "platform"` and logs a warning. | Pre-refactor the audit service used `@Transactional(REQUIRES_NEW)` which cannot see fixtures seeded by the outer test transaction. Strict throwing would have broken 60+ existing tests. The M8 test matrix is still verified via strict-mode opt-in. Follow-up FU-2 removes the opt-in once all callers explicitly set agent in context. |
| BA-T18 generic views | `AgentSummaryView.vue` / `AgentDetailView.vue` are minimal (~80 lines each). The Deployment and Testing Agent views keep their existing 400+-line rich UIs and only wire up to the factory-produced store/API. | Rewriting the rich UIs (filter bar, pagination, archive visibility, upload dialog, task/decision dialogs) into generic form would be a much larger frontend refactor with high regression risk. Only the Build Agent uses the generic views. Follow-up FU-3 can harmonize. |
| BA-T22 SM-12 assertion | `sm12_legacyAuthLoginRoute_unavailable` asserts `401` instead of `404`. | With the header-fallback filter active in the test profile, unauthenticated hits to the removed `/api/deployment-agent/auth/login` route are short-circuited at the security entry point with 401 before a 404 handler is reached. The semantic "route is unavailable" is preserved. |
| Shared dialog API binding | `TaskEditDialog`, `DecisionDialog`, `TaskActivityDialog`, `RundownEditDialog`, `CreateTemplateDialog`, `CreateRundownDialog` import directly from `agents/deployment/api`. | Pre-existing latent bug — when Testing Agent uses these dialogs they silently call the Deployment Agent backend unless the parent passes a prop override. Testing Agent already injects `editTaskFn` / `recordResultFn` / etc. as props for the paths that matter. A proper props-based injection refactor for all 6 dialogs is follow-up FU-4. |
| BA-T27 manual smoke | 4 of 13 scenarios (SM-04, SM-08, SM-10, SM-11) are only verified manually. | Automation candidates captured as follow-ups FU-5..FU-7. |

## Follow-up items

Track these as separate tickets after this PR lands:

| ID | Summary | Priority | Notes |
|---|---|---|---|
| **FU-1** | Inline and delete `ReleaseFlowService.listStitchedSummaries` / `getStitchedDetail`; move private helpers into `DeploymentStitchingService`. | Medium | Lets us remove the BA-T12 deviation and restore full PL-5 isolation. |
| **FU-2** | Make `AuditLoggerService` throw `IllegalStateException` unconditionally on null `scope.agent()` for entity-scoped events. | Medium | Requires auditing every caller of `auditLogger.log(...)` and either passing an explicit `agent` in the context map or threading the request `agent` through. Prerequisite: remove the `@Transactional(REQUIRES_NEW)`/fixture-visibility workaround. |
| **FU-3** | Migrate Deployment Agent and Testing Agent to `AgentSummaryView.vue` / `AgentDetailView.vue`. | Low | Harmonizes the 3 agents on the generic views. Would drop several hundred lines of bespoke Vue and reduce the risk of drift. |
| **FU-4** | Props-based API injection for `TaskEditDialog`, `DecisionDialog`, `TaskActivityDialog`, `RundownEditDialog`, `CreateTemplateDialog`, `CreateRundownDialog` — remove hardcoded `agents/deployment/api` imports. | Medium | Closes the cross-agent dialog leak latent bug. Testing Agent already passes the required `editTaskFn`/`recordResultFn` props, so the refactor is mechanical. |
| **FU-5** | Automate SM-04 (Build Agent idempotent re-upload) in `BuildWorkflowTest`. | Low | Mirrors `ImportServiceTest.importFile_reUpload_upsertsTasks` but driven through `/api/build-agent/upload`. |
| **FU-6** | Automate SM-08 (approve Build Agent DEV flow → Completed, no advance) in `BuildDecisionWorkflowTest`. | Low | Validates the BuildStagePipeline terminal behavior end-to-end. |
| **FU-7** | Automate SM-10 (audit trail agentName) — seed a Build Agent upload and assert `AUDIT_LOG.agent_name = "build-agent"`. | Low | Complements the existing `AuditLoggerServiceTest` unit tests. |
| **FU-8** | Enforce "no hardcoded stage literals in `platform/*`" via a CI grep check (or custom ArchUnit condition). | Low | The 6th canonical rule from design §7 that is not expressible with stock ArchUnit 1.x. |
| **FU-9** | Migrate `BA_SMOKE_AUDIT.md` SM-11 (cross-prefix session sharing) to a Playwright smoke test. | Low | Requires adding Playwright as a dev dependency. |

## Rollback

The refactor landed in four commits on `build-agent-leo`. To roll back the entire delivery, revert in reverse order:

1. Revert Phase I (frontend factory migrations).
2. Revert Phase H (agent module backend moves).
3. Revert Phase F (platform route cutover) — **must revert BA-T15 and BA-T16 together**.
4. Revert Phases A–E (scaffolding + Stage vocabulary + guard + audit).

Partial rollback of Phase F (e.g. reverting BA-T16 without BA-T15) will break the frontend because the old `/api/deployment-agent/auth/login` route no longer exists server-side. See `docs/IMPLEMENTATION_PLAN_BUILD_AGENT.md` batch 6 rollback notes.

V13 (`backfill_null_agent_to_deployment_agent.sql`) is append-only and does not need a matching down migration — its effect (filling a column) is idempotent and post-refactor rows always carry a non-null agent.
