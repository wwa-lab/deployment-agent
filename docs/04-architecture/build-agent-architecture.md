# System Architecture: Build Agent

**Date:** 2026-04-11
**Status:** Draft (v3, post-multi-agent-architecture-review)
**Document type:** Architecture **+ selected design-level specifics**. By explicit scope decision (see **Document Scope** below), this artifact retains concrete interface signatures, file paths, endpoint inventories, and impact lists that would normally live in a design document. The downstream `build-agent-design.md` is correspondingly narrowed to class-level signatures, test matrices, implementation algorithms, and LOC estimates — it does not re-state structural decisions.
**Source:** `build-agent-spec.md` **with explicit supersessions** listed in §Spec Delta below. The spec is the nominal primary source for product intent; where v3 architecture reverses spec-visible behavior, the Spec Delta table is the source of truth until `build-agent-spec.md` is updated in Task #2.
**Supersedes:** v2 (2026-04-10). v2 proposed surgical shared-contract changes (extend `Stage` enum with `DEV`, extend `ReleaseFlowFamilyKey` regex, add `devStatus`/`devPresent` fields to `ReleaseFlowListItemDto`, keep Deployment Agent as the global view). v3 replaces those with a platform refactor that isolates stage vocabulary per agent module. See **Why v3 Exists** below.

---

## Document Scope

This document is **architecture plus selected design**. The team explicitly chose a single-document model rather than splitting structural decisions from implementation contracts across two documents, in order to avoid duplicated or conflicting artifacts. Concretely:

- **Present in this document:** Module boundaries, architectural decisions (PL-*/BA-*), component inventories, file paths, Java interface signatures where they are part of an architectural contract, JPA attribute-level schema claims, specific v2→v3 breaking-route mappings, the required `SecurityConfig.java:36` whitelist edit, and an explicit impact analysis per module.
- **Deferred to `build-agent-design.md`:** Full class-level signatures for new classes, per-method test matrices, line-level implementation algorithms for `DeploymentStitchingService.listStitchedSummaries` and `ReleaseFlowFamilyKey.normalize`, the `createAgentWorkspace` factory's internal implementation, LOC estimates, and per-file code skeletons.

`build-agent-design.md` **must not** re-state or contradict any PL-*/BA- decision in this document; its role is to operationalize those decisions into code-level deliverables.

---

## Why v3 Exists

v2 treated Build Agent as a Testing Agent mirror with four "surgical" shared-contract changes. Under the product goal of scaling to 7–10 independent agents, those changes do not scale: every new agent that introduces a new stage token would force another edit to `Stage`, `ReleaseFlowFamilyKey`, and `ReleaseFlowListItemDto`. After 2–3 more agents those shared contracts become dumping grounds and the agents stop being independent.

v3 resolves this by **refactoring the platform layer once** so that stage vocabulary, stage ordering, and stitching are per-agent concerns, and then delivering Build Agent as the first consumer of the new structure. The refactor is non-breaking for Deployment Agent and Testing Agent at the runtime level: each lands its own `DeploymentStage` / `TestingStage` enum and its own `StagePipeline` bean, and their observable behavior is preserved.

This shifts Build Agent's scope from "four surgical changes to shared contracts" to "platform refactor + one new Agent Module". The total code footprint is larger, but per-agent coupling drops to the minimum that the shared release-flow domain model actually requires.

---

## Spec Delta (Source-of-Truth Contract)

v3 architecture intentionally reverses several statements in `build-agent-spec.md`. Until Task #2 updates the spec in place, this table is authoritative wherever architecture and spec disagree. Each row names the spec location, the v3 position, and the PL-*/BA-* decision that owns the new behavior. Design-stage readers should treat any spec statement not listed here as still binding.

| `build-agent-spec.md` location | Spec statement (summary) | v3 position | Owner |
|---|---|---|---|
| §1.1 Feature Summary | "Build Agent reuses the existing domain model, shared platform capabilities, and most existing backend services." | **Clarified.** Build Agent reuses the existing release-flow *domain shape* (`ReleaseFlow → Request → Task → TaskExecutionHistory`) but does not share the shared `Stage` enum, shared stitching, or shared capability API prefix. Those three concerns move to per-agent modules or a new `/api/platform/*` prefix. | PL-1, PL-2, PL-3, PL-5 |
| §1.1 last paragraph | "Build Agent introduces a new single-stage DEV dimension... documented in §5 and §6." | **Superseded.** DEV is not a new dimension on a shared enum. It is `agents/build/domain/BuildStage { DEV }`, a module-private enum. | PL-3 |
| §5.1 No New Entities, bullet 1 | "One new enum value (`Stage.DEV`)" | **Superseded.** The shared `Stage` enum is **removed**, not extended. Each Agent Module declares its own enum. | PL-3 |
| §5.1 bullet 2 | "Additive DTO fields on `ReleaseFlowListItemDto` (`devStatus`, `devPresent`)" | **Superseded.** DTO replaces positional fields with `Map<String, RequestStatus> stageStatuses` + `Set<String> stagesPresent`. | PL-7 |
| §5.1 bullet 3 | "Extended regex and stage-token recognition in `ReleaseFlowFamilyKey`" | **Superseded.** `ReleaseFlowFamilyKey` moves into Deployment Agent Module (`agents/deployment/domain/`) and never learns DEV or any non-Deployment token. | PL-5 |
| §5.1 last paragraph | "No JPA schema migration is strictly required" | **Still true but for a different reason.** JPA attribute types change from `Stage` enum to `String`; the underlying DB column is already `VARCHAR`, so no Flyway migration. Unique-key structure on `DA_RELEASE_FLOW` is unchanged (see §Data Architecture for the release-flow identity discussion). | PL-3 |
| §5.3 Stage Enum Extension (entire section incl. the code sample) | "Required new implementation: `enum Stage { DEV, SIT, UAT, PROD }` with explicit switch `next()`" | **Superseded.** Shared `Stage` is deleted. Each Agent Module declares its own Stage enum. `next()` is removed from Stage and moves to per-agent `StagePipeline` beans. | PL-3, PL-4 |
| §5.4 ReleaseFlowFamilyKey Extension (entire section) | "Extend STAGE_PREFIX_WITH_SEPARATOR to `^(dev\|sit\|uat\|prod)...`" | **Superseded.** FamilyKey migrates to Deployment Agent Module and keeps the original `sit\|uat\|prod` regex unchanged. No DEV-stripping logic is written. | PL-5 |
| §5.5 ReleaseFlowListItemDto Extension (entire section) | "Add fields `RequestStatus devStatus`, `boolean devPresent`" | **Superseded.** Fixed positional fields become a generic Map/Set. | PL-7 |
| §5.6 Agent Column Usage, Deployment Agent row | "Shows all flows (including legacy null-agent data)" | **Superseded.** Deployment Agent summary is scoped by `agent = "deployment-agent"`. Legacy `agent IS NULL` rows are invisible from every agent workspace until a platform-level Global View ships (R-04). No backfill migration is part of this delivery. | PL-6 |
| §5.7 Cross-Agent Release Flow Behavior | "The Release Flow is shared — it can contain requests from multiple agents spanning DEV + SIT/UAT/PROD" | **Clarified, not superseded.** `DA_RELEASE_FLOW` schema is unchanged — it is still keyed by `(project_id, normalized_release_id)` globally and has no `agent` column. In practice, each agent generates its release IDs with an agent-specific stage prefix (`sit-xxx`, `dev-xxx`, etc.), so duplicate `(projectId, normalized_release_id)` collisions across agents do not occur. Two uploads that would share a family key are always two distinct physical rows; stitching (Deployment-Agent-internal, PL-5) may group them in memory for Deployment Agent's summary only. The "one Release Flow shared by multiple agents" reading of §5.7 is not how the data actually persists today, and v3 does not introduce it. | PL-5, PL-6, §Data Architecture |
| §6.1 Capability Domains, "Shared domain changes required by Build Agent" list | Lists the four surgical shared-contract changes | **Superseded.** The entire bullet list is replaced by PL-1/PL-2/PL-3/PL-5/PL-7 and the corresponding Impact Analysis in this document. | PL-1 |
| §7.3 Release Flow Summary with Agent-Scoped Filtering | Implicitly preserves Deployment Agent global visibility | **Superseded.** PL-6 applies the same agent-scoped filter to Deployment Agent. | PL-6 |
| §7.8 Agent Boundary Enforcement | Presents `AgentBoundaryGuard` as Build Agent-only | **Extended.** The guard is promoted to a Platform Core component invoked by every Agent Module's controllers, including Deployment Agent and Testing Agent. Testing Agent's pre-existing gap (v2 R-08) is closed as a side effect. | PL-9 |
| §10.1 API Prefix | "`/api/build-agent/` only" | **Extended.** A new `/api/platform/*` prefix is added for platform capability routes (`/auth/*`, `/audit-logs`, `/config`, `/access-grants`, `/upload/template`). 16 existing routes currently mounted under `/api/deployment-agent/*` migrate to the new prefix — see §API Boundaries for the full mapping. | PL-2, §API Boundaries |

**Functional Requirements (BFR-*) traceability:** Task #2 will produce an updated spec with BFR-level supersessions. Until then, BFRs referenced from §7 of the spec that depend on the shared `Stage` enum (e.g. BFR-14 stage forcing, BFR-18 legacy data visibility) are re-grounded against PL-3 / PL-6 in this document. No BFR loses its product intent; only the mechanism changes.

---

## Platform Context

WWA hosts multiple Agent Workspaces on top of a shared platform core:

```
FinBlock  →  WWA Agent Workspace Hub  →  Agent Modules
                                          ├── Deployment Agent  (SIT → UAT → PROD)
                                          ├── Testing Agent     (UAT, terminal)
                                          └── Build Agent       (DEV, terminal)   ← NEW
```

