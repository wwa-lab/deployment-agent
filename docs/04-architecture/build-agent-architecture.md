# System Architecture: Build Agent

**Date:** 2026-04-10
**Status:** Draft (v2, post-review)
**Source:** build-agent-spec.md (primary), testing-agent-architecture.md (baseline), architecture.md (platform baseline)

---

## Platform Context

Build Agent is the **third workspace** under the **WWA Agent Workspace Hub**. The operating model remains:

```
FinBlock  →  WWA Agent Workspace Hub (`WWA`)  →  Agent Workspaces
                                                   ├── Deployment Agent (first workspace, SIT/UAT/PROD)
                                                   ├── Testing Agent    (second workspace, UAT)
                                                   └── Build Agent      (third workspace, DEV)
```

- **WWA Agent Workspace Hub** continues to own authentication, top-level navigation, platform access management, and platform-level audit.
- **Build Agent** reuses the same domain model, services, and shared capabilities as Deployment Agent and Testing Agent.
- **SDLC coverage:** Build Agent owns the **DEV** stage, placing the three agents in a clean `DEV → SIT → UAT → PROD` chain with zero stage overlap between agents.
- **Data isolation** is achieved at the controller layer via the existing `Request.agent` column **plus** a new `AgentBoundaryGuard` on task mutations and the Build Agent detail endpoint.

---

## Terminology (Stitched Flow Model)

To avoid the kind of confusion found in v1 of this document, the following terms are used precisely throughout:

- **Persisted Release Flow** — a single row in the `da_release_flow` table. It is created by exactly one upload, belongs to exactly one agent (its linked requests all carry the same `agent` value under Build Agent rules), and has its own `currentStage`. This is the unit the repository and progression service operate on.
- **Release Flow Family** — the logical grouping of one or more Persisted Release Flows that share the same normalized family key computed by `ReleaseFlowFamilyKey`. A family is **not** a database entity; it is an in-memory concept computed at query time.
- **Stitched Summary Row** — one row in the summary response produced by `ReleaseFlowService.listStitchedSummaries` (ref: `ReleaseFlowService.java:172-221`). It groups Persisted Release Flows by family key, picks a **representative flow** via `representativeFlow()`, concatenates the requests from all grouped flows at the DTO level, and emits a single `ReleaseFlowListItemDto`. Because `listStitchedSummaries` pre-filters base flows by the supplied `agent` parameter, a Stitched Summary Row never spans multiple agents in practice — see §Stitched Summary Behavior with DEV.
- **Stitched Detail View** — the response produced by `ReleaseFlowService.getStitchedDetail(releaseFlowId, linkedFlowIds, ...)` (ref: `ReleaseFlowService.java:241`). It loads the given primary flow plus the listed linked flows, concatenates their requests, picks a representative flow, and emits a single `ReleaseFlowDetailDto`. This is only invoked when the caller supplies a `?linked=<ids>` query parameter.

**Critical clarification:** A single Persisted Release Flow never contains requests from multiple agents. Cross-agent "sharing" happens only at the stitched (in-memory) layer, when multiple persisted flows share a family key.

---

## Overview

Build Agent mirrors the Testing Agent workspace pattern (thin controller delegation + parameterized frontend) but with **four surgical shared-contract changes** that the earlier agents did not require:

1. `Stage` enum adds `DEV` and replaces `next()` ordinal math with an explicit switch (keeps `DEV` terminal)
2. `ReleaseFlowFamilyKey` learns to recognize `dev` as a stage token so cross-agent family grouping works
3. `ReleaseFlowListItemDto` gets two additive fields (`devStatus`, `devPresent`)
4. A new `AgentId.BUILD_AGENT` constant plus a new `AgentBoundaryGuard` component for controller-layer enforcement

Unlike Testing Agent — which achieved isolation with zero domain layer changes — Build Agent must open the shared contract layer just far enough to make `DEV` a first-class stage. Every change is additive; no existing enum value, DTO field, regex, or service signature is removed or renamed.

**Architectural approach:** Thin controller delegation + controller-layer agent boundary guard + four additive shared-contract changes.

**Key architectural decision:** Build Agent does not introduce a new service layer, repository layer, or entity layer. All business logic in `TaskService`, `ReleaseFlowService`, `DecisionEngine`, `ReleaseFlowProgressionService`, `RecordResultService`, `AutoExecutionService`, and `ImportService` is reused without modification.

---

## Technology Stack

No changes. Same stack as Deployment Agent and Testing Agent:

| Layer | Technology |
|-------|-----------|
| Frontend | Vue 3 (Composition API) · Vite 5 · Pinia · Vue Router 4 · Axios |
| Backend | Java 21 · Spring Boot 3.2.0 · Spring MVC · Spring Data JPA · Spring Security |
| Database | Oracle (production) · H2 in-memory (tests) |
| Build | Maven 3 (backend) · npm (frontend) |
| Auth | Session-based login via authentication-provider abstraction |

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Users                                                                       │
│  Developer · Tech Lead · DevOps Admin · Audit / Management                   │
└──────────────────────┬───────────────────────────────────────────────────────┘
                       │ HTTPS
                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Web App (Vue 3 SPA)                                                         │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐               │
│  │ Deployment      │  │ Testing          │  │ Build Agent      │ ← NEW       │
│  │ Agent           │  │ Agent            │  │ (DEV only,       │             │
│  │ (SIT/UAT/PROD,  │  │ (UAT,            │  │  no stitched     │             │
│  │  stitched view) │  │  stitched view)  │  │  detail)         │             │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘               │
│           │                    │                    │                        │
│  ┌────────▼────────────────────▼────────────────────▼────────┐               │
│  │  Shared Components                                         │               │
│  │  UploadDialog (:allowed-stages) · RecordResultDialog · ... │               │
│  └────────────────────────────────────────────────────────────┘               │
└──────────────────────┬───────────────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  API Service (Spring Boot 3)                                                 │
│                                                                              │
│  ┌────────────────────────┐ ┌────────────────────────┐ ┌────────────────────┐│
│  │  /api/deployment-      │ │  /api/testing-         │ │  /api/build-agent/ ││
│  │  agent/                │ │  agent/                │ │  ← NEW              ││
│  │  (stitched detail via  │ │  (stitched detail via  │ │  (single-flow only; ││
│  │   ?linked)             │ │   ?linked)             │ │   linked rejected)  ││
│  └────────────┬───────────┘ └────────────┬───────────┘ └──────────┬──────────┘│
│               │                          │                        │          │
│               │                          │                  ┌─────▼─────┐    │
│               │                          │                  │ Agent     │    │
│               │                          │                  │ Boundary  │    │
│               │                          │                  │ Guard     │    │
│               │                          │                  └─────┬─────┘    │
│               └──────────────┬───────────┴────────────────────────┘          │
│                              ▼                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │  Shared Domain Services (BEHAVIOR UNCHANGED)                          │   │
│  │                                                                       │   │
│  │  ReleaseFlowService (incl. listStitchedSummaries, getStitchedDetail,  │   │
│  │                       getById, findRequestsForFlow)                   │   │
│  │  TaskService · ImportService · DecisionEngine                         │   │
│  │  ReleaseFlowProgressionService (progressAfterDecision)                │   │
│  │  RecordResultService · AutoExecutionService                           │   │
│  │  TaskStateMachine · ReleaseFlowAggregation                            │   │
│  │  AuditLoggerService · ConfigurationService · AuthService              │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                              │                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │  Shared Contracts (SURGICAL ADDITIVE CHANGES)                         │   │
│  │                                                                       │   │
│  │  Stage                 ← add DEV, rewrite next() as switch            │   │
│  │  ReleaseFlowFamilyKey  ← add 'dev' to stage token recognition         │   │
│  │  ReleaseFlowListItemDto← add devStatus / devPresent fields            │   │
│  │  AgentId               ← add BUILD_AGENT constant                     │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                              │                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │  Shared Persistence (UNCHANGED)                                       │   │
│  │  Spring Data JPA · All existing repositories                          │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
└──────────────┬──────────────────────┬────────────────────────────────────────┘
               │                      │ REST (fire-and-forget)
               ▼                      ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌────────────────────────┐