- **Platform Core** owns the stage-agnostic release-flow domain (`ReleaseFlow`, `Request`, `Task`, `TaskExecutionHistory`), the task state machine, aggregation, security, audit, configuration, access management, the Jenkins/Ansible execution adapters, and the cross-agent shell (login, navigation, home page).
- **Agent Modules** own their own stage vocabulary (e.g. `BuildStage { DEV }`), their own `StagePipeline`, their own controllers, their own frontend stores/views, and — where applicable — business logic that is not shared (e.g. Deployment Agent's stitched family view).
- **Data isolation** is enforced by the `Request.agent` column (persisted as a String) plus a platform-level `AgentBoundaryGuard` invoked by every agent's controllers on ID-bearing endpoints.

Build Agent is the first Agent Module delivered under this pattern. Deployment Agent and Testing Agent are migrated into the pattern as part of the same delivery so that the codebase reaches a consistent end state rather than a half-migrated one.

---

## Terminology

- **Agent Module** — A self-contained package under `com.wwa.agenthub.agents.<name>/` (backend) and `frontend/src/agents/<name>/` (frontend) that owns an agent's controllers, Stage enum, `StagePipeline` bean, and UI. Agent Modules depend only on Platform Core; they do not depend on each other.
- **Platform Core** — The stage-agnostic, agent-agnostic substrate: domain entities, task state machine, shared services (`TaskService`, `ReleaseFlowService`, `DecisionEngine`, `ReleaseFlowProgressionService`, `RecordResultService`, `ImportService`, `AutoExecutionService`, `AuditLoggerService`, `ConfigurationService`, `AuthService`), security filters, `AgentBoundaryGuard`, and frontend composables (`createAgentWorkspace`, shared `UploadDialog`).
- **Stage Vocabulary** — The set of stage identifiers an Agent Module recognizes. Each Agent Module declares its own enum (e.g. `BuildStage { DEV }`). The persistent `Request.stage` column stores the stage as a String so the platform core never binds to a single closed enum.
- **StagePipeline** — A per-agent `@Component` that knows the stage ordering within that agent and reports its `agentId()`. `DeploymentStagePipeline` encodes `SIT → UAT → PROD`; `TestingStagePipeline` and `BuildStagePipeline` are single-stage terminal pipelines. `ReleaseFlowProgressionService` resolves the right pipeline at call time via `StagePipelineRegistry` (keyed by `request.getAgent()`) rather than calling a hard-coded `Stage.next()`. Controllers never pass pipelines as method parameters. Unknown stages throw `IllegalArgumentException` (fail-loud).
- **StagePipelineRegistry** — A Platform Core `@Component` that injects every `StagePipeline` at startup and builds an immutable `agentId → pipeline` map. `ReleaseFlowProgressionService.progressAfterDecision(taskId)` looks up the right pipeline from the task's parent request's agent column. Missing-agent lookup throws `IllegalStateException`; duplicate `agentId()` fails Spring context startup.
- **Persisted Release Flow** — A single row in the `da_release_flow` table. It belongs to exactly one agent (all of its linked requests share the same `Request.agent` value) and has its own `currentStage` stored as a String.
- **Stitched Summary / Stitched Detail** (*Deployment Agent only*) — In-memory grouping of Persisted Release Flows that share a normalized family key. This logic moves from the shared `ReleaseFlowService` into `agents/deployment/domain/DeploymentStitchingService`. Testing Agent and Build Agent do not stitch.

---

## Overview

Build Agent is delivered as an Agent Module, together with a Platform Core refactor that makes stage vocabulary a per-agent concern. The refactor applies non-breaking runtime changes to Deployment Agent and Testing Agent so that all three agents end up sitting on the same pattern.

The architectural approach has two parts.

### Part A — Platform Core refactor (prerequisite)

1. **Stage enum leaves `contracts/enums/`.** `contracts/enums/Stage.java` is removed. Each Agent Module declares its own stage enum inside its module (`DeploymentStage { SIT, UAT, PROD }`, `TestingStage { UAT }`, `BuildStage { DEV }`). `Request.stage` and `ReleaseFlow.currentStage` are persisted as `String`, so JPA never binds to a single closed enum.
2. **`StagePipeline` + `StagePipelineRegistry` are introduced** as Platform Core components. Each Agent Module provides its own `@Component` `StagePipeline` implementation (reporting `agentId()`). `ReleaseFlowProgressionService.progressAfterDecision(String taskId)` keeps its signature unchanged and gains one new constructor dependency (`StagePipelineRegistry`) that it uses to resolve the right pipeline from `request.getAgent()`. The 5 existing callers (`DecisionController`, `TestingAgentTaskController`, `RecordResultService`, `AutoExecutionService`, `ExternalExecutionMonitorService`) are untouched. Unknown stages throw `IllegalArgumentException`; unknown agents throw `IllegalStateException`.
3. **`ReleaseFlowFamilyKey` and the stitching methods** (`listStitchedSummaries`, `getStitchedDetail`) move out of platform `ReleaseFlowService` into `agents/deployment/domain/DeploymentStitchingService`. Platform `ReleaseFlowService` exposes only stage-agnostic list/get methods. Testing Agent and Build Agent never touch stitching.
4. **`ReleaseFlowListItemDto` is generalized.** The fixed `sitStatus / uatStatus / prodStatus / *Present` fields are replaced with `Map<String, RequestStatus> stageStatuses` and `Set<String> stagesPresent`. Each agent's frontend reads only the stage keys it cares about; adding a new stage never touches the DTO.
5. **Deployment Agent stops being the global view.** The current `ReleaseFlowController.list` default of "show all agents" is replaced with "show only `deployment-agent` flows" (Q1 = peer agents). A separate platform-level Global View endpoint/page is out of scope for this delivery and is tracked as a follow-up.
6. **Package structure.** All agent-specific code moves into `com.wwa.agenthub.agents.<name>/` (backend) and `frontend/src/agents/<name>/` (frontend). The existing `web/controller/*`, `frontend/src/api/`, `frontend/src/stores/`, and `frontend/src/views/` directories are purged of agent-specific files; only platform-level shared code remains there.
7. **Frontend `createAgentWorkspace(config)` factory.** New composable that returns shared workspace plumbing (`{ config, client, api, useStore, routes }`) given an agent configuration object. Each Agent Module's `index.ts` stays small while dedicated agent views and API wrappers layer on top of the shared store/client foundation.

### Part B — Build Agent Module

1. **Backend `agents/build/`** — `BuildStage { DEV }`, `BuildStagePipeline` (single-stage terminal), `BuildReleaseFlowController`, `BuildUploadController`, `BuildTaskController`, `BuildDecisionController`. All controllers force `agent = "build-agent"` server-side and invoke the platform-level `AgentBoundaryGuard` before delegating any ID-bearing call.
2. **Frontend `agents/build/`** — `index.ts` calls `createAgentWorkspace({ agentKey: 'build-agent', stages: ['DEV'], supportsStitching: false, defaultStage: 'DEV' })`, while `api.ts`, `BuildAgentSummaryView.vue`, and `BuildAgentDetailView.vue` provide the Build-specific UI and action wiring.
3. **No new entities, no new tables.** The only schema effect is that `Request.stage` and `ReleaseFlow.currentStage` change their JPA attribute type from `Stage` enum to `String`; the underlying DB column is already `VARCHAR` in both Oracle and H2, so no Flyway migration is required.

### End state

- Build Agent is a self-contained module. Adding the 4th, 5th, … 10th release-flow-style agent is a copy of `agents/build/` with a new stage enum, a new `StagePipeline`, and a new `index.ts`. No platform or existing-agent code changes.
- Stage vocabulary is agent-private. Agents do not share a closed enum.
- Stitching is Deployment Agent's internal feature, not a platform capability.
- Deployment Agent is a peer of the other agents, not an implicit global parent.

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
│  Users                                                                        │
│  Developer · Tech Lead · Task Owner · DevOps Admin · Audit / Management      │
└──────────────────────┬───────────────────────────────────────────────────────┘
                       │ HTTPS + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Web App (Vue 3 SPA)                                                         │
│                                                                              │
│  ┌──────────────────── frontend/src/platform/ ─────────────────────┐         │
│  │  Shell · Router · agentRegistry                                 │         │
│  │  composables/createAgentWorkspace · createReleaseFlowStore      │         │
│  │  components/UploadDialog  (agent-agnostic, props-driven)        │         │
│  └─────────────────────────────────────────────────────────────────┘         │
│                                                                              │
│  ┌─ agents/deployment/ ─┐ ┌─ agents/testing/ ──┐ ┌─ agents/build/ ────┐      │
│  │  index.ts (config)   │ │  index.ts          │ │  index.ts  ← NEW   │      │
│  │  api · store · views │ │  api · store · views│ │  api · store · views│     │
│  │  stages: SIT/UAT/PROD│ │  stages: UAT       │ │  stages: DEV       │      │
│  │  stitched view ✓     │ │  stitched view ✗   │ │  stitched view ✗   │      │
│  └──────────────────────┘ └────────────────────┘ └────────────────────┘      │
└──────────────────────┬───────────────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  API Service (Spring Boot 3)                                                 │
│                                                                              │
│  ┌─ agents/deployment/ ─┐ ┌─ agents/testing/ ──┐ ┌─ agents/build/ ────┐      │
│  │  web/*Controller     │ │  web/*Controller   │ │  web/*Controller   │      │
│  │  domain/             │ │  domain/           │ │  domain/           │      │
│  │    DeploymentStage   │ │    TestingStage    │ │    BuildStage      │      │
│  │    StagePipeline     │ │    StagePipeline   │ │    StagePipeline   │      │
│  │    ReleaseFlowFamily │ │                    │ │                    │      │
│  │    KeyStitchingSvc   │ │                    │ │                    │      │
│  └─────────┬────────────┘ └─────────┬──────────┘ └──────────┬─────────┘      │
│            │                        │                       │                │
│            │        ┌───────────────▼────────────────┐      │                │
│            └───────▶│  AgentBoundaryGuard (platform) │◀─────┘                │
│                     └───────────────┬────────────────┘                       │
│                                     ▼                                         │
│  ┌──────────────────── platform/ (agent-agnostic) ─────────────────────┐    │
│  │  domain/                                                             │    │
│  │    ReleaseFlowService (list/get by agent+stageString, no stitching) │    │
│  │    TaskService · DecisionEngine · RecordResultService                │    │
│  │    ReleaseFlowProgressionService (resolves pipeline via Registry)    │    │
│  │    ImportService · AutoExecutionService                              │    │
│  │    TaskStateMachine · ReleaseFlowAggregation                         │    │
│  │    AuditLoggerService (agentName from scope.agent())                 │    │
│  │    ConfigurationService · AuthService                                │    │
│  │  contracts/                                                          │    │
│  │    Request (stage: String)  ·  ReleaseFlow (currentStage: String)   │    │
│  │    Task · TaskExecutionHistory · UserContext                        │    │
│  │    ReleaseFlowListItemDto                                            │    │
│  │      (Map<String,RequestStatus> stageStatuses, Set<String> present) │    │
│  │    AgentId constants                                                 │    │
│  │  web/security/  ·  web/shared/ (auth, config, audit, access)        │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                     │                                         │
│  ┌──────────────────────────────────▼──────────────────────────────────┐    │
│  │  Spring Data JPA repositories   (stage column: VARCHAR)             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└──────────────┬──────────────────────┬────────────────────────────────────────┘
               ▼                      ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌────────────────────────┐
│  Oracle DB           │  │  Jenkins              │  │  Auth Provider         │
│  (stage: VARCHAR)    │  │  + Ansible Tower      │  │  (UNCHANGED)           │
│                      │  │  (UNCHANGED)          │  │                        │
└──────────────────────┘  └───────────────────────┘  └────────────────────────┘
```

---

## Architecture Decisions

Decisions are classified into two groups:

- **Platform-level (PL-*)** decisions introduce the Agent Module substrate. They apply to Deployment Agent and Testing Agent as well as Build Agent, and they are the non-trivial refactor work delivered in Part A of the Overview.
- **Build Agent-specific (BA-*)** decisions describe the configuration and constraints unique to Build Agent as a consumer of the Agent Module pattern (Part B).

All v2 AD numbers are obsolete. A v2 → v3 correspondence table appears at the end of this section.

---

### PL-1: Platform Refactor Over Surgical Shared-Contract Changes

**Decision:** Resolve Build Agent by refactoring the platform layer once, rather than repeatedly adding surgical extensions to shared contracts for every new agent.

**Alternatives considered:**
- **Continue the v2 approach** (add `DEV` to shared `Stage`, extend `ReleaseFlowFamilyKey` regex, append `devStatus`/`devPresent` to `ReleaseFlowListItemDto`) — rejected. Each new agent with a new stage token would repeat the same surgery. After 4–5 agents the shared enum, the family-key regex, and the DTO become unbounded dumping grounds and the agents stop being independent.
- **Give Build Agent a parallel domain model** (`BuildJob` entity, independent service layer) — rejected. BA-1 through BA-6 are deliberate mirrors of Deployment/Testing Agent stories including Task, Decision, Record Result, Rerun. Build Agent is literally a release-flow-style agent; duplicating the domain would create a maintenance-burden fork with no product justification.

**Rationale:** The problem is not that Build Agent doesn't fit the release-flow model — it does. The problem is that the current platform bakes stage vocabulary, stage ordering, and stitching into the shared contract layer. Moving those three concerns into per-agent modules resolves the coupling without duplicating domain logic.

**Consequences:**
- One-time refactor delivered with Build Agent instead of three smaller "technical debt" items spread over future agent deliveries.
- Deployment Agent and Testing Agent migrate into the pattern in the same delivery; they each gain a local Stage enum and a `StagePipeline` bean, and they lose the shared `Stage` enum.
- All three agents converge on the same package structure, which becomes the template for agents 4 through 10.

---

### PL-2: Agent Module Package Structure

**Decision:** All agent-specific code lives under a dedicated agent module:
- **Backend:** `com.wwa.agenthub.agents.<name>/` with subpackages `web/` (controllers) and `domain/` (Stage enum, `StagePipeline`, agent-specific services).
- **Frontend:** `frontend/src/agents/<name>/` with `index.ts`, `api.ts`, `store.ts`, `SummaryView.vue`, `DetailView.vue`.

**Boundary rules:**
- An Agent Module depends only on Platform Core. It does not import from another agent's package.
- Platform Core does not know any agent's name beyond the `AgentId` constants. No `if (agentId.equals("build-agent"))` branches in platform code.
- Shared capability controllers (Auth, Configuration, Audit Log, Access Grant, Template Management) stay in Platform Core; they are not copied into each agent module.

**Alternatives considered:**
- **Keep the flat `web/controller/` directory** — rejected. The 15-item "new agent checklist" scattered across seven directories does not scale past three agents.
- **One Maven module per agent** — rejected for this delivery. Would require a multi-module Maven project and complicate the existing build. Package boundaries + ArchUnit fitness tests are a lighter-weight version of the same discipline and can be upgraded to separate Maven modules later.

**Consequences:**
- Existing Deployment Agent and Testing Agent code moves packages. Test classes move with them. Imports update throughout the codebase.
- Adding a new agent is a new directory, not edits scattered across seven existing locations.
- ArchUnit tests enforce the dependency rules so the boundary does not erode silently.

---

### PL-3: Per-Agent Stage Vocabulary, Persisted as String

**Decision:** The shared `contracts/enums/Stage` enum is removed. Each Agent Module declares its own Stage enum inside its package:
- `agents/deployment/domain/DeploymentStage { SIT, UAT, PROD }`
- `agents/testing/domain/TestingStage { UAT }`
- `agents/build/domain/BuildStage { DEV }`

Platform Core services operate on `String stage` values. `Request.stage` and `ReleaseFlow.currentStage` JPA attributes change from `Stage` enum to `String`. The underlying DB column is already `VARCHAR` in Oracle and H2, so no Flyway migration is needed. Each agent's controller layer converts between the String and the module-local enum at the HTTP boundary.

**Alternatives considered:**
- **Keep a shared `Stage` enum and add `DEV` to it** (v2 approach) — rejected per PL-1.
- **Make `Stage` an open string-backed value class in Platform Core** — rejected. Solves the closed-enum problem but still centralizes stage vocabulary in one place, which is exactly the coupling we want to remove. Per-agent enums enforce the "agents do not share stage vocabulary" invariant more strongly.
- **Let agents share a Stage enum when names coincide** (e.g. Testing Agent reuses `DeploymentStage.UAT`) — rejected. Coupling vocabularies by accident of name recreates the problem. Testing Agent's `TestingStage.UAT` is a different Java type from Deployment Agent's `DeploymentStage.UAT` even though they share a product meaning today.

**Rationale:** Stage vocabulary is a product concept owned by each agent. Per-agent enums give IDE-level safety inside each module (exhaustive `switch`, type-safe method signatures) while keeping Platform Core String-typed and open.

**Consequences:**
- Every usage of `Stage` in domain services, repositories, and DTOs switches to `String`. This is the largest mechanical edit in Part A.
- `@Enumerated(EnumType.STRING)` annotations on stage columns are removed.
- Persisted data is unchanged — values were always stored as strings (`"SIT"`, `"UAT"`, `"PROD"`).
- An ArchUnit test asserts that no class in `platform/` imports any Stage enum class from any `agents/` package.

---

### PL-4: StagePipeline as a Per-Agent Bean, Resolved via Platform Registry

**Decision:** Introduce a platform interface and a platform registry:

```java
public interface StagePipeline {
    String agentId();
    Optional<String> next(String currentStage);    // throws on unknown stage
    boolean isTerminal(String stage);               // throws on unknown stage
    List<String> orderedStages();
}
```

Each Agent Module provides its own `@Component` implementation (`DeploymentStagePipeline`, `TestingStagePipeline`, `BuildStagePipeline`), each reporting its own `agentId()`. A Platform Core component `StagePipelineRegistry` injects every `@Component StagePipeline` at startup and builds an immutable `agentId → pipeline` map. `ReleaseFlowProgressionService` receives the registry as a constructor dependency and resolves the correct pipeline at call time via `registry.forAgent(request.getAgent())`. **The method signature `progressAfterDecision(String taskId)` is unchanged.**

**Alternatives considered:**
- **Pass `StagePipeline` as a method parameter to `progressAfterDecision`** (this was an earlier v3 draft) — rejected. The method is called from five sites including `RecordResultService.recordResult`, `AutoExecutionService.submitAutoExecution`, and `ExternalExecutionMonitorService.processCallback`. The monitor service runs on a Jenkins/Ansible callback thread with no HTTP or agent ambient context. Parameter threading would force agent semantics deep into Platform Core services and violate PL-2.
- **Inject a single `StagePipeline` as a field on `ReleaseFlowProgressionService`** — rejected. Would force the service to be agent-scoped and contradict its role as a shared platform service.
- **Keep `Stage.next()` as an enum method** — rejected. Ordering is per-agent; per-agent enums cannot know cross-agent ordering.
- **"Unknown stage returns `Optional.empty()` from `next(...)`" (fail-silent)** — rejected. A mis-routed progression call (e.g. a Deployment Agent flow at `"SIT"` accidentally resolved through `BuildStagePipeline`) would silently mark the flow `Completed` — silent data corruption. Pipelines throw `IllegalArgumentException` on unknown stages so routing bugs surface loudly.

**Rationale:**
- Single resolution point. Pipeline is looked up exactly once, inside `progressAfterDecision`, from data that is already loaded (`request.getAgent()`).
- Background-callback safe. `ExternalExecutionMonitorService` can call `progressAfterDecision(taskId)` from any thread; the registry lookup derives agent from the persistent data, not from ambient context.
- Fail-loud. Unknown agent → `IllegalStateException` from registry. Unknown stage → `IllegalArgumentException` from pipeline. Both cases roll back the enclosing transaction.

**Consequences:**
- `ReleaseFlowProgressionService.progressAfterDecision(String taskId)` signature is **unchanged**. All five existing call sites continue working without modification:
  - `DecisionController.java:41`
  - `TestingAgentTaskController.java:133` (migrates to Testing Agent Module in Phase H)
  - `RecordResultService.java:98`
  - `AutoExecutionService.java:159`
  - `ExternalExecutionMonitorService.java:207`
- `ReleaseFlowProgressionService` constructor gains one new dependency: `StagePipelineRegistry`.
- Each Agent Module declares one `@Component` class implementing `StagePipeline`.
- `StagePipelineRegistry` is introduced as a Platform Core component.
- ArchUnit rule: Platform Core services other than `StagePipelineRegistry` and `ReleaseFlowProgressionService` must not reference any `StagePipeline` subtype by name; they work through the interface and registry only.

---

### PL-5: Stitching Moves to the Deployment Agent Module

**Decision:** `ReleaseFlowFamilyKey`, `ReleaseFlowService.listStitchedSummaries`, and `ReleaseFlowService.getStitchedDetail` move from platform `ReleaseFlowService` into a new `agents/deployment/domain/DeploymentStitchingService`. Platform `ReleaseFlowService` exposes only stage-agnostic list/get operations:
- `Page<ReleaseFlow> listByAgent(String agentId, filters, Pageable)`
- `ReleaseFlow getById(String id, boolean includeArchived)`
- `List<Request> findRequestsForFlow(String releaseFlowId, boolean includeArchived)`

**Rationale (Q2 = stitching is Deployment Agent business logic):**
- Stitching exists so Deployment Agent users can see how a release flows through the SIT/UAT/PROD family. It is not a platform capability; Testing Agent and Build Agent have no product use for it.
- Leaving stitching in the shared `ReleaseFlowService` forces every other agent to know the concept exists (even if only to avoid it) and keeps the shared `ReleaseFlowFamilyKey` regex as a multi-agent concern.

**Consequences:**
- Testing Agent currently calls `listStitchedSummaries` by accident of code sharing. It migrates to platform `listByAgent`. Because Testing Agent is UAT-only, stitching has no visible effect today — the migration is behavior-preserving.
- `DeploymentStitchingService` is the only caller of `ReleaseFlowFamilyKey`. The family-key regex recognizes only Deployment Agent's stage tokens (SIT/UAT/PROD). Build Agent's DEV never enters the family-key's scope.
- Any future "cross-agent family view" is a new platform capability (a Global View), not an enhancement to Deployment Agent's stitcher.

---

### PL-6: Deployment Agent Is Peer-Scoped, Not Global

**Decision:** Deployment Agent's summary list defaults to `agent = "deployment-agent"`, matching Testing Agent and Build Agent. The current "Deployment Agent shows all agents' flows" behavior is removed.

**Rationale (Q1 = peer agents):** The "Deployment Agent = everything" assumption from v2's AD-12 made Deployment Agent an implicit parent of the other agents and undermined the "multi-agent workspace of equals" product goal. Removing it makes the three agents structurally symmetric.

**Consequences:**
- A user who wants a cross-agent view must switch between agent workspaces. Deployment Agent no longer shows DEV flows; Build Agent does not show SIT/UAT/PROD flows.
- A platform-level **Global View** page (shows all flows across all agents, intended for DevOps admins and auditors) is acknowledged as a needed capability but is **out of scope for this delivery**. Tracked as follow-up R-13 (replaces v2's R-13).
- Legacy `Request` rows with `agent IS NULL` become invisible from every agent workspace under PL-6. They remain in the database untouched and will become visible again only when the platform-level Global View ships (R-13). No backfill migration is part of this delivery.
- Existing Deployment Agent tests that assert "all flows visible regardless of agent" are updated to assert "only deployment-agent flows visible".

---

### PL-7: ReleaseFlowListItemDto Uses Generic Stage Maps

**Decision:** Replace the fixed positional fields `sitStatus`, `uatStatus`, `prodStatus`, `sitPresent`, `uatPresent`, `prodPresent` with:
```java
Map<String, RequestStatus> stageStatuses   // key = stage String, e.g. "SIT", "DEV"
Set<String> stagesPresent
```
Only stages that actually have requests on a given flow appear in the map and set.

**Alternatives considered:**
- **Append `devStatus` / `devPresent`** (v2 AD-9) — rejected per PL-1.
- **Nested `stageStatuses: List<StageStatusEntry>` object** — rejected. A `Map<String, RequestStatus>` serializes to a clean JSON object and is simpler to read on the frontend.

**Rationale:** Adding a new stage to any agent never touches this DTO again. Frontend code reads `stageStatuses["SIT"]` using the agent's known stage keys, so there is no ambiguity about which stages to render.

**Consequences:**
- All Deployment Agent and Testing Agent frontend code reading `item.sitStatus` etc. migrates to `item.stageStatuses.SIT`. Mechanical change.
- Snapshot tests for existing summary tables update to the new JSON shape.
- The resulting JSON envelope is forward-compatible: adding a stage key is additive; frontend code that iterates only its own known keys is unaffected.

---

### PL-8: Frontend createAgentWorkspace Factory

**Decision:** Introduce a composable `createAgentWorkspace(config)` in `frontend/src/platform/composables/`. It takes an agent configuration and returns shared workspace plumbing (`{ config, client, api, useStore, routes }`). Each Agent Module's `index.ts` reduces to roughly:

```ts
import { createAgentWorkspace } from '@/platform/composables/createAgentWorkspace'

export const buildAgentWorkspace = createAgentWorkspace({
  agentKey: 'build-agent',
  agentName: 'Build Agent',
  stages: ['DEV'],
  supportsStitching: false,
  defaultStage: 'DEV',
})
```

**Alternatives considered:**
- **Continue copy-pasting** `releaseFlow.ts` into `testingAgentReleaseFlow.ts` etc. — rejected. 7–10 agents × ~5 files per agent = 35–50 copy-pasted files that drift independently whenever the pattern evolves.
- **CLI scaffolder that generates boilerplate** — rejected. Adds tooling without reducing duplication at review time. A runtime factory gives the same ergonomics with a stronger "all agents share one implementation" guarantee.

**Rationale:** The factory is the frontend expression of PL-2. Without it, the Agent Module package structure still ships 35+ near-identical files across 7 agents.

**Consequences:**
- Deployment Agent's and Testing Agent's current hand-written stores, views, and API modules are replaced with `createAgentWorkspace(...)` calls in their respective `agents/<name>/index.ts`.
- Agent-specific UI variations (e.g. "stage filter is a disabled input rather than a dropdown when the agent has only one stage") are passed as configuration, not as component overrides.
- Customizations not expressible as config are handled by slot-based composition; the factory does not try to be a universal view framework.

---

### PL-9: Controller-Layer Agent Boundary Guard (Platform Component)

**Decision:** `AgentBoundaryGuard` is a Platform Core component used by every Agent Module's controllers. On ID-bearing endpoints, the controller calls:
- `assertTaskBelongsToAgent(taskId, expectedAgent)`
- `assertRequestBelongsToAgent(requestId, expectedAgent)`
- `assertFlowBelongsToAgent(flowId, expectedAgent)`

A mismatch throws a `NotFoundException` that the global exception handler maps to HTTP 404 (not 403), so that task and flow IDs do not leak across agent namespaces.

**Alternatives considered:**
- **Push the check into domain services** with an `expectedAgent` parameter — rejected. Domain services stay agent-agnostic under PL-3/PL-4.
- **Spring Security `@PreAuthorize`** — rejected. The existing codebase performs authorization imperatively inside controller methods (`validateRequestScope`, `validateAdmin`, etc.). Introducing `@PreAuthorize` only for boundary checks would fragment the authorization style.
- **Skip the check and rely on query filters** — rejected. A data isolation claim that depends on every query being correct is too fragile for a multi-agent platform.

**Rationale:** Consistent with the existing imperative validation style. Keeps domain services agent-agnostic. The guard lives in Platform Core because all agents need it identically.

**Consequences:**
- Every Agent Module's controllers invoke the guard before delegating any ID-bearing call.
- Testing Agent gains boundary enforcement it did not have in v2 (closes the pre-existing R-08 gap as a side effect of the migration).
- Deployment Agent also gains boundary enforcement; under PL-6 Deployment Agent is scoped, so the guard and the query filter together provide defense in depth.

---

### PL-10: Thin Controller Delegation with Explicit Translation

**Decision:** Agent Module controllers are thin wrappers whose only responsibilities are:

1. Force `agent` and, where applicable, `stage` server-side (ignore client-supplied values).
2. Invoke `AgentBoundaryGuard` for ID-bearing calls.
3. Convert between String stage values and the module-local Stage enum at the HTTP boundary.
4. Delegate everything else to Platform Core services. Controllers do NOT inject or pass `StagePipeline` — pipeline resolution happens inside `ReleaseFlowProgressionService` via `StagePipelineRegistry` (see PL-4).

No business logic lives in controllers beyond these four responsibilities.

**Rationale:** Controllers are the one layer that knows both the agent context and the platform's String-based domain services; they are the natural translation boundary. Business logic in controllers is the single most common cause of divergence between agents and must be actively resisted.

**Consequences:**
- Any fix applied to a Platform Core service benefits all agents automatically.
- Controller tests are small: force-agent, call-guard, delegate, translate-response.
- New endpoints rarely require platform changes; they usually just wire up existing platform capabilities.

---

### PL-11: Dynamic agentName in AuditLoggerService

**Decision:** `AuditLoggerService.log` derives `agentName` from `scope.agent()` rather than the current hardcoded `"deployment-agent"` literal. The current implementation keeps a guarded fallback to `agentName = "platform"` for platform-scoped capability events whose audit scope is not attributable to a single agent module.

**Context:** `AuditLoggerService.java:61` currently hardcodes `entry.setAgentName("deployment-agent")`, so every audit entry from every agent is tagged as Deployment Agent regardless of the actual workspace. This is a pre-existing defect affecting Testing Agent today and would affect Build Agent if left unfixed.

**Consequences:**
- Build Agent ships with correct audit tagging from day one.
- Testing Agent's pre-existing defect is repaired as a side effect of the refactor.
- Historical Testing Agent audit rows in production remain incorrectly tagged as `"deployment-agent"`. Forward-only fix (R-12).

---

### BA-1: BuildStage Is a Single-Stage Terminal Enum

**Decision:** `agents/build/domain/BuildStage` has exactly one value: `DEV`. `BuildStagePipeline.next("DEV")` returns `Optional.empty()` and `isTerminal("DEV")` returns `true`. A Build Agent release flow completing its last task is marked `Completed`; it never auto-advances.

**Alternatives considered:**
- **Multi-stage build pipeline** (e.g. `COMPILE → PACKAGE → PUBLISH`) — rejected for MVP per spec §1.6.
- **Allow DEV → SIT auto-progression into Deployment Agent** — rejected. Would cross an agent module boundary, violating PL-2.

**Rationale:** Build Agent's product scope is the DEV phase only. Cross-agent progression, if ever needed, is an explicit product feature (likely a manual "Promote to SIT" action), not an implicit pipeline step.

---

### BA-2: Build Agent Does Not Implement Stitching

**Decision:** Build Agent's summary and detail endpoints never call `DeploymentStitchingService`. Build Agent has no notion of linked flows, no `?linked=` query parameter, and no cross-flow family view.

**Rationale:** Stitching is Deployment Agent's feature per PL-5. Build Agent is a peer Agent Module; it does not consume other agents' internal services.

**Duplicate-upload behavior (aligned with current `ImportService`):**
- Build Agent release flow identity follows the existing `DA_RELEASE_FLOW` unique index on `(project_id, normalized_release_id)` — see §Data Architecture and PL-3 for why the schema does not change.
- `ImportService.findOrCreateReleaseFlowByIdentifier` looks up the existing row by `(projectId, normalizedReleaseId)` and **reuses it** when present. A second Build Agent upload with the same normalized release identifier is an **upsert into the same row**, not a new row. The new Request and its Tasks are appended as children of the existing ReleaseFlow.
- This matches Deployment Agent's current behavior for repeat SIT/UAT/PROD uploads and is not a new semantic.
- There is no "two separate rows for the same DEV-1234" outcome in this delivery. If a user wants to represent a genuinely distinct build attempt, the current mechanism is to use a different release identifier (e.g. `DEV-1234-v2`); formalizing that pattern is a Build-Agent-internal concern and is not part of this delivery.

**Cross-agent family visibility:**
- A user who wants to see `DEV → SIT → UAT → PROD` for a single underlying release either switches between agent workspaces manually, or waits for the platform-level Global View (R-04).

---

### BA-3: Separate API Prefix `/api/build-agent/`

**Decision:** Build Agent uses `/api/build-agent/` as its API prefix, matching the existing `/api/deployment-agent/` and `/api/testing-agent/` patterns. No shared routes.

**Rationale:** Clear namespace separation, independent evolution, simpler auditing.

---

### Inherited Decisions (carried forward from v2 without structural change)

- **Shared Access Model** — Access grants are shared across all agents. An active grant allows access to any agent workspace. No `agent` dimension is added to `AccessScope`. Per-agent access control is out of scope.
- **Agent Identity Constants** — Agent identity lives in `platform/contracts/AgentId` (backend, after the Phase H package move) and `frontend/src/config/agentId.ts` (frontend). No string literals for agent identifiers in controllers, services, or views.

---

### v2 → v3 AD Correspondence

| v2 AD | Outcome in v3 |
|---|---|
| v2 AD-1 (Thin Controller Delegation) | **PL-10** (expanded to explicit translation responsibilities) |
| v2 AD-2 (Surgical Shared-Contract Changes) | **Replaced** by PL-1 |
| v2 AD-3 (DEV is Terminal via `Stage.DEV.next() == null`) | **BA-1** (re-expressed via `BuildStagePipeline`) |
| v2 AD-4 (Agent Boundary Guard) | **PL-9** (now platform-level) |
| v2 AD-5 (Separate API Prefix) | **BA-3** |
| v2 AD-6 (Separate Store) | Subsumed by **PL-8** (createAgentWorkspace factory) |
| v2 AD-7 (Shared Access) | **Inherited Decisions** |
| v2 AD-8 (Agent Identity Constants) | **Inherited Decisions** |
| v2 AD-9 (DTO Fields Additive) | **Replaced** by PL-7 |
| v2 AD-10 (No Stitched Detail in Build Agent) | **BA-2** (rationale changes: Build Agent does not touch stitching at all, not just "MVP scope") |
| v2 AD-11 (Dynamic agentName) | **PL-11** |
| v2 AD-12 (Deployment Agent Global View) | **Removed**, replaced by PL-6 + follow-up R-13 |

---

## Component Architecture

This section lists components by module. Platform Core is the agent-agnostic substrate; each Agent Module is self-contained and depends only on Platform Core.

### Backend — Platform Core

**New components**
- `platform/domain/StagePipeline` — interface (PL-4). Each Agent Module provides its own `@Component` implementation. Pipelines expose `agentId()` for registry lookup and throw `IllegalArgumentException` on unknown stages.
- `platform/domain/StagePipelineRegistry` — Platform Core `@Component` (PL-4). Auto-injects every `StagePipeline` at startup, builds an immutable `agentId → pipeline` map, and serves runtime lookups from `ReleaseFlowProgressionService`. Fail-loud on duplicate `agentId()` at startup and on missing agent at runtime.
- `platform/web/security/AgentBoundaryGuard` — promoted from "Build Agent-only helper" (v2) to a Platform Core component used by every Agent Module (PL-9). Provides `assertTaskBelongsToAgent`, `assertRequestBelongsToAgent`, `assertFlowBelongsToAgent`.
- `platform/web/shared/` — new subpackage for capability controllers that are not agent-specific. **New API prefix `/api/platform/`** replaces the current historical mounting under `/api/deployment-agent/*`:

  | Controller | v2 route | v3 route |
  |---|---|---|
  | `AuthController` (login, logout, session) | `/api/deployment-agent/auth/*` | `/api/platform/auth/*` |
  | `AuditLogController` | `/api/deployment-agent/audit-logs` | `/api/platform/audit-logs` |
  | `ConfigurationController` | `/api/deployment-agent/config` | `/api/platform/config` |
  | `AccessGrantController` | `/api/deployment-agent/access-grants` | `/api/platform/access-grants` |
  | `TemplateDownloadController` (shared Excel template) | `/api/deployment-agent/templates/*` | `/api/platform/upload/template` |

  This is a **breaking change to existing routes**. The following constraints ensure the migration is safe at runtime:

  - **Session cookies are preserved.** `application.properties:15` sets `server.servlet.context-path=/` and no `server.servlet.session.cookie.path` override exists. Therefore the `JSESSIONID` cookie's default `Path` attribute is `/`, and the cookie is sent on every `/api/*` request regardless of sub-prefix. Moving the auth routes from `/api/deployment-agent/auth/*` to `/api/platform/auth/*` does not require users to re-authenticate. `SessionAuthFilter` is unchanged (ref: `SessionAuthFilter.java:28`, which uses standard `request.getSession(false)`).
  - **`SecurityConfig` whitelist must be updated.** `SecurityConfig.java:36` currently contains `.requestMatchers("/api/deployment-agent/auth/login").permitAll()`. This line must change to `.requestMatchers("/api/platform/auth/login").permitAll()` in the same commit that moves `AuthController`, otherwise the new login route would be rejected by Spring Security before it can authenticate and the login loop becomes inescapable.
  - **Frontend login view and all hard-coded auth URL references** update to the new prefix in the same delivery.

**Modified components**
- `ReleaseFlowService` — stitching methods (`listStitchedSummaries`, `getStitchedDetail`) and the `ReleaseFlowFamilyKey` dependency are **removed** (PL-5). Surviving signature: `listByAgent(String agentId, filters, Pageable)` + `getById(...)` + `findRequestsForFlow(...)`. All stage-aware logic operates on `String stage`.
- `ReleaseFlowProgressionService.progressAfterDecision(String taskId)` — **signature unchanged** (verified against `ReleaseFlowProgressionService.java:49`). Constructor gains one new dependency: `StagePipelineRegistry`. The terminal-check at `ReleaseFlowProgressionService.java:72` changes from `releaseFlow.getCurrentStage().next() == null` to `pipeline.isTerminal(flow.getCurrentStage())` with `pipeline = stagePipelineRegistry.forAgent(request.getAgent())`. All five call sites (`DecisionController:41`, `TestingAgentTaskController:133`, `RecordResultService:98`, `AutoExecutionService:159`, `ExternalExecutionMonitorService:207`) continue working unchanged.
- `ReleaseFlowAggregation` — iteration over `Stage.values()` is replaced with iteration over the distinct stage strings present on the requests being aggregated.
- `ReleaseFlow` entity — `currentStage` attribute type changes from `Stage` to `String`. `@Enumerated(EnumType.STRING)` removed.
- `Request` entity — `stage` attribute type changes from `Stage` to `String`. `@Enumerated(EnumType.STRING)` removed.
- `ReleaseFlowListItemDto` — positional fixed stage fields are replaced by `Map<String, RequestStatus> stageStatuses` and `Set<String> stagesPresent` (PL-7). Record constructor and `from()` factory methods are updated at every call site.
- `AuditLoggerService.log` — `agentName` derived from `scope.agent()` with a guarded `platform` fallback for platform-scoped events (PL-11).
- `AgentId` — add `BUILD_AGENT` constant.

**Deleted components**
- `contracts/enums/Stage.java` — stage vocabulary moves into Agent Modules (PL-3).
- `ReleaseFlowService.listStitchedSummaries` / `getStitchedDetail` — logic migrates to Deployment Agent Module (PL-5).
- `domain/releaseflow/ReleaseFlowFamilyKey.java` — migrates to Deployment Agent Module (PL-5).

**Unchanged**
- `TaskService`, `ImportService`, `DecisionEngine`, `RecordResultService`, `AutoExecutionService`, `TaskExecutionHistoryService`, `ConfigurationService`, `AuthService` — business logic is preserved; method signatures change only where a `Stage` enum parameter or return type becomes `String`.
- `TaskStateMachine` (pure function, no stage awareness).
- All Spring Data JPA repositories — method signatures change where they previously accepted `Stage`, but query semantics are unchanged.
- All JPA entities other than the `ReleaseFlow.currentStage` and `Request.stage` attribute-type changes listed above.
- Security filters (`SessionAuthFilter`, `HeaderAuthFilter`) and Spring Security configuration.

---

### Backend — Deployment Agent Module

**New location:** `com.wwa.agenthub.agents.deployment/`

**New components**
- `domain/DeploymentStage { SIT, UAT, PROD }` — agent-local enum (PL-3).
- `domain/DeploymentStagePipeline` — `@Component implements StagePipeline` encoding `SIT → UAT → PROD` with PROD terminal (PL-4).
- `domain/ReleaseFlowFamilyKey` — moved from platform (PL-5). Regex knows only SIT/UAT/PROD tokens. The v2 "conservative DEV stripping" logic is deleted; it is no longer needed because family key never sees DEV.
- `domain/DeploymentStitchingService` — owns `listStitchedSummaries` and `getStitchedDetail` (PL-5). Sole caller of `ReleaseFlowFamilyKey`.

**Moved components**
- `web/controller/ReleaseFlowController` → `agents/deployment/web/DeploymentReleaseFlowController`. Summary list now forces `agent = "deployment-agent"` (PL-6). Detail endpoint delegates to `DeploymentStitchingService` when `?linked=` is supplied.
- `web/controller/UploadController` → `agents/deployment/web/DeploymentUploadController`. Forces `agent = "deployment-agent"` server-side.
- `web/controller/TaskController` → `agents/deployment/web/DeploymentTaskController`. Invokes `AgentBoundaryGuard` before every ID-bearing call (PL-9).
- `web/controller/DecisionController` → `agents/deployment/web/DeploymentDecisionController`. Calls `progressAfterDecision(taskId)` unchanged; pipeline resolution happens inside the service via `StagePipelineRegistry`.

**Behavioral change**
- Summary list no longer returns flows from other agents or from legacy null-agent rows (PL-6). Existing integration tests that asserted "all flows visible regardless of agent" are updated.

---

### Backend — Testing Agent Module

**New location:** `com.wwa.agenthub.agents.testing/`

Testing Agent has not been publicly released; it is still in internal testing. The v2 → v3 migration treats Testing Agent as unshipped, so no "behavior-preserving" hedging applies — the module simply lands in its final v3 shape.

**New components**
- `domain/TestingStage { UAT }` — agent-local enum.
- `domain/TestingStagePipeline` — single-stage terminal pipeline (`isTerminal("UAT") == true`).

**Moved components**
- `web/controller/TestingAgent*Controller` → `agents/testing/web/Testing*Controller`. Each gains an `AgentBoundaryGuard` invocation it did not have in v2 (closes R-08 as a side effect of PL-9).
- Summary list uses platform `listByAgent("testing-agent", ...)`. The accidental `listStitchedSummaries` call from v2 is removed.

---

### Backend — Build Agent Module

**New location:** `com.wwa.agenthub.agents.build/`

**All new components**
- `domain/BuildStage { DEV }` — single-value enum.
- `domain/BuildStagePipeline` — `next("DEV") == Optional.empty()`, `isTerminal("DEV") == true`, `orderedStages() == List.of("DEV")` (BA-1).
- `web/BuildReleaseFlowController` — thin wrapper around platform `listByAgent` / `getById` / `findRequestsForFlow`. Does not accept `?linked=`. Never calls `DeploymentStitchingService` (BA-2).
- `web/BuildUploadController` — forces `agent = "build-agent"` and `stage = "DEV"` server-side. Calls platform `ImportService` with `(file, agentId, stage, userContext)` — `ImportService` is agent-agnostic and does not take a pipeline.
- `web/BuildTaskController` — invokes `AgentBoundaryGuard` on every task ID, then delegates to platform `TaskService`.
- `web/BuildDecisionController` — invokes `AgentBoundaryGuard`, then calls `ReleaseFlowProgressionService.progressAfterDecision(taskId)` with the unchanged signature. Pipeline resolution happens inside the service.

---

### Frontend — Platform Core

**New location:** `frontend/src/platform/`

**New components**
- `api/platformClient.ts` — Axios instance with `baseURL: '/api/platform'`. Shared 401 interceptor redirects to `/login`. Used by every platform capability API module.
- `api/auth.ts`, `api/audit.ts`, `api/config.ts`, `api/accessGrants.ts` — platform capability API modules, migrated from their current location in `frontend/src/api/` and rebound to `platformClient`.
- `stores/user.ts`, `stores/audit.ts`, `stores/config.ts`, `stores/accessGrants.ts` — platform capability Pinia stores, migrated from `frontend/src/stores/`.
- `composables/createAgentWorkspace.ts` — factory from PL-8. Returns shared workspace plumbing (`{ config, client, api, useStore, routes }`) from a config object. Dedicated agent views consume that shared store/client foundation.
- `composables/createReleaseFlowStore.ts` — generic Pinia store factory parameterized by agent config.
- `composables/createReleaseFlowApi.ts` — generic API module factory (agent Axios client + CRUD methods).
- `components/AgentSummaryView.vue` / `components/AgentDetailView.vue` — generic read-only building blocks used during the refactor and still available as shared foundations, while the shipping agents now keep dedicated summary/detail views for richer upload and task workflows.
- `config/agentRegistry.ts` — existing file moves here; grows a Build Agent entry.
- `frontend/src/config/agentId.ts` — grows `BUILD_AGENT` constant.
- `views/LoginView.vue`, `views/WwaHomeView.vue`, `views/WorkspaceLayout.vue`, `views/TemplateManagementView.vue`, `views/ConfigAdminView.vue`, `views/AuditLogView.vue`, `views/AccessManagementView.vue` — platform-owned shell and capability views, migrated from `frontend/src/views/`.

**Moved components**
- `components/UploadDialog.vue` → `frontend/src/platform/components/UploadDialog.vue`. Already agent-agnostic in the current codebase; just changes location.

---

### Frontend — Deployment Agent Module

**New location:** `frontend/src/agents/deployment/`

**Contents**
- `index.ts` — ~20 lines calling `createAgentWorkspace({ agentKey: 'deployment-agent', agentName: 'Deployment Agent', stages: ['SIT', 'UAT', 'PROD'], supportsStitching: true })`.

Deployment Agent keeps dedicated summary/detail views on top of the shared workspace plumbing. The stitched linked-detail behavior remains implemented entirely at the backend layer — when a Deployment Agent detail URL carries `?linked=<ids>`, the frontend passes the query parameter through, and `DeploymentStitchingService` returns a `ReleaseFlowDetailDto` populated with the stitched requests.

**Deleted**
- Legacy shared frontend API wrappers are removed in favor of `frontend/src/api/platformClient.ts`, agent-local `frontend/src/agents/*/api.ts`, and the shared workspace factory output.
- `frontend/src/stores/releaseFlow.ts`, `task.ts` — absorbed into the factory store.
- `frontend/src/views/ReleaseFlowSummaryView.vue`, `ReleaseFlowDetailView.vue` — superseded by `frontend/src/agents/deployment/ReleaseFlowSummaryView.vue` and `ReleaseFlowDetailView.vue`.

---

### Frontend — Testing Agent Module

**New location:** `frontend/src/agents/testing/`

**Contents**
- `index.ts` — `createAgentWorkspace({ agentKey: 'testing-agent', agentName: 'Testing Agent', stages: ['UAT'], supportsStitching: false, defaultStage: 'UAT' })`.

**Deleted**
- `frontend/src/api/testingAgentClient.ts`, `testingAgentReleaseFlows.ts`, `testingAgentTasks.ts`, `testingAgentUpload.ts`.
- `frontend/src/stores/testingAgentReleaseFlow.ts`.
- `frontend/src/views/TestingAgentSummaryView.vue`, `TestingAgentDetailView.vue`.

---

### Frontend — Build Agent Module

**New location:** `frontend/src/agents/build/`

**All new**
- `index.ts` — `createAgentWorkspace({ agentKey: 'build-agent', agentName: 'Build Agent', stages: ['DEV'], supportsStitching: false, defaultStage: 'DEV' })`.
- `api.ts` — Build-specific release-flow, task, decision, upload, and template wrappers bound to `/api/build-agent/*` and `/api/platform/upload/template`.
- `BuildAgentSummaryView.vue` — DEV-only summary page with upload dialog, filters, and list table.
- `BuildAgentDetailView.vue` — DEV-only detail page with request tabs plus task edit/run/result/activity/decision actions.

---

## Data Architecture

### Schema Changes

No new tables. No new columns. No Flyway migration is required.

The Platform Core refactor changes JPA attribute types on two columns, but the underlying DB column type is already `VARCHAR` on Oracle and H2:

| Column | Old JPA type | New JPA type | DB column type | Migration |
|---|---|---|---|---|
| `Request.stage` | `Stage` enum (`@Enumerated(EnumType.STRING)`) | `String` | `VARCHAR` | None |
| `ReleaseFlow.currentStage` | `Stage` enum (`@Enumerated(EnumType.STRING)`) | `String` | `VARCHAR` | None |

Existing persisted values (`"SIT"`, `"UAT"`, `"PROD"`) remain valid and unchanged. The change is purely in the Java layer.

### Release Flow Identity Model (unchanged from current code)

The `DA_RELEASE_FLOW` table retains its current uniqueness model. Evidence:

- `ReleaseFlow.java:34` — `@Index(name = "IDX_RF_PROJECT_RELEASE", columnList = "project_id, normalized_release_id", unique = true)`.
- `ORACLE_CURRENT_SCHEMA.sql:25` — `CREATE UNIQUE INDEX IDX_RF_PROJECT_RELEASE ON DA_RELEASE_FLOW (project_id, normalized_release_id)`.
- `ReleaseFlowRepository.java:19` — `findByProjectIdAndNormalizedReleaseIdAndArchivedAtIsNull(...)` is the only lookup path used by import.
- `ImportService.java:155–177` — `findOrCreateReleaseFlowByIdentifier` looks up by `(projectId, normalizedReleaseId)` and reuses the existing row when present.

**Key properties of this model in v3:**

- The unique key is **global across all agents**, not agent-scoped. `ReleaseFlow` has no `agent` column and does not gain one in v3.
- **Agent partitioning is a runtime consequence of stage-prefix generation**, not a schema invariant. Deployment Agent generates release IDs like `sit-<project>-0001`, Testing Agent uses `uat-<project>-0001`, Build Agent uses `dev-<project>-0001`. These normalize to distinct strings, so `(projectId, normalized_release_id)` collisions across agents do not occur in practice.
- **Duplicate upload behavior** is existing `ImportService` upsert semantics: a second upload with the same release identifier merges new Requests into the existing ReleaseFlow row. This behavior is shared across all three agents — Deployment Agent repeat SIT uploads, Build Agent repeat DEV uploads, and Testing Agent repeat UAT uploads all behave identically.
- **"One ReleaseFlow belongs to one agent" is a runtime invariant**, not a schema invariant. It is enforced by three layers: (1) stage-prefix partitioning in `ReleaseFlowService.create`, (2) controllers forcing the agent on every write path, and (3) each agent's module-private Stage enum vocabulary. The schema itself does not prevent a ReleaseFlow from having Requests with mixed `agent` values; the runtime mechanisms above ensure it never happens in practice.

**Why not strict agent-scoped uniqueness (Option B rejected):**

An alternative was to add `agent` to `ReleaseFlow` and change the unique key to `(project_id, normalized_release_id, agent)`. This was considered and rejected because:

1. The current stage-prefix mechanism already provides effective agent partitioning without a schema change.
2. Strict agent-scoped uniqueness would require a Flyway migration (drop index, add column, backfill existing rows to `deployment-agent`, rebuild index) plus signature changes across every query method that joins on `(projectId, normalizedReleaseId)` — roughly 7–10 repository methods plus `ImportService` plumbing.
3. The stricter model's only real defense is against a hypothetical future where two agents choose overlapping stage prefixes. Discipline at the `StagePipeline` declaration layer is a cheaper control.

If a future agent genuinely needs to share a stage prefix with another agent (e.g. two different agents that both want to use `"UAT"` as their own stage), that is the point at which Option B becomes unavoidable. It is explicitly flagged as a future migration, not as technical debt.

### Stage Value Inventory

| Stage string | Owning module | Persisted by |
|---|---|---|
| `"SIT"` | Deployment Agent (`DeploymentStage.SIT`) | Deployment Agent controllers |
| `"UAT"` | Deployment Agent (`DeploymentStage.UAT`), Testing Agent (`TestingStage.UAT`) | Deployment Agent and Testing Agent controllers |
| `"PROD"` | Deployment Agent (`DeploymentStage.PROD`) | Deployment Agent controllers |
| `"DEV"` | Build Agent (`BuildStage.DEV`) | Build Agent controllers |

Two agents may legitimately persist the same stage String (`"UAT"`) — they are different Java types (`DeploymentStage.UAT` vs `TestingStage.UAT`) that happen to share a product meaning. The `Request.agent` column is the primary discriminator; Platform Core never needs to distinguish stage vocabularies and treats them as opaque strings.

### Agent Column Usage

| Value | Meaning | Visibility |
|---|---|---|
| `"deployment-agent"` | Request created through Deployment Agent | Deployment Agent workspace only |
| `"testing-agent"` | Request created through Testing Agent | Testing Agent workspace only |
| `"build-agent"` | Request created through Build Agent | Build Agent workspace only |
| `null` | Legacy data (pre-agent-column) | **Invisible from every agent workspace** under PL-6. Rows remain in the database untouched. Will become visible again only when the platform-level Global View ships (R-13). No backfill migration is part of this delivery. |

### Stitching Scope

Stitching (`listStitchedSummaries`, `getStitchedDetail`, `ReleaseFlowFamilyKey`) lives entirely inside the Deployment Agent Module (PL-5) and operates only on Deployment Agent Persisted Release Flows. There is no notion of "Build Agent stitching" or "cross-agent stitching" in the v3 architecture:

- Two Build Agent uploads sharing a release identifier (e.g. both `DEV-1234`) upsert into the same `ReleaseFlow` row via `ImportService`; the Build summary stays grouped at the workflow level and repeated DEV attempts are exposed in the detail page.
- A user who wants to see `DEV → SIT → UAT → PROD` for a single release either switches between agent workspaces manually, or waits for the platform-level Global View (R-13).
- `ReleaseFlowFamilyKey` never sees `DEV` or any non-Deployment stage token, so the v2 "conservative DEV stripping" regex logic is deleted rather than extended.

---

## Integration Architecture

No changes to external integrations. All three agents continue to reuse the same integration points:

- **Jenkins** and **Ansible Tower** — same fire-and-forget submission pattern through the existing platform `AutoExecutionAdapter` implementations.
- **Authentication Provider** — same session-based login through the existing `TeamBookAuthenticationProvider` interface.
- **Access Grant Resolution** — same deny-by-default lookup (shared across agents per inherited decision).
- **Audit Storage** — same `REQUIRES_NEW` propagation with per-agent `agentName` derived from `scope.agent()` (PL-11).

Internal wiring change: the Jenkins and Ansible adapters are platform components called from platform `AutoExecutionService`. Agent context (which agent initiated the call) is passed through as a String argument, not as a domain coupling.

---

## API Boundaries

### Route Prefix Inventory

| Prefix | Owner | Contents |
|---|---|---|
| `/api/platform/*` | Platform Core (**new in v3**) | `/auth/{login,logout,me}`, `/audit-logs`, `/config`, `/config/components`, `/access-grants`, `/access-grants/*`, `/upload/template` |
| `/api/deployment-agent/*` | Deployment Agent Module | Release flows, upload, tasks, decisions (all existing domain endpoints, scoped to `agent = "deployment-agent"`) |
| `/api/testing-agent/*` | Testing Agent Module | Release flows, upload, tasks, decisions (scoped to `agent = "testing-agent"`) |
| `/api/build-agent/*` | Build Agent Module (**new in v3**) | Release flows, upload, tasks, decisions (scoped to `agent = "build-agent"`) |

### Breaking Route Changes (v2 → v3)

| v2 route | v3 route | Reason |
|---|---|---|
| `POST /api/deployment-agent/auth/login` | `POST /api/platform/auth/login` | Platform capability extracted from Deployment Agent prefix (PL-2) |
| `POST /api/deployment-agent/auth/logout` | `POST /api/platform/auth/logout` | Same |
| `GET /api/deployment-agent/auth/me` | `GET /api/platform/auth/me` | Same |
| `GET /api/deployment-agent/audit-logs` | `GET /api/platform/audit-logs` | Same |
| `GET /api/deployment-agent/config` | `GET /api/platform/config` | Same |
| `POST /api/deployment-agent/config` | `POST /api/platform/config` | Same |
| `GET /api/deployment-agent/config/components` | `GET /api/platform/config/components` | Same |
| `POST /api/deployment-agent/config/components` | `POST /api/platform/config/components` | Same |
| `DELETE /api/deployment-agent/config/components/{id}` | `DELETE /api/platform/config/components/{id}` | Same |
| `GET /api/deployment-agent/access-grants` | `GET /api/platform/access-grants` | Same |
| `POST /api/deployment-agent/access-grants` | `POST /api/platform/access-grants` | Same |
| `PATCH /api/deployment-agent/access-grants/{employeeId}` | `PATCH /api/platform/access-grants/{employeeId}` | Same |
| `POST /api/deployment-agent/access-grants/{employeeId}/suspend` | `POST /api/platform/access-grants/{employeeId}/suspend` | Same |
| `POST /api/deployment-agent/access-grants/{employeeId}/reactivate` | `POST /api/platform/access-grants/{employeeId}/reactivate` | Same |
| `GET /api/deployment-agent/access-grants/directory` | `GET /api/platform/access-grants/directory` | Same |
| `GET /api/deployment-agent/templates/*` | `GET /api/platform/upload/template` | Same |

Session cookies (`JSESSIONID`) are preserved across the route move because the cookie `Path` attribute is `/` (ref: `application.properties:15` and the absence of any `server.servlet.session.cookie.path` override).

`SecurityConfig.java:36` must be updated in the same commit that moves `AuthController` — the `permitAll()` whitelist currently hard-codes the v2 login route. Failing to update it leaves the login flow inescapable.

### Cutover Strategy: Hard Cutover (no route aliases)

**Decision:** The v2 → v3 route migration is a **hard cutover**. Old routes under `/api/deployment-agent/auth/*`, `/api/deployment-agent/audit-logs`, `/api/deployment-agent/config`, `/api/deployment-agent/access-grants`, and `/api/deployment-agent/templates/*` are **removed in the same commit** that adds the corresponding `/api/platform/*` routes. The shared template download now lives at `/api/platform/upload/template`. There is no deprecation window and no dual-mounting.

**Alternatives considered:**
- **Soft cutover with route aliases** (controllers mounted at both the v2 and v3 paths for N releases, old paths return a deprecation header) — rejected. The only known external consumers of the v2 routes are (a) the frontend in this same repository and (b) the `SecurityConfig.java:36` whitelist. Both update in the same commit. There is no external client base to protect with a deprecation window.
- **Frontend-only migration first** (backend adds `/api/platform/*`, keeps `/api/deployment-agent/*` as legacy, frontend migrates, then old routes removed in a follow-up) — rejected. Introduces a transient "two sources of truth" state in the backend that must itself be tested, without delivering any benefit because the frontend-backend handshake is coordinated inside the same delivery.

**Rationale:**
- The product has not been publicly released. Testing Agent is still in internal testing (Q4). There are no external bookmarks, no third-party API consumers, and no documented integration points outside this repository.
- A hard cutover keeps the codebase in exactly one valid shape at any point in time. Any code reviewer or subsequent change can trust that if a route exists at `/api/platform/auth/login`, it does **not** also exist at `/api/deployment-agent/auth/login`.
- The frontend, the `SecurityConfig` whitelist, the integration tests, and the API documentation all migrate together in the same set of commits.

**Consequences:**
- Anyone operating outside this repository who had bookmarked the v2 routes (for example during developer exploration) must update their bookmarks after the delivery lands. This is an acceptable cost given the single-codebase consumer base.
- If a future consumer is discovered that depends on v2 routes, it becomes a one-time migration task for that consumer, not a change to this delivery's rollout plan.

### Agent Domain Endpoint Shape

All three Agent Modules expose the same endpoint shape:

- `GET /{prefix}/release-flows` — list flows (scoped to the owning agent)
- `GET /{prefix}/release-flows/{id}` — flow detail; Deployment Agent additionally honors `?linked=<ids>` for stitched detail (BA-2: Build Agent and Testing Agent ignore `?linked=`)
- `POST /{prefix}/upload` — Excel upload; forces `agent = <owning agent>` and the agent's permitted stage server-side
- `GET /{prefix}/tasks/{id}` — task detail
- `PATCH /{prefix}/tasks/{id}/input` — edit task input
- `POST /{prefix}/tasks/{id}/start-manual` — start manual execution
- `POST /{prefix}/tasks/{id}/record-result` — record manual result
- `POST /{prefix}/tasks/{id}/submit-auto` — submit auto execution
- `POST /{prefix}/tasks/{id}/rerun` — rerun
- `GET /{prefix}/tasks/{id}/history` — execution history
- `POST /{prefix}/decisions/{decisionId}/apply` — apply decision

Full request/response shapes, status codes, and validation rules are design-layer concerns and live in the Build Agent design document. This section only establishes the route prefix discipline.

### Controller Boundary Responsibilities

Every agent controller method on an ID-bearing endpoint performs the five-step sequence from PL-10:
1. Force `agent` (and stage where applicable) server-side.
2. Invoke `AgentBoundaryGuard.assertXxx(...)`.
3. Convert incoming stage String to the module-local Stage enum (if the endpoint accepts a stage parameter).
4. Delegate to a Platform Core service. Controllers do NOT pass `StagePipeline` anywhere — `ReleaseFlowProgressionService` resolves pipelines internally via `StagePipelineRegistry` (see PL-4).
5. Translate the platform response back into the agent's view shape.

---

## Security Architecture

### Changes in v3

1. **`AgentBoundaryGuard` becomes a Platform Core component (PL-9).** Used by every Agent Module's controllers on every ID-bearing endpoint. A mismatch throws a `NotFoundException` mapped to HTTP 404.
2. **`AuditLoggerService.log` derives `agentName` dynamically (PL-11).** Platform-scoped capability events may still fall back to `platform`.
3. **`SecurityConfig.java:36` whitelist updates** to `.requestMatchers("/api/platform/auth/login").permitAll()`.
4. **Deployment Agent loses its implicit global scope (PL-6).** Its summary list query is scoped by `agent = "deployment-agent"`; legacy null-agent rows become invisible until the Global View ships (R-13).

### Unchanged

- Session management policy (`SessionCreationPolicy.IF_REQUIRED`).
- Filter chain ordering: `SessionAuthFilter` → `HeaderAuthFilter` → `UsernamePasswordAuthenticationFilter`.
- Access Grant model (deny-by-default).
- Scope grants (`Application + SNOW Group`).
- RBAC / permission enforcement style (imperative inside controller methods).
- Optimistic locking via `@Version`.
- Audit isolation via `REQUIRES_NEW` transaction propagation.
- Header auth fallback (`HeaderAuthFilter`), controlled by `app.auth.header-fallback-enabled`.

### Design Rationale (Preserved from v2)

- **No Spring Security filter for boundary checks.** Filters run before the controller and do not cleanly have entity-level access. The guard runs at the same layer as the business operation it protects.
- **No `@PreAuthorize`.** The codebase performs authorization imperatively; introducing `@PreAuthorize` only for boundary checks would fragment the authorization style.

---

## Performance and Resilience Impact

The Platform Core refactor is expected to be performance-neutral; no hot path changes its asymptotic cost. Two specific concerns are worth calling out explicitly.

### String vs Enum Stage Comparisons

Platform services now compare stages using `String.equals` instead of enum reference equality. The additional cost per comparison is small (char-array compare over ~3–4 characters) and is not on any hot path that previously bottlenecked on stage comparison. `ReleaseFlowAggregation` and `latestRequestsPerStage` now iterate over a Set of distinct stage strings derived from the request list, rather than over a fixed `Stage.values()` array; for flows with a small number of stages this is a minor reallocation per call and is well within noise.

### `AgentBoundaryGuard` Overhead

**Actual transaction placement in the current codebase:**

- `application.properties:7` sets `spring.jpa.open-in-view=false`. There is no request-scoped Hibernate session.
- `@Transactional` lives on service methods, not on controllers. (Grepping `/web/controller/` confirms no controller-level `@Transactional` annotations.)

This means the guard's repository lookup and the subsequent service method's repository lookup execute in **two independent transactions**, each opening its own Hibernate session. The Hibernate first-level cache is **not** shared between them — the guard's lookup does not "warm" the service's lookup.

**Actual cost per protected endpoint:**

- The guard adds exactly **one additional indexed database round trip** (`SELECT ... FROM DA_TASK WHERE id = ?` followed by a reference navigation through `DA_REQUEST.agent`, or the equivalent for flow-level / request-level assertions).
- Task ID and request ID lookups hit the primary key index; flow-level assertions hit `IDX_RF_PROJECT_RELEASE` or the PK. Observed latency for single-row indexed lookups against Oracle in the existing codebase is well under 2 ms.
- For task mutation endpoints, the downstream service does reload the same task in its own transaction. This is a real second lookup, not a cache hit — but it is still a single indexed PK lookup and is not on any path that previously bottlenecked on task read latency.

**Assumption flagged for design validation:** The marginal cost figure above assumes Oracle's buffer cache absorbs the back-to-back lookups into effectively the same disk read. If load testing reveals the doubled round trip is measurable, two mitigations are available: (a) move `@Transactional` onto controller methods so guard + service share a session (non-trivial; changes transaction semantics for error handling); or (b) have the guard return the loaded entity and refactor `TaskService` to accept a pre-loaded entity on its mutation paths (invasive; changes platform service signatures). Neither mitigation is part of this delivery. Design should define a controller-level integration benchmark to confirm the overhead is acceptable before either mitigation is considered.

For flow-level detail, the guard performs `releaseFlowRepository.findById` + `requestRepository.findByReleaseFlowIds` lookups, and the downstream handler then calls `getById` / `findRequestsForFlow`. Under the current transaction model these are four independent lookups, not two cached hits. Same framing as above: cheap in absolute terms, to be validated under load in design.

### Stitching Footprint

Stitching's cost is unchanged — it simply moves packages from platform to `agents/deployment/domain/`. Testing Agent and Build Agent never invoke it, so their summary-list cost drops slightly (one less grouping pass) compared to v2 Testing Agent's accidental stitched call.

### Write Path

Unchanged. All writes delegate to the same platform services as before the refactor. No new transaction boundaries, no new lock contention.

### Observability

Recommended additions (tracked at the design layer):
- A counter for `AgentBoundaryGuard` rejections (by agent and endpoint) to detect cross-agent probing.
- A counter for `StagePipeline` terminal transitions (by agent and terminal stage) to catch misconfigured pipelines.
- Continued use of the existing operational logging in domain services.

### Baseline Capacity

No change to baseline capacity targets. The system's hot paths (decision application, task state transitions, summary list rendering) are untouched in semantic terms; the refactor only changes where the code lives and what types it uses.

---

## Constraints and Assumptions

| # | Constraint | Source |
|---|-----------|--------|
| C1 | No new database tables. No new columns. No Flyway migration. JPA attribute type changes for `Request.stage` and `ReleaseFlow.currentStage` from enum to String are backward-compatible because the DB column is already `VARCHAR`. | PL-3 |
| C2 | Agent Modules depend only on Platform Core. They do not import from each other. | PL-2 |
| C3 | Platform Core does not reference any individual `AgentId` constant by value; branches on specific agents are forbidden outside controllers. | PL-2 |
| C4 | Each Agent Module declares its own Stage enum and its own `StagePipeline` `@Component` reporting its own `agentId()`. Platform Core resolves pipelines via `StagePipelineRegistry` — controllers never pass pipelines as method parameters. | PL-3, PL-4 |
| C5 | Stitching is implemented only inside Deployment Agent Module. | PL-5 |
| C6 | Deployment Agent summary is scoped by `agent = "deployment-agent"`. No global view at the Deployment Agent layer. | PL-6 |
| C7 | Legacy `Request` rows with `agent IS NULL` are invisible from every agent workspace until the Global View ships. No backfill migration. | PL-6 |
| C8 | `ReleaseFlowListItemDto` uses `Map<String, RequestStatus> stageStatuses` and `Set<String> stagesPresent`; no positional per-stage fields. | PL-7 |
| C9 | Every Agent Module controller invokes `AgentBoundaryGuard` on ID-bearing endpoints. Boundary violations return HTTP 404. | PL-9 |
| C10 | Build Agent upload forces `agent = "build-agent"` and `stage = "DEV"` server-side. | BA-1, spec BFR-14 |
| C11 | `BuildStage.DEV` is terminal; `BuildStagePipeline.next("DEV")` returns `Optional.empty()`. Build Agent never auto-advances across agent boundaries. | BA-1 |
| C12 | Build Agent does not call `DeploymentStitchingService`. `?linked=` is silently ignored. | BA-2 |
| C13 | Platform capability routes move to `/api/platform/*`. `SecurityConfig.java` whitelist for the login route updates in the same commit. | PL-2, API Boundaries |
| C14 | Access grants are shared across agents; `AccessScope` does not gain an `agent` dimension. | Inherited |
| C15 | Agent identity strings are defined as constants at backend (`AgentId`) and frontend (`frontend/src/config/agentId.ts`). No string literals in controllers, services, or views. | Inherited |
| C16 | Authorization style is imperative validation inside controller methods. No `@PreAuthorize`. | PL-9 |

---

## Impact Analysis

### Platform Core Refactor (Part A)

**Create:**
- `platform/domain/StagePipeline.java` interface (with `agentId()` routing key; throws on unknown stages).
- `platform/domain/StagePipelineRegistry.java` @Component (Platform Core; auto-injects all pipelines, maps by `agentId()`, fail-loud on duplicate / missing).
- `platform/web/security/AgentBoundaryGuard.java` (promoted from Build-Agent-only helper).
- `platform/web/shared/` package housing `AuthController`, `AuditLogController`, `ConfigurationController`, `AccessGrantController`, `TemplateDownloadController` at their new `/api/platform/*` routes.
- Frontend `platform/api/platformClient.ts` and 5 capability API modules bound to it.
- Frontend `platform/composables/createAgentWorkspace.ts`, `createReleaseFlowStore.ts`, `createReleaseFlowApi.ts`.
- Frontend `platform/components/AgentSummaryView.vue`, `AgentDetailView.vue`.
- ArchUnit tests asserting agent/platform dependency direction.

**Modify:**
- `ReleaseFlowService` — remove stitching methods; change signatures to `String stage`.
- `ReleaseFlowProgressionService.progressAfterDecision(String taskId)` — **signature unchanged** (ref: `ReleaseFlowProgressionService.java:49`). Constructor gains `StagePipelineRegistry` dependency; body at line 72 replaces `currentStage.next() == null` with `pipeline.isTerminal(currentStage)` via registry lookup. No caller changes across 5 call sites.
- `ReleaseFlowAggregation` — iterate over observed stage strings instead of `Stage.values()`.
- `ReleaseFlow` entity — `currentStage` attribute `Stage` → `String`.
- `Request` entity — `stage` attribute `Stage` → `String`.
- `ReleaseFlowListItemDto` — positional stage fields → `Map<String, RequestStatus> stageStatuses` + `Set<String> stagesPresent`.
- `AuditLoggerService.log` — derive `agentName` from `scope.agent()`; retain guarded `platform` fallback for platform-scoped events.
- `AgentId` — add `BUILD_AGENT` constant.
- `SecurityConfig.java:36` — change whitelist to `/api/platform/auth/login`.
- Frontend `LoginView.vue` and all hard-coded auth URL references.

**Delete:**
- `contracts/enums/Stage.java`.
- `ReleaseFlowService.listStitchedSummaries`, `getStitchedDetail`.
- `domain/releaseflow/ReleaseFlowFamilyKey.java` (logic moves to Deployment Agent Module).

### Deployment Agent Module Migration (Part A)

**Create:**
- `agents/deployment/domain/DeploymentStage`, `DeploymentStagePipeline`.
- `agents/deployment/domain/ReleaseFlowFamilyKey` (moved from platform).
- `agents/deployment/domain/DeploymentStitchingService`.
- `agents/deployment/web/Deployment*Controller` (four controllers, migrated from `web/controller/`).
- `frontend/src/agents/deployment/index.ts`.

**Delete:**
- `web/controller/ReleaseFlowController`, `UploadController`, `TaskController`, `DecisionController` (replaced by agent-module versions).
- Legacy shared frontend API wrappers (`frontend/src/api/client.ts`, `releaseFlows.ts`, `tasks.ts`, `upload.ts`) removed in favor of `frontend/src/api/platformClient.ts`, `frontend/src/agents/*/api.ts`, and the shared workspace factory.
- `frontend/src/stores/releaseFlow.ts`, `task.ts`.
- `frontend/src/views/ReleaseFlowSummaryView.vue`, `ReleaseFlowDetailView.vue`.

### Testing Agent Module Migration (Part A)

**Create:**
- `agents/testing/domain/TestingStage`, `TestingStagePipeline`.
- `agents/testing/web/Testing*Controller` (four controllers, migrated from `web/controller/`). Each gains an `AgentBoundaryGuard` invocation.
- `frontend/src/agents/testing/index.ts`.

**Delete:**
- `web/controller/TestingAgentReleaseFlowController`, `TestingAgentTaskController`, `TestingAgentUploadController`, plus whatever decision controller Testing Agent currently uses.
- `frontend/src/api/testingAgentClient.ts`, `testingAgentReleaseFlows.ts`, `testingAgentTasks.ts`, `testingAgentUpload.ts`.
- `frontend/src/stores/testingAgentReleaseFlow.ts`.
- `frontend/src/views/TestingAgentSummaryView.vue`, `TestingAgentDetailView.vue`.

### Build Agent Module (Part B)

**Create:**
- `agents/build/domain/BuildStage`, `BuildStagePipeline`.
- `agents/build/web/BuildReleaseFlowController`, `BuildUploadController`, `BuildTaskController`, `BuildDecisionController`.
- `frontend/src/agents/build/index.ts` (~20 lines).

**Modify:**
- `frontend/src/platform/config/agentRegistry.ts` — add Build Agent entry.
- `frontend/src/router/index.ts` — add `/wwa/build-agent` and `/wwa/build-agent/release-flows/:id` routes.

### Scope Note

Design-layer artifacts (file-level class signatures, test matrices, LOC estimates) live in the Build Agent design document. This architecture document establishes the structure and constraints; the design document decomposes them into concrete coding tasks.

---

## Pending External Dependencies

No new external dependencies introduced by v3. Existing dependencies unchanged:

1. Team Book adapter contract (pending, not Build Agent specific).
2. Jenkins / Ansible credentials (runtime config).
3. Enterprise directory enrichment (pending consideration).

---

## Open Architecture Risks

v3 replaces the v2 risk list in full. Obsolete v2 risks are listed at the end for traceability.

- **R-01** — **Platform refactor scope is large.** Part A touches many files across backend domain, backend web, frontend composables, and frontend views. Mitigation: land Platform Core changes on their own commits before any Agent Module migration; keep tests green after each step; use ArchUnit fitness functions to catch boundary regressions immediately.
- **R-02** — **Breaking route change to platform capabilities.** Moving `/api/deployment-agent/auth/*` / `/audit-logs` / `/config` / `/access-grants` / `/templates/*` to `/api/platform/*` (with shared template download now at `/api/platform/upload/template`) invalidates any external consumer or bookmark pointing at the v2 routes. Mitigation: for this delivery the only known consumers are the frontend and the `SecurityConfig` whitelist (both updated in the same commit). Any external integration discovered later becomes an additional migration task.
- **R-03** — **`SecurityConfig.java:36` whitelist forgot-to-update risk.** If the commit that moves `AuthController` does not also update the `permitAll()` matcher, login becomes unreachable. Mitigation: an integration test that POSTs to `/api/platform/auth/login` as an unauthenticated user and asserts a 2xx response blocks the commit if the whitelist is wrong.
- **R-04** — **Legacy null-agent data becomes invisible** (PL-6 consequence). Users of the v2 Deployment Agent summary who rely on seeing pre-agent-column historical rows lose that visibility until the Global View ships. Mitigation: document the gap in the release notes; schedule the Global View as a near-term follow-up (replaces v2's R-13).
- **R-05** — **`StagePipelineRegistry` missing-agent lookup at runtime.** If a `Request` row carries an `agent` value with no corresponding `StagePipeline` @Component (configuration drift: new agent added without a pipeline, or data-integrity drift: a stale test fixture writes an unrecognized agent string), `progressAfterDecision` throws `IllegalStateException` and the transaction rolls back, preventing progression for that flow. Mitigation: (a) ArchUnit fitness test asserting every `AgentId` constant has a matching `StagePipeline` implementation; (b) integration test that writes each `AgentId` to a `Request` row and exercises `progressAfterDecision`; (c) Spring Boot startup check in `StagePipelineRegistry` constructor verifying at least one pipeline is registered. Startup is fail-loud on duplicate `agentId()` values.
- **R-06** — **String-typed stages weaken type safety in platform services.** A typo in a stage String passed from a controller into a platform service will not be caught at compile time. Mitigation: controllers are the only layer that constructs stage Strings, and they always derive them from the module's Stage enum via `.name()`; enum-based construction catches typos at the controller layer. An ArchUnit test forbids string literals of stage names in platform code.
- **R-07** — **Testing Agent migration has zero shipping users** (Q4), so no runtime regression risk. But the Testing Agent codebase is still live in development; any other branch carrying changes to `TestingAgent*Controller` will have merge conflicts with the file moves. Mitigation: coordinate merge order with any active Testing Agent branches.
- **R-08** — **`AuditLoggerService` `agentName` historical rows** (carried forward from v2 R-12). Pre-refactor audit rows keep their incorrect `agentName` values. Forward-only fix.
- **R-09** — **Frontend `createAgentWorkspace` factory coverage gap.** Deployment Agent's current hand-written summary and detail views contain subtle behaviors (column ordering, filter persistence, detail tab state) that the factory must reproduce. Mitigation: migrate Deployment Agent frontend after the factory passes Testing Agent and Build Agent first (simpler agents validate the factory), then address any Deployment-Agent-specific behaviors as factory config options before migrating the Deployment Agent view.
- **R-10** — **Pre-existing cross-agent task mutation gap** in v2 Testing Agent (carried forward from v2 R-08). Closed as a side effect of PL-9 because Testing Agent controllers now invoke `AgentBoundaryGuard` in the migrated form. No separate remediation task is needed.

### Obsolete v2 Risks

The following v2 risks no longer apply in v3 and are removed from the tracking list:

- v2 R-02 (`Stage.values()` iteration tolerance) — the enum is deleted; iteration now uses observed stage strings.
- v2 R-06 (Deployment/Testing stage dropdowns must not include DEV) — stage dropdowns are per-agent by construction under PL-8.
- v2 R-07 (family key regex edge case for project names containing `dev-`) — family key never sees DEV; the DEV-stripping regex is not written.
- v2 R-09 (additive DTO fields must not leak into Deployment/Testing renderers) — no additive fields; DTO uses a generic Map.
- v2 R-11 (users cannot see downstream stages from within Build Agent) — this is now the baseline behavior of all peer agents; not a risk, just the PL-6 consequence.
- v2 R-13 (Deployment Agent summary contains empty-column rows) — removed by PL-6; Deployment Agent no longer shows other-agent rows.
- v2 R-14 (cross-agent stitching does not happen at the summary layer) — no longer a deferred feature; stitching is by design a Deployment-Agent-internal concept under PL-5.