│  Oracle DB           │  │  Jenkins              │  │  Auth Provider         │
│  (UNCHANGED)         │  │  + Ansible Tower      │  │  (UNCHANGED)           │
│                      │  │  (UNCHANGED)          │  │                        │
│  Same entities       │  │                       │  │                        │
│  No new tables       │  │                       │  │                        │
│  No migration        │  │                       │  │                        │
└──────────────────────┘  └───────────────────────┘  └────────────────────────┘
```

---

## Architecture Decisions

### AD-1: Thin Controller Delegation

**Decision:** Build Agent controllers delegate to existing domain services. No new service classes are created.

**Rationale:** Business logic (import, task state machine, decisions, progression) is agent-agnostic. The `agent` column is already a query parameter in `ReleaseFlowService`. Duplicating services would create maintenance burden and divergence risk.

**Consequences:** Build Agent controllers are thin wrappers. Any domain logic fix applies to all three agents automatically. Build Agent gains the additional responsibility of invoking `AgentBoundaryGuard` before delegating task mutations and single-flow reads.

### AD-2: Surgical Shared-Contract Changes (Not a Parallel Domain Model)

**Decision:** Add `DEV` to `Stage`, extend `ReleaseFlowFamilyKey`, and add `devStatus`/`devPresent` to `ReleaseFlowListItemDto`. Do not create a parallel "build domain model" or a new `BuildStage` enum.

**Alternatives considered:**
- **Parallel `BuildStage` enum** — rejected. Would duplicate all stage-aware logic in `ReleaseFlowService`, `ReleaseFlowProgressionService`, aggregation, and DTOs. Massive divergence risk
- **Store Build Agent stage as null** — rejected. `Stage` is non-nullable across JPA, aggregation, and DTO population; would break existing assumptions throughout the codebase
- **Extend `Stage` but leave `next()` ordinal math** — rejected. `DEV.next()` would auto-promote to `SIT`, crossing the agent boundary on flow completion (see spec R-01)

**Rationale:** `DEV` is conceptually another stage in the same SDLC chain, just earlier. Making it a first-class `Stage` value keeps stage-aware queries and aggregations working without branching. The explicit `next()` switch makes the terminal-stage contract auditable.

**Consequences:** Four files change in the shared contract layer. `ReleaseFlowProgressionService.progressAfterDecision` does not need to change — its existing terminal-stage branch (`currentStage.next() == null`) handles DEV completion the same way it handles PROD completion today. Deployment Agent and Testing Agent summary views continue to read only their own stage columns.

### AD-3: `DEV` is Terminal (`Stage.DEV.next() == null`)

**Decision:** `Stage.DEV.next()` returns `null`. A Build Agent Persisted Release Flow completing its last task is marked `Completed`; it does NOT auto-advance into `SIT`.

**Rationale:**
- Build Agent owns only the DEV scope per BA-01 product boundary
- Auto-advancing DEV → SIT would cross the agent boundary and pull a Build Agent flow into Deployment Agent's territory
- Cross-agent transitions, if ever needed, are a separate product feature (probably a manual "Promote to SIT" action), not implicit in the Stage chain

**Consequences:** `ReleaseFlowProgressionService.progressAfterDecision` already treats `currentStage.next() == null` as terminal — zero change to the progression service. `Stage.next()` semantics are no longer `ordinal()+1`, so the existing `ordinal()`-based implementation must be replaced by an explicit switch.

### AD-4: Controller-Layer Agent Boundary Guard

**Decision:** Introduce a new component `AgentBoundaryGuard` invoked by Build Agent controllers on every endpoint that loads a task or Persisted Release Flow by ID. On mismatch, the guard produces an HTTP 404 (not 403) to avoid leaking task IDs across namespaces.

**Responsibilities:**
- `assertTaskBelongsToAgent(taskId, expectedAgent)` — loads the task, navigates to its parent request, rejects if `request.agent != expectedAgent`
- `assertRequestBelongsToAgent(requestId, expectedAgent)` — loads the request directly, rejects if `request.agent != expectedAgent` (used by the `GET /tasks?requestId=X` endpoint where no task id is available)
- `assertFlowBelongsToAgent(flowId, expectedAgent)` — verifies the Persisted Release Flow exists via `releaseFlowRepository.findById(flowId)` and checks agent membership via `requestRepository.findByReleaseFlowIds(List.of(flowId), true)`, rejecting if no request carries the expected agent

**Alternatives considered:**
- **Push the check into domain services** with an `expectedAgent` parameter — rejected. Would require changing every call site including Deployment Agent and Testing Agent; larger blast radius; breaks the existing test suite
- **Spring Security expression (`@PreAuthorize`)** — rejected. The existing codebase does not use `@PreAuthorize`; authorization is performed imperatively inside controller methods via helpers such as `validateRequestScope`, `validateRundownOperator`, and `validateAdmin`. Introducing `@PreAuthorize` for Build Agent alone would fragment the authorization style
- **Skip the check for MVP (match Testing Agent's pre-existing gap)** — rejected. The spec's data isolation claim becomes a lie

**Rationale:**
- Consistent with the existing imperative-validation style used throughout Deployment Agent and Testing Agent controllers
- Keeps domain services clean and agent-agnostic
- 404 response avoids information leak about task IDs in other namespaces
- Can be back-ported to Testing Agent in a follow-up (R-08)

**Consequences:** Each Build Agent controller method that accepts a task or flow ID calls the guard before delegating. Detail responsibilities vs. exception types are the design document's job.

### AD-5: Separate API Prefix `/api/build-agent/` (inherited pattern)

**Decision:** Build Agent uses `/api/build-agent/` as its API prefix. Same rationale as Testing Agent's AD-3 (clear namespace separation, independent evolution, simpler auditing).

### AD-6: Separate Frontend Store Instance (inherited pattern)

**Decision:** Build Agent uses a dedicated Pinia store separate from the deployment and testing agent stores. Prevents state collision when navigating between agents.

### AD-7: Shared Access Model (inherited pattern)

**Decision:** Access grants are shared across all three agents. An active grant allows access to Deployment, Testing, and Build Agent workspaces. No `agent` dimension is added to `AccessScope`.

### AD-8: Agent Identity Constants (inherited pattern)

**Decision:** Agent identity lives in the existing `AgentId` constants class (backend) and a mirror constants module (frontend). Build Agent adds its constants to both layers. No string literals allowed in controllers, service calls, or view configuration.

### AD-9: `ReleaseFlowListItemDto` Fields are Additive, Consumption is Explicit

**Decision:** `devStatus` and `devPresent` are added to the DTO. Deployment Agent and Testing Agent summary view renderers MUST continue to read only their own stage columns and MUST NOT render the new fields.

**Rationale:**
- The DTO is a shared contract; fragmenting it per agent would create type drift
- Frontend rendering is the natural place to decide which stage columns to display
- Regression: snapshot tests on Deployment/Testing summary views must continue to show their original stage columns unchanged

**Consequences:** A future shared `AgentSummaryView` refactor must take the set of visible stage columns as a prop rather than hardcoding all columns.

### AD-11: Dynamic `agentName` in `AuditLoggerService`

**Decision:** `AuditLoggerService.log` derives `agentName` from `scope.agent()` rather than the current hardcoded `"deployment-agent"` literal. When `scope.agent()` is null (legacy data), fall back to `"deployment-agent"`.

**Context:** The current implementation at `AuditLoggerService.java:61` hardcodes `entry.setAgentName("deployment-agent")`, which means every audit entry from every agent is currently tagged as Deployment Agent regardless of the actual workspace that produced it. This is a pre-existing defect affecting Testing Agent today.

**Rationale:**
- Minimal diff: one line change inside the existing `log` method
- Does not require changing any caller signature, so Build Agent, Testing Agent, and Deployment Agent controllers all benefit automatically
- `scope.agent()` is already populated correctly from the request scope, making it the natural source of truth
- Legacy rows (pre-agent-column data) continue to write `"deployment-agent"` so historical behavior is preserved

**Alternatives considered:**
- **Add `String agentName` parameter to `log()`** — rejected. Ripples through every call site across three agents
- **ThreadLocal / RequestContext** — rejected. Introduces global state
- **Map-based lookup from `source_system`** — rejected. Adds indirection for no benefit

**Consequences:**
- Historical Testing Agent audit entries in production remain incorrectly tagged as `"deployment-agent"`; only new entries are corrected. This is a forward-only fix (R-12)
- Build Agent ships with correct audit tagging from day one
- Testing Agent's existing pre-existing defect is also repaired as a side effect

### AD-12: Deployment Agent Summary Remains Global

**Decision:** Deployment Agent summary continues to show all Persisted Release Flows regardless of `agent` value, including build-only and testing-only flows. Build-only flows will render with empty `SIT`/`UAT`/`PROD` columns in the Deployment Agent UI.

**Rationale:**
- Matches existing `ReleaseFlowController.list` behavior — today, the `agent` query parameter is optional and never forced
- Matches the existing practical fact that Testing Agent flows already appear in Deployment Agent summary today (nobody has complained because TA deployments are sparse)
- Avoids a shared-service refactor that would ripple into Testing Agent, regression tests, and Deployment Agent access patterns
- Build Agent is a **scoped view** on top of a **global Deployment Agent view**. The mental model is: Deployment Agent = everything; Testing / Build Agent = slice

**Alternatives considered:**
- **Force `agent IN ('deployment-agent', null)` in Deployment Agent controller** — rejected. Changes Deployment Agent's product semantics and affects Testing Agent visibility too
- **Add `excludeAgents` query parameter** — rejected. Solves nothing; the Deployment Agent frontend would need to always pass it

**Consequences:**
- Spec BFR-19 is scoped to **rendering** only (Deployment Agent must not render `devStatus`/`devPresent` columns), not **visibility** (the flows still appear as rows)
- New risk R-13 tracks the UX observation that Deployment Agent users will see "empty" rows for build-only flows
- Test expectations in the Build Agent design and spec that asserted "Deployment Agent summary does NOT show build-only flows" are removed

### AD-10: Build Agent Detail Does Not Support Stitched Linked View (MVP Scope)

**Decision:** Build Agent detail accepts only a single `flowId`. The `?linked=<ids>` query parameter — which in Deployment Agent and Testing Agent triggers `ReleaseFlowService.getStitchedDetail` — is **not supported** in Build Agent for MVP. If a client supplies `?linked=`, the Build Agent detail controller ignores the parameter and returns only the single Persisted Release Flow.

**Alternatives considered:**
- **Full stitched support with per-linked-flow boundary validation** — rejected for MVP. Would require `AgentBoundaryGuard` to validate each linked flow individually, plus a new service method (or parameter) that filters the stitched request set down to build-agent requests only. Larger scope, more edge cases, and the stitched view of DEV+SIT+UAT+PROD is naturally better rendered from the Deployment Agent side
- **Pass-through stitched detail with frontend filtering** — rejected. Would return other agents' request data to the Build Agent client, violating the data isolation promise

**Rationale:**
- Build Agent's product scope is the DEV phase only; its detail view is naturally single-flow
- Users who need to see how a DEV flow relates to its downstream SIT/UAT/PROD family can switch to Deployment Agent, whose existing stitched detail already handles the full chain
- Keeps the MVP boundary guard simple: `assertFlowBelongsToAgent(flowId, BUILD_AGENT)` is one lookup

**Consequences:**
- Build Agent frontend detail view does not read `route.query.linked`
- If the query parameter is present on a Build Agent URL, it is silently ignored (or returns 400 — design layer decides)
- Any future "see the DEV→SIT→UAT→PROD family from the DEV side" feature is an explicit follow-up and must revisit this decision
- R-11 tracks this as a known UX trade-off (users cannot see downstream stages from within Build Agent)

---

## Component Architecture

### Backend

**New components**
- `AgentBoundaryGuard` — shared component that validates a given task ID or flow ID belongs to the expected agent, used by all Build Agent controllers that accept a task or flow ID
- `BuildAgentReleaseFlowController` — thin wrapper for list and single-flow detail; rejects or ignores `?linked=` per AD-10
- `BuildAgentUploadController` — thin wrapper; forces `agent = "build-agent"` and `stage = "DEV"` server-side
- `BuildAgentTaskController` — thin wrapper for the task read and mutation endpoints; calls the guard before every delegation
- `BuildAgentDecisionController` — thin wrapper for the decision endpoint; calls the guard before delegating to `DecisionEngine` and `ReleaseFlowProgressionService.progressAfterDecision`

**Modified shared contracts**
- `Stage` — add `DEV`; rewrite `next()` from ordinal math to an explicit switch
- `ReleaseFlowFamilyKey` — conservative extension to recognize `dev` only when followed by digits or as an infix token (does not add `dev` to the aggressive separator regex; see §Conservative DEV Stripping)
- `ReleaseFlowListItemDto` — **append** `devStatus` and `devPresent` fields, populated in all existing positional constructor call sites
- `AgentId` — add `BUILD_AGENT` constant

**Modified shared services**
- `AuditLoggerService` — one-line change to derive `agentName` from `scope.agent()` rather than the current hardcoded `"deployment-agent"` literal. Side effect: Testing Agent audit entries begin producing `agentName = "testing-agent"` going forward, correcting a pre-existing defect. See AD-11 and R-12

**Unchanged**
- All domain services (`ReleaseFlowService`, `TaskService`, `ImportService`, `DecisionEngine`, `ReleaseFlowProgressionService`, `RecordResultService`, `AutoExecutionService`, `TaskExecutionHistoryService`)
- Pure functions (`TaskStateMachine`, `ReleaseFlowAggregation`)
- All Spring Data JPA repositories
- All JPA entities
- Security filters (`SessionAuthFilter`, `HeaderAuthFilter`) and Spring Security configuration
- All existing Deployment Agent and Testing Agent controllers
- All shared capability controllers (Auth, Configuration, Audit Log, Access Grant)

### Frontend

**New components**
- A Build Agent Axios instance pointing at `/api/build-agent`
- Build Agent API modules for release flows, upload, tasks, and decisions
- A Build Agent Pinia store, structurally parallel to the existing per-agent stores
- `BuildAgentSummaryView` — single `DEV` stage column; passes `:allowed-stages="['DEV']"` to `UploadDialog`
- `BuildAgentDetailView` — single `DEV` stage tab; does not read `route.query.linked`

**Modified components**
- `agentRegistry.ts` — extend the `AgentCategory` type with `'build'`; register the Build Agent entry with its description, route, and icon
- Vue Router configuration — add the two Build Agent routes

**Unchanged**
- All existing Deployment Agent and Testing Agent views and stores
- `UploadDialog` (already accepts `:allowed-stages`)
- All shared dialogs and components

---

## Data Architecture

### Schema Changes

No new tables. No new columns. No Flyway migration.

The `Request.agent` column already exists. The `stage` column stores the enum constant name as a string; adding `DEV` to the Java enum means the database will start accepting `'DEV'` as a legal value from the next Build Agent upload onward. Both H2 and Oracle store enum values as VARCHAR without enumerated-type constraints in the schema.

### Stage Value Compatibility

| Stage value | Status | Used by |
|---|---|---|
| `SIT` | Existing | Deployment Agent |
| `UAT` | Existing | Deployment Agent, Testing Agent |
| `PROD` | Existing | Deployment Agent |
| `DEV` | **New** (no schema change) | Build Agent |

Adding `DEV` affects only rows created by Build Agent uploads. Existing rows with `stage IN ('SIT','UAT','PROD')` remain valid and unchanged.

### Agent Column Usage

| Value | Meaning |
|---|---|
| `"deployment-agent"` | Request created through Deployment Agent |
| `"testing-agent"` | Request created through Testing Agent |
| `"build-agent"` | **New** — Request created through Build Agent |
| `null` | Legacy data (pre-agent-column); visible only in Deployment Agent |

### Stitched Summary Behavior with DEV (Within-Agent Only)

`ReleaseFlowService.listStitchedSummaries` pre-filters base flows by the supplied `agent` parameter before grouping them into families (ref: `ReleaseFlowService.java:183-199`). Because Build Agent's controller forces `effectiveAgent = BUILD_AGENT` server-side, the stitched grouping **only sees build-agent Persisted Release Flows** — it can never fold a Deployment Agent SIT/UAT/PROD flow into a Build Agent summary row, even when both share the same family key.

Consequently:

- **Within-agent stitching works** — two Build Agent uploads of `DEV-1234` (e.g. reuploading after a failed import) produce a single stitched row in Build Agent summary
- **Cross-agent stitching does NOT happen** at the summary layer, by design for MVP
- **`linkedReleaseFlowIds`** on a Build Agent summary row never contain Deployment Agent or Testing Agent flow IDs
- A user who wants to see the full DEV → SIT → UAT → PROD family for a release must switch to Deployment Agent (whose existing stitched summary already covers SIT/UAT/PROD; it does not cover DEV either, by the same pre-filter mechanism — this is tracked as R-14)

**Why the `ReleaseFlowFamilyKey` DEV extension is still needed:** Within-agent stitching for duplicate DEV uploads, and the conservative stripping behavior described below.

### Conservative DEV Stripping

`ReleaseFlowFamilyKey` is extended to recognize `dev` as a stage token only in the narrow cases where the `dev` prefix is unambiguously a stage identifier:

1. `dev` followed by digits, no separator (e.g. `dev1234`) — recognized via `STAGE_PREFIX_WITH_DIGITS`
2. `dev` + separator + digits (e.g. `DEV-1234`) — recognized via a **new** `DEV_PREFIX_WITH_DIGIT_SEPARATOR` pattern
3. `dev` as an infix token between other tokens (e.g. `HCC-DEV-AMH-1234`) — recognized via `isStageToken` used inside `stripInfixStageToken`

Critically, `dev` is **not** added to the existing aggressive `STAGE_PREFIX_WITH_SEPARATOR` regex that strips `sit|uat|prod` before arbitrary non-digit remainders. That aggressive pattern would turn legitimate project identifiers like `dev-tools`, `dev-kit`, or `dev-portal` into `tools` / `kit` / `portal` and collide with real projects of those names. `dev` is a much more common project-name prefix than `sit`/`uat`/`prod`, so the asymmetry is intentional.

### Stitched Detail Behavior with DEV

Build Agent does not use `getStitchedDetail`. See AD-10.

Deployment Agent and Testing Agent continue to use `getStitchedDetail` exactly as today. Their pre-filter behavior means they also cannot surface Build Agent DEV flows in their own stitched detail responses, unless a client explicitly constructs a linked flow ID list that includes Build Agent flow IDs — but since the existing Deployment Agent stitched detail is gated by user-visible flow lookup, not by cross-agent traversal, this is not a practical path.

---

## Integration Architecture

No changes. Build Agent reuses the same integration points:

- **Jenkins** and **Ansible Tower** — same fire-and-forget submission pattern
- **Authentication Provider** — same session-based login
- **Access Grant Resolution** — same deny-by-default lookup
- **Audit Storage** — same `REQUIRES_NEW` propagation

---

## API Boundaries

### New Endpoint Surfaces

Build Agent exposes the same endpoint shape as Deployment Agent's existing routes (list flows, get flow by id, upload, download template, list/get tasks, edit task input, fetch execution history, start manual, record result, submit auto, apply decision). The full endpoint path list and parameter contracts live in the Build Agent design document; they mirror the actual `TaskController`, `DecisionController`, `UploadController`, and `ReleaseFlowController` routes rather than any earlier speculative shape.

**Build Agent-specific behavior at the controller boundary:**
- Every controller forces `agent = "build-agent"` server-side and ignores any client-supplied agent value
- The upload controller additionally forces `stage = "DEV"` server-side
- Every task-id and flow-id-bearing endpoint invokes `AgentBoundaryGuard` before delegating
- The detail endpoint does not honor `?linked=` per AD-10

### Existing Endpoints Unchanged

All `/api/deployment-agent/`, `/api/testing-agent/`, and shared capability endpoints remain unchanged.

---

## Security Architecture

### Additions

Build Agent introduces **one** new security primitive: `AgentBoundaryGuard`. It is orthogonal to the existing filter chain and runs inside the controller method, not in the Spring Security filter pipeline.

**Why not a Spring Security filter?** Filters run before the controller and do not cleanly have entity-level access (they would have to duplicate repository lookups and lazy loading). A controller-layer guard is simpler and lives at the same layer as the business operation it protects.

**Why not `@PreAuthorize`?** The existing codebase does not use `@PreAuthorize` anywhere; all authorization is imperative and happens inside the controller methods via helpers such as `validateRequestScope`, `validateRundownOperator`, and `validateAdmin`. Build Agent follows the same imperative style for consistency.

### Otherwise Unchanged

- Session management (`IF_REQUIRED` session policy)
- Filter chain (`SessionAuthFilter` → `HeaderAuthFilter` → Spring Security)
- Access Grant model (deny-by-default)
- Scope grants (`Application + SNOW Group`)
- RBAC / permission enforcement
- Optimistic locking via `@Version`
- Audit isolation via `REQUIRES_NEW` propagation

Build Agent controllers perform the same imperative role and scope validation as Deployment/Testing Agent controllers, plus the `AgentBoundaryGuard` invocation.

---

## Performance and Resilience Impact

Build Agent introduces no new read paths with asymptotically different cost from Deployment Agent or Testing Agent, but adds two small constant-factor overheads that should be explicitly accounted for.

### Read Path Costs

- **Summary list (`GET /api/build-agent/release-flows`)** — reuses `ReleaseFlowService.listStitchedSummaries` with `effectiveAgent = "build-agent"`. Same query shape and pagination model as the other two agents. The additional `devStatus`/`devPresent` DTO population is O(requests-per-flow) and piggybacks on the existing per-flow iteration. No extra queries.
- **Detail (`GET /api/build-agent/release-flows/{id}`)** — mirrors the non-linked path used by `ReleaseFlowController` and `TestingAgentReleaseFlowController`: `releaseFlowService.getById(id, includeArchived)` to load the flow, then `releaseFlowService.findRequestsForFlow(id, includeArchived)` to load visible requests, then the standard DTO assembly. Build Agent never calls `getStitchedDetail` per AD-10, so it avoids the multi-flow fan-out cost entirely.
- **Task read endpoints** — identical to existing Deployment Agent behavior.

### `AgentBoundaryGuard` Overhead

The guard performs one extra repository lookup per protected endpoint (task-level, request-level, or flow-level). For task mutation endpoints, this overlaps with the domain service's own task load (e.g. `TaskService.editInput` loads the task again). Both loads happen within the same `@Transactional` scope, so the second load is expected to hit the Hibernate first-level cache rather than trigger a second database round trip. The effective marginal cost is one entity-manager lookup plus the reference navigation to `task.request.agent`.

For flow-level detail, the guard performs an independent `releaseFlowRepository.findById` + `requestRepository.findByReleaseFlowIds` lookup before the controller proceeds to `getById` / `findRequestsForFlow`. All of these share the same transaction, so repeated entity fetches are absorbed by the L1 cache. The guard intentionally does not share load state with the subsequent handler — keeping it self-contained makes it safe to invoke from any endpoint.

### Write Path

Unchanged. Build Agent writes delegate to the same services as Deployment and Testing Agent. No new transaction boundaries, no new lock contention.

### Observability

Recommended additions (tracked at the design layer, not mandated here):
- A counter for `AgentBoundaryGuard` rejections (by agent and endpoint) to detect cross-agent probing
- Continued use of the existing operational logging in domain services

### Baseline Capacity

No change to expected baseline capacity. The system's hot paths (decision application, task state transitions, summary list rendering) are untouched. The only hot-path change is that `Stage.values()` iterations in `ReleaseFlowService.aggregateFlowStatus` and `latestRequestsPerStage` iterate over four values instead of three, which is not meaningfully different.

---

## Constraints and Assumptions

| # | Constraint | Source |
|---|-----------|--------|
| C1 | Build Agent reuses the same data model — no new entities, no new tables, no Flyway migration | AD-2 |
| C2 | Build Agent controllers are thin wrappers — no domain logic duplication | AD-1 |
| C3 | Agent identity is stored as `Request.agent = "build-agent"` | Spec BFR-09 |
| C4 | Build Agent upload always forces `stage = "DEV"` and `agent = "build-agent"` server-side | Spec BFR-14 |
| C5 | `DEV` is a terminal stage — `Stage.DEV.next() == null` | AD-3 |
| C6 | Task mutations enforce agent boundary at the controller layer | AD-4 |
| C7 | Boundary violations return HTTP 404, not 403 | AD-4 |
| C8 | Build Agent detail does not support the `?linked=` stitched view | AD-10 |
| C9 | Legacy data (null agent) is NOT visible in Build Agent | Spec BFR-18 |
| C10 | Access grants are shared across agents | AD-7 |
| C11 | All existing Deployment Agent and Testing Agent behavior must remain unchanged | Spec §14.2 |
| C12 | Agent identity strings are defined as constants at backend and frontend | AD-8 |
| C13 | `devStatus`/`devPresent` DTO fields are ignored by Deployment/Testing summary renderers | AD-9 |
| C14 | Authorization style is imperative validation inside controller methods, consistent with the existing codebase (no `@PreAuthorize`) | AD-4 |

---

## Impact Analysis

### Components to Create
- One controller-layer agent boundary guard
- Four Build Agent controllers (release-flow, upload, task, decision)
- One Build Agent frontend API module set (client, release-flows, upload, tasks)
- One Build Agent Pinia store
- Two Build Agent views (summary and detail)

### Components to Modify
- `Stage` (add `DEV`, rewrite `next()`)
- `ReleaseFlowFamilyKey` (extend stage token recognition)
- `ReleaseFlowListItemDto` (additive `devStatus`/`devPresent`)
- `AgentId` (add `BUILD_AGENT`)
- Frontend agent registry (add entry; extend `AgentCategory`)
- Frontend router (add two routes)

### Components Unchanged
All domain services, repositories, entities, security filters, progression service, existing Deployment/Testing Agent controllers, shared capability controllers, database schema, configuration, Jenkins/Ansible adapters, and all existing Deployment/Testing frontend views and stores.

Design-layer artifacts (file paths, class signatures, test matrix, LOC estimates) live in the Build Agent design document, not here.

---

## Pending External Dependencies

No new external dependencies. All existing dependencies from Deployment Agent apply unchanged:

1. Team Book adapter contract (pending, not Build Agent specific)
2. Jenkins/Ansible credentials (runtime config)
3. Enterprise directory enrichment (pending consideration)

---

## Open Architecture Risks Carried Forward

Tracked in spec §15 unless otherwise noted:

- **R-01** — Full `mvn test` gate required after `Stage.next()` rewrite
- **R-02** — `Stage.values()` iterations must tolerate the new enum slot (additive-safe; filter-based iterations skip empty buckets)
- **R-03** — View duplication across three agents (follow-up refactor)
- **R-06** — Regression: Deployment/Testing stage dropdowns must not include `DEV`
- **R-07** — Family key regex edge case: project names containing `dev-` as literal text
- **R-08** — Testing Agent's pre-existing cross-agent task mutation gap is not closed by Build Agent MVP
- **R-09** — Additive DTO fields must not leak into Deployment/Testing renderers
- **R-11** — Users cannot see downstream SIT/UAT/PROD stages from within Build Agent detail because AD-10 disables stitched linked view; mitigation is for users to switch to Deployment Agent, whose stitched detail already covers SIT/UAT/PROD
- **R-12** — `AuditLoggerService` `agentName` fix retroactively changes the value written for Testing Agent audit entries from `"deployment-agent"` to `"testing-agent"`. Forward-only fix; historical rows are not backfilled
- **R-13** — Deployment Agent summary now visibly contains build-only and testing-only rows with empty stage columns. Accepted per AD-12 as "Deployment Agent is the global view"
- **R-14** — Cross-agent stitching at the summary layer does not happen (service pre-filters by agent). A future cross-agent family view is a follow-up, not MVP
