# System Architecture: Deployment Agent + Platform Core

**Date:** 2026-04-11 (Agent Module Boundary section added; Deployment Agent sections unchanged from 2026-03-26)
**Status:** Deployment Agent MVP implemented; Agent Module Boundary pattern delivered with Build Agent v3 — see `build-agent-architecture.md`
**Source:** `spec.md` (Deployment Agent primary), repository code (Deployment Agent validation), `build-agent-architecture.md` (Agent Module Boundary definition and rationale)

**Scope note:** This document is the platform-level architecture reference. It was originally written as Deployment Agent's architecture (before the multi-agent pattern existed) and still contains the Deployment Agent sections (Technology Stack, High-Level Architecture, Constraints, Data/State/Integration/API/Security). Starting with Build Agent v3, it additionally carries the **Agent Module Boundary** section that defines how any Agent Module plugs into Platform Core. Future agents 4–N should read the Agent Module Boundary section first; the Deployment Agent sections below serve as the reference implementation of that pattern.

---

## Platform Context

WWA Agent Workspace Hub hosts multiple peer Agent Workspaces on top of a shared Platform Core. The operating model is:

```
FinBlock  →  WWA Agent Workspace Hub (`WWA`)  →  Platform Core + Agent Modules
                                                   ├── Deployment Agent  (SIT → UAT → PROD)
                                                   ├── Testing Agent     (UAT, terminal)
                                                   └── Build Agent       (DEV, terminal)
                                                   (+ future agents 4 through N)
```

- **FinBlock** provides one stable entry link to WWA.
- **WWA Agent Workspace Hub** owns authentication, top-level navigation, platform access management, and platform-level audit. These capabilities live in Platform Core and are served under the `/api/platform/*` route prefix.
- **Agent Modules** own agent-specific release-flow workspaces (release orchestration for Deployment Agent, UAT testing for Testing Agent, DEV build workflow for Build Agent, etc.). Each Agent Module is self-contained, depends only on Platform Core, and is served under its own `/api/<agent-key>/*` route prefix.

Deployment Agent was historically the first (and only) workspace in this repository and was structurally privileged — platform capability routes were mounted under `/api/deployment-agent/*`, and its summary view was an implicit "global view" of all flows. As of Build Agent v3 (Q2 2026), Deployment Agent is a peer of the other agents: capability routes moved to `/api/platform/*`, and its summary view scopes by `agent = "deployment-agent"` like every other agent.

Shared capabilities (Audit Log, Access Management, Configuration Management, Template Management) are owned by Platform Core and reached by every agent's UI via the platform API client. Their ownership boundary is documented in `docs/00-context/wwa-product-positioning.md`.

---

## Agent Module Boundary

**Audience:** Anyone adding a new Agent Workspace, or anyone needing to understand where Platform Core ends and an Agent Module begins.

**Canonical source:** `build-agent-architecture.md` §Architecture Decisions (PL-1 through PL-11 and BA-1 through BA-3). That document establishes the pattern in full detail and records the rationale for each decision. This section summarizes the contract that every Agent Module must honor.

### Package Structure

**Backend** (`src/main/java/com/wwa/deploymentagent/`):

```
com.wwa.deploymentagent/
├── platform/                          ← Platform Core (agent-agnostic)
│   ├── domain/
│   │   ├── StagePipeline.java         ← Interface; each agent provides an impl
│   │   ├── releaseflow/               ← ReleaseFlowService (list/get by agent, String stage)
│   │   ├── task/                      ← TaskService, TaskStateMachine, RecordResultService
│   │   ├── fileimport/                ← ImportService, ExcelParserService
│   │   ├── decision/                  ← DecisionEngine, ReleaseFlowProgressionService
│   │   ├── execution/                 ← AutoExecutionService, Jenkins/Ansible adapters
│   │   ├── audit/                     ← AuditLoggerService
│   │   ├── auth/                      ← AuthService, authentication-provider abstraction
│   │   └── configuration/             ← ConfigurationService
│   ├── contracts/                     ← Shared DTOs, AgentId constants, UserContext
│   │                                    (Request.stage and ReleaseFlow.currentStage
│   │                                    are String, not enum)
│   ├── web/
│   │   ├── security/                  ← AgentBoundaryGuard (used by every agent's
│   │   │                                 controllers), SessionAuthFilter, HeaderAuthFilter
│   │   └── shared/                    ← Platform capability controllers at /api/platform/*
│   │                                    (AuthController, AuditLogController,
│   │                                    ConfigurationController, AccessGrantController,
│   │                                    TemplateDownloadController)
│   └── errors/                        ← GlobalExceptionHandler, AppException hierarchy
│
└── agents/                            ← Agent Modules (peer, independent)
    ├── deployment/
    │   ├── domain/
    │   │   ├── DeploymentStage { SIT, UAT, PROD }
    │   │   ├── DeploymentStagePipeline        (SIT → UAT → PROD)
    │   │   ├── ReleaseFlowFamilyKey           (Deployment-Agent-internal)
    │   │   └── DeploymentStitchingService     (Deployment-Agent-internal)
    │   └── web/
    │       ├── DeploymentReleaseFlowController   (/api/deployment-agent/release-flows)
    │       ├── DeploymentUploadController
    │       ├── DeploymentTaskController
    │       └── DeploymentDecisionController
    ├── testing/
    │   ├── domain/
    │   │   ├── TestingStage { UAT }
    │   │   └── TestingStagePipeline              (UAT terminal)
    │   └── web/
    │       └── Testing*Controller (4 files)       (/api/testing-agent/*)
    └── build/
        ├── domain/
        │   ├── BuildStage { DEV }
        │   └── BuildStagePipeline                 (DEV terminal)
        └── web/
            └── Build*Controller (4 files)         (/api/build-agent/*)
```

**Frontend** (`frontend/src/`):

```
frontend/src/
├── platform/                          ← Platform Core (agent-agnostic)
│   ├── api/
│   │   ├── platformClient.ts          ← Axios instance, baseURL: /api/platform
│   │   └── auth.ts · audit.ts · config.ts · accessGrants.ts · templates.ts
│   ├── stores/                        ← user · audit · config · accessGrants (capability stores)
│   ├── composables/
│   │   ├── createAgentWorkspace.ts    ← Factory — the entry point for every Agent Module
│   │   ├── createReleaseFlowStore.ts  ← Generic Pinia store factory
│   │   └── createReleaseFlowApi.ts    ← Generic API module factory
│   ├── components/
│   │   ├── AgentSummaryView.vue       ← Generic summary view; reads stageStatuses map
│   │   ├── AgentDetailView.vue        ← Generic detail view; passes through ?linked=
│   │   └── UploadDialog.vue           ← Shared, props-driven upload component
│   ├── config/
│   │   ├── agentRegistry.ts           ← Home page / nav registry (one entry per agent)
│   │   └── agentId.ts                 ← Backend-mirrored AgentId constants
│   └── views/                         ← LoginView, WwaHomeView, WorkspaceLayout,
│                                         AuditLogView, ConfigAdminView, AccessManagementView,
│                                         TemplateManagementView
│
└── agents/                            ← Agent Modules (factory-driven; ~20 lines each)
    ├── deployment/index.ts            ← createAgentWorkspace({ key: 'deployment-agent', ... })
    ├── testing/index.ts               ← createAgentWorkspace({ key: 'testing-agent', ... })
    └── build/index.ts                 ← createAgentWorkspace({ key: 'build-agent', ... })
```

### Dependency Rules (Enforced by ArchUnit)

1. **Agent Modules depend only on Platform Core.** No class in `agents/<name>/` may import from any other `agents/*` package.
2. **Platform Core does not depend on any Agent Module.** No class in `platform/*` may import from any `agents/*` package.
3. **Platform Core does not bind to any per-agent Stage enum.** `Request.stage` and `ReleaseFlow.currentStage` are `String` at the JPA and service layer. Per-agent Stage enums exist only inside Agent Modules.
4. **Platform Core does not branch on specific `AgentId` values.** Constructs like `if (agentId.equals(BUILD_AGENT))` are forbidden outside controllers.
5. **Stage string literals (`"SIT"`, `"UAT"`, `"PROD"`, `"DEV"`, ...) must not appear inside Platform Core code.** They live only inside Agent Modules at the controller translation boundary.
6. **REST controllers live in exactly two places:** `platform/web/shared/` (platform capability routes at `/api/platform/*`) or `agents/<name>/web/` (per-agent routes at `/api/<agent-key>/*`). Nowhere else.

These rules are enforced by an ArchUnit fitness-function test class (`AgentModuleBoundaryTest`). The exact rule definitions are in `build-agent-design.md` §7.

### Required Interfaces for Every Agent Module

**Backend contract (every Agent Module must provide):**

1. A Stage enum at `agents/<name>/domain/<Agent>Stage.java` declaring the agent's stage vocabulary. Any number of values (typically 1 for single-stage agents, up to 3 for multi-stage agents). Module-private to the agent's package.
2. A `StagePipeline` `@Component` at `agents/<name>/domain/<Agent>StagePipeline.java` implementing the Platform Core interface. Encodes the agent's stage ordering. Terminal stages return `Optional.empty()` from `next(...)`.
3. One or more `@RestController` classes in `agents/<name>/web/` under a unique `/api/<agent-key>/*` prefix. Each ID-bearing endpoint must invoke `AgentBoundaryGuard.assertXxxBelongsToAgent(id, AgentId.<AGENT>)` before delegating. Each write endpoint must force `agent = "<agent-key>"` server-side, ignoring any client-supplied value.
4. Controllers pass the agent's own `StagePipeline` bean into any Platform Core service method that needs progression (currently only `ReleaseFlowProgressionService.progressAfterDecision`).

**Frontend contract:**

1. A single `frontend/src/agents/<name>/index.ts` file that calls `createAgentWorkspace(config)` with the agent's `{ key, name, apiBase, stages, supportsStitching, stageFilter }`.
2. A registration entry in `frontend/src/platform/config/agentRegistry.ts`.
3. Wiring of the factory-returned routes into the platform router.

### Shared Platform Capabilities

The following capabilities are owned by Platform Core and shared across every agent:

| Capability | Backend | Frontend |
|---|---|---|
| Authentication (login / logout / session) | `platform/web/shared/AuthController` at `/api/platform/auth/*` | `platform/api/auth.ts`, `LoginView.vue` |
| Configuration Management | `platform/web/shared/ConfigurationController` at `/api/platform/config` | `platform/api/config.ts`, `ConfigAdminView.vue` |
| Audit Log (cross-agent view) | `platform/web/shared/AuditLogController` at `/api/platform/audit-logs` | `platform/api/audit.ts`, `AuditLogView.vue` |
| Access Management | `platform/web/shared/AccessGrantController` at `/api/platform/access-grants` | `platform/api/accessGrants.ts`, `AccessManagementView.vue` |
| Template Download | `platform/web/shared/TemplateDownloadController` at `/api/platform/templates/*` | `platform/api/templates.ts` |
| Audit writes (`agentName` derived from `scope.agent()`) | `platform/domain/audit/AuditLoggerService` | — |
| Agent boundary enforcement | `platform/web/security/AgentBoundaryGuard` | — |
| Task execution primitives | `TaskService`, `TaskStateMachine`, `RecordResultService`, `AutoExecutionService`, `TaskExecutionHistoryService` | — |
| Release flow domain (list/get by agent, stage-agnostic) | `ReleaseFlowService`, `ReleaseFlowProgressionService`, `ImportService`, `ReleaseFlowAggregation`, `DecisionEngine` | `AgentSummaryView`, `AgentDetailView` (generic) |
| Upload dialog | — | `platform/components/UploadDialog.vue` |

These capabilities are agent-agnostic. They do not know about Deployment Agent, Testing Agent, Build Agent, or any future agent.

### Adding a New Agent: Checklist

To add the 4th, 5th, ..., Nth agent:

**Backend (~6 new files):**

1. Create `agents/<name>/domain/<Agent>Stage.java` — declare the agent's stage vocabulary.
2. Create `agents/<name>/domain/<Agent>StagePipeline.java` — `@Component` implementing `StagePipeline`.
3. Create `agents/<name>/web/<Agent>ReleaseFlowController.java` — scoped list + guarded detail.
4. Create `agents/<name>/web/<Agent>UploadController.java` — force `agent` (and stage if applicable) server-side.
5. Create `agents/<name>/web/<Agent>TaskController.java` — `AgentBoundaryGuard` on every endpoint.
6. Create `agents/<name>/web/<Agent>DecisionController.java` — passes `StagePipeline` into progression.
7. Add `<AGENT>` constant to `platform/contracts/AgentId.java`.

**Frontend (~1 new file + 3 small edits):**

8. Create `frontend/src/agents/<name>/index.ts` calling `createAgentWorkspace(config)`.
9. Add a registration to `frontend/src/platform/config/agentRegistry.ts`.
10. Add the factory-returned routes to the platform router.
11. Add the `<AGENT>` constant to `frontend/src/platform/config/agentId.ts`.

**Tests:**

12. Parameterize `StagePipelineContractTest` to include the new implementation.
13. Write `<Agent>*ControllerTest` integration tests using `BuildAgentControllerTest` as the template.
14. Write `<Agent>DataIsolationTest` covering cross-agent task/flow probes.
15. Confirm `mvn test` and `cd frontend && npm run build` pass without modifying any existing agent or platform code.

**Expected footprint per new agent:** roughly 400–600 backend LOC (enum + pipeline + 4 controllers + tests) and ~25 frontend LOC (the factory call plus registry entry). **No Platform Core changes. No schema changes. No edits to existing agents. No new Maven modules.**

### What a New Agent Is NOT Allowed to Do

- Modify any file under `platform/*` except adding entries to `AgentId.java`, `agentRegistry.ts`, `agentId.ts`, and the platform router.
- Import from another agent's package.
- Introduce a new shared DTO field or a new method on a Platform Core service without architecture review.
- Bypass `AgentBoundaryGuard` on any ID-bearing endpoint.
- Share a Stage enum type with another agent, even if the stage names coincide. (Two agents can both write `"UAT"` into `Request.stage`; they must use separate Java enum types.)
- Extend `ReleaseFlowFamilyKey` or `DeploymentStitchingService`. Stitching is Deployment-Agent-internal.

### When the Pattern Breaks Down

The Agent Module pattern assumes every agent is a **release-flow-style** agent that fits the shared `ReleaseFlow → Request → Task → TaskExecutionHistory` domain model. An agent that is fundamentally different in shape (e.g. a query-only agent, a dashboard agent, a data-lineage agent) does not fit this pattern and should not be forced into it. Such agents are expected to be introduced through a separate platform-level discussion; they may share the platform shell, auth, and audit, but not the release-flow domain.

Similarly, if a future agent legitimately needs to share a stage prefix with another agent (e.g. two agents both claiming `"UAT"` as their primary stage), the current release-flow identity model — which relies on stage-prefix partitioning of `(project_id, normalized_release_id)` — no longer provides agent partitioning by accident. That scenario would force a migration to add `agent` to `DA_RELEASE_FLOW`'s unique key. The rationale for deferring that migration is in `build-agent-architecture.md` §Data Architecture under "Why not strict agent-scoped uniqueness".

### Historical Note

Before Build Agent v3 (Q2 2026), this repository treated Deployment Agent as the sole agent. Auth, audit, config, and access-grants were mounted under `/api/deployment-agent/*` because it was the only available prefix. Testing Agent was added as the second agent using a "parallel store + copy-paste" pattern that did not scale. Build Agent introduced the Agent Module pattern described here, and Deployment Agent and Testing Agent were migrated into it in the same delivery. The canonical account of the migration, its decision rationale, its risks, and its ArchUnit fitness functions is `build-agent-architecture.md` v3. Future agents should take that document as the authoritative example and read this section for the platform-level contract.

---

## Overview

Deployment Agent is a controlled, human-in-the-loop release orchestration workspace operating as the first agent workspace within the WWA Agent Workspace Hub. Users upload deployment requests via Excel, the system creates Release Flows that track deployment progress across SIT / UAT / PROD stages, and task owners or admins make explicit workflow decisions before the flow can advance. The current workspace already includes deny-by-default Access Grants, scoped visibility through `Application + SNOW Group`, and an Access Management MVP.

**Architectural style:** Layered service architecture with a Vue 3 SPA frontend, Spring Boot REST API backend, Oracle persistence, and a deny-by-default authorization layer that combines platform entry grants with scoped visibility governance.

**Naming note:** `Deployment Agent` is the workspace display name. `WWA` is the short label for the `WWA Agent Workspace Hub`. Current technical identifiers remain unchanged for now, including `/wwa/deployment-agent`, `/api/deployment-agent`, and the `com.wwa.deploymentagent` package namespace.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Vue 3 (Composition API) · Vite 5 · Pinia · Vue Router 4 · Axios |
| Backend | Java 21 · Spring Boot 3.2.0 · Spring MVC · Spring Data JPA · Spring Security |
| Database | Oracle (production) · H2 in-memory (tests) |
| Build | Maven 3 (backend) · npm (frontend) |
| Auth | Session-based login via authentication-provider abstraction with header fallback for tests |

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  Users                                                               │
│  Developer · Tech Lead · DevOps Admin · Audit / Management           │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ HTTPS
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Web App                                                             │
│  Vue 3 · Pinia · Vue Router · Axios                                  │
│                                                                      │
│  Summary · Detail · Upload · Config · Audit · Access Mgmt · Login    │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  API Service                                     ┌─────────────────┐ │
│  Spring Boot 3 · Spring MVC                      │  Auth           │ │
│  Workflow controllers + Access Mgmt MVP          │  Session Filter │ │
│  Jakarta Validation · RBAC / Access Grants       │  Spring Security│ │
│                                                  └────────┬────────┘ │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────┐  ┌─────────────────────┐  ┌────────────────┐  │
│  │  Import &         │  │  Execution &         │  │  Config, Audit │  │
│  │  Workflow Engine  │  │  Decision Engine     │  │  & Access Ctrl │  │
│  │                   │  │                      │  │                │  │
│  │  Excel Parser     │  │  Task State Machine  │  │  Config CRUD   │  │
│  │  Import Service   │  │  Decision Engine     │  │  Audit Logger  │  │
│  │  Release Flow Svc │  │  Progression Service │  │  Access Grants │  │
│  │  Task Service     │  │  Auto Execution Svc  │  │  Permission Map│  │
│  └──────────────────┘  └──────────┬────────────┘  └────────────────┘  │
│                                   │                                   │
├──────────────────────────────────────────────────────────────────────┤
│  Persistence                                                         │
│  Spring Data JPA · Workflow + Audit + Config + Access Grant stores   │
└──────────────┬──────────────────────┬────────────────────────────────┘
               │                      │ REST (fire-and-forget)
               ▼                      ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌────────────────┐
│  Oracle DB           │  │  Jenkins              │  │  Configured    │
│                      │  │  + Ansible Tower      │  │  Auth Provider │
│  7 implemented       │  │                       │  │  (stub today,  │
│  entities including  │  │  Jenkins: Basic Auth │  │  optional Team │
│  workflow, audit,    │  │  Ansible: Bearer     │  │  Book adapter  │
│  access grants, and  │  │  10s connect / 30s   │  │  later)        │
│  CLOB for JSON cols  │  │  read timeout        │  │                │
│  Append-only audit   │  │                       │  │                │
└──────────────────────┘  └───────────────────────┘  └────────────────┘
```

---

## Constraints and Assumptions

| # | Constraint | Source |
|---|-----------|--------|
| C1 | System is embedded within the WWA Agent Workspace Hub | Spec §1 |
| C2 | Excel template schema is fixed for MVP (AMH_HCC_task sheet) | Spec §10 |
| C3 | Editable task statuses limited to `Pending` and `Ready_For_Execution` | Spec §7.7 |
| C4 | Import is atomic at file level — all rows succeed or fail together | Spec FR-14 |
| C5 | No auto-progression after execution without explicit human decision | Spec FR-53 |
| C6 | Task and rundown mutation is owner-driven with `DEVOPS_ADMIN` override rather than TL-only review control | Spec §7.8 / Spec §9.1 |
| C7 | Task reruns preserve same `task_id`; new execution history per attempt | Spec §9.4 |
| C8 | Deployment Agent product entry is deny-by-default in Phase 1 | Spec FR-70 |
| C9 | Product access and scoped visibility are managed through local Access Grants rather than a separate user account system | Spec US-21 / US-24 |
| C10 | Access enforcement must be consistent across menus, routes, and APIs | Spec FR-75 / FR-76 |

### Resolved Design Decisions

| Decision | Resolution |
|----------|-----------|
| Auto-execution trigger | User-triggered: task owner or admin starts Run / records MANUAL result |
| Secret store | Jenkins/Ansible credentials stored in config table; no external vault for MVP |
| Execution callbacks | Deferred — MVP uses fire-and-forget; task stays in `Executing` after submission |
| Result log storage | Full logs stay in Jenkins/Ansible; DA stores external job URL for click-through |
| Authentication | Session-based login via configured authentication provider; stub provider for local/dev/test |
| Product entry authorization | Phase 1 uses local Access Grants with deny-by-default semantics |

---

## Data Architecture

### Conceptual Entities

| Entity | Description | Key Attributes |
|--------|------------|----------------|
| Release Flow | Deployment journey across stages | project_id, release_id (system-generated), current_stage, flow_status, review_status |
| Request | Stage-scoped unit within a Release Flow | stage, request_status, snow_group, application, agent, owner |
| Task | Atomic executable step (one per Excel row) | execution_type (MANUAL/AUTO), task_status, input_parameters (JSON), expected_output |
| Task Execution History | Per-attempt execution record | attempt_number, execution_status, result_summary, external job fields (6) |
| Configuration Item | Runtime config (Jenkins/Ansible URLs, credentials) | config_key (enum PK), config_value |
| Audit Log Entry | Immutable operator action record | operator_id, action_type, application, snow_group, agent, context_payload (JSON) |
| Access Grant | Product authorization record for one employee | employee_id, grant_status, assigned_roles, scope_grants, last_login_at, updated_by |

### Entity Relationships

```
Release Flow ──1:N──► Request ──1:N──► Task ──1:N──► Task Execution History

Configuration Item  (independent)
Audit Log Entry     (independent, soft references to Release Flow / Request / Task + scope fields)
Access Grant        (independent, product entry + scoped visibility record)
```

### Excel Template Field Mapping

| Template Field | Action | Target | Classification |
|---------------|--------|--------|---------------|
| Project ID | Map | ReleaseFlow.project_id | Core — grouping key |
| Project Name | Map | ReleaseFlow.project_name | Display |
| Task ID | Map | Task.task_group_id | Display grouping |
| Task Name | Map | Task.task_group_name | Display |
| Step seq# | Map | Task.step_seq | Core — ordering |
| Step | Map | Task.task_name | Core — identity |
| Execution Type | Map | Task.execution_type | Core — MANUAL/AUTO |
| Script to be executed | Map | Task.input_parameters.script | Core — payload |
| Parameter (input) | Map | Task.input_parameters.parameters | Core — payload |
| Parameter (Expected Output) | Map | Task.expected_output | Core — verification |
| Owner | Map | Task.owner | Display |
| Planned Start/End | Map | Task.planned_start_time/end_time | Display only |
| Activity category, Common, Dependencies, Validation | Store | Task.import_metadata (JSON) | Metadata blob |
| Status, Start/End date/time | Drop | — | Not imported |
| Stage | From upload UI | Request.stage | Core |
| Application | From upload UI | Request.application | Runtime scope |
| SNOW Group | From upload UI | Request.snow_group | Runtime scope |
| Agent | From upload UI | Request.agent | Runtime scope |
| Release ID | System-generated | ReleaseFlow.release_id | Core |

---

## State Architecture

### Task Status

```
Pending ──► Ready_For_Execution ──► Executing ──► Awaiting_Review ──► Approved
  │                │                    │                │
  └──► Skipped     └──► Skipped         └──► Failed      └──► Rejected
                                                │                │
                                                └► Ready_For_    └► Ready_For_
                                                   Execution        Execution
                                                   (rerun)          (rerun)
```

### Aggregation Rules (bottom-up)

| Level | Input | Rule |
|-------|-------|------|
| Request status | Child task statuses | All Approved/Skipped → Completed; Any Rejected → Rejected; Any Failed → Failed; Any active → Running; else Pending |
| Flow status | Child request statuses | Same priority-based aggregation |
| Stage summary | Task statuses in stage | Done (all terminal) / Running (any active) / Pending (all pending) |

---

## Integration Architecture

### Jenkins

- **Pattern:** Synchronous REST POST, fire-and-forget
- **Auth:** Basic Auth (user + API token from config table)
- **URL:** `{jenkins_url}/job/{script}/buildWithParameters`
- **Timeout:** 10s connect / 30s read
- **Parameters:** Map entries become named build params; String sent as `PARAMETERS`

### Ansible Tower

- **Pattern:** Synchronous REST POST, fire-and-forget
- **Auth:** Bearer token (from config table)
- **URL:** `{ansible_url}/api/v2/job_templates/{script}/launch/`
- **Body:** JSON with `extra_vars` (serialized via Jackson ObjectMapper)
- **Timeout:** 10s connect / 30s read
- **Job URL:** Points to AWX UI (`/#/jobs/playbook/{id}`), not API

### Authentication Provider Boundary

- **Pattern:** Interface-based provider (`TeamBookAuthenticationProvider` naming retained in code)
- **Current baseline:** `StubTeamBookAuthenticationProvider` is active in local/dev/test and supplies both login fixtures and directory-search fixtures
- **Future production option:** Team Book adapter once enterprise contract details are finalized
- **Responsibility boundary:** Deployment Agent maintains product authorization through its own local Access Grant store

### Access Grant Resolution (Phase 1)

- **Pattern:** Internal authorization lookup after successful authentication
- **Source of truth:** Deployment Agent persistence store
- **Purpose:** Determine whether an authenticated employee may enter the product, what effective roles/permissions apply, and which `Application + SNOW Group` scopes are visible/manageable
- **Current contract:** `auth/login` and `auth/me` return a compatibility `role` plus `roles[]`, effective `permissions[]`, and `scopes[]`

---

## API Boundaries

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | /auth/login | Session login | Public |
| GET | /auth/me | Current user | Session |
| POST | /auth/logout | End session | Session |
| GET | /access-grants | List access grants | DEVOPS_ADMIN |
| GET | /access-grants/directory | Search provider-backed employee directory | DEVOPS_ADMIN |
| POST | /access-grants | Create access grant with roles / scope grants | DEVOPS_ADMIN |
| PATCH | /access-grants/{employeeId} | Update roles / scope grants / metadata | DEVOPS_ADMIN |
| POST | /access-grants/{employeeId}/suspend | Suspend product access | DEVOPS_ADMIN |
| POST | /access-grants/{employeeId}/reactivate | Reactivate product access | DEVOPS_ADMIN |
| POST | /upload | Excel import | DEVELOPER, TL, DEVOPS_ADMIN |
| GET | /release-flows | List flows (paginated) | Any authenticated within scoped visibility |
| GET | /release-flows/{id} | Flow detail with tasks | Any authenticated within scoped visibility |
| GET | /tasks | List tasks by request | Any |
| GET | /tasks/{id} | Task detail | Any |
| PUT | /tasks/{id}/input | Edit task input | Task owner or DEVOPS_ADMIN |
| GET | /tasks/{id}/executions | Execution history | Any |
| POST | /tasks/{id}/start-manual | Start MANUAL task | Task owner or DEVOPS_ADMIN |
| POST | /tasks/{id}/record-result | Record MANUAL result | Task owner or DEVOPS_ADMIN |
| POST | /tasks/{id}/submit-auto | Submit AUTO task | Task owner or DEVOPS_ADMIN |
| POST | /tasks/{id}/decision | Apply decision | Task owner or DEVOPS_ADMIN |
| GET | /config | List config items | Any |
| POST | /config | Upsert config item | DEVOPS_ADMIN |
| GET | /audit-logs | List audit entries | Any authenticated within scoped visibility |

All endpoints prefixed with `/api/deployment-agent`.

---

## Security Architecture

- **Session management:** `IF_REQUIRED` — session created on login, read by SessionAuthFilter
- **Filter chain:** SessionAuthFilter → HeaderAuthFilter (test fallback) → Spring Security
- **Authentication / authorization split:** the configured auth provider validates login identity; local Access Grants provide product entry authorization, effective roles, and `Application + SNOW Group` scope grants for Phase 1
- **RBAC / permissions:** Enforced server-side in controllers and domain services; frontend route guards and UI visibility must align with the same effective permissions
- **Global admin rule:** `DEVOPS_ADMIN` with an empty scope list is treated as a global admin context
- **CSRF:** Disabled (REST API with session cookies)
- **Audit isolation:** AuditLoggerService uses `REQUIRES_NEW` propagation — audit writes persist even if the business transaction rolls back
- **Optimistic locking:** `@Version` on ReleaseFlow, Request, Task — concurrent updates return 409
- **Deny-by-default:** Users without an active Access Grant are blocked from Deployment Agent even if enterprise authentication succeeds

---

## Pending External Dependencies

1. **Future Team Book adapter contract** — endpoint URL, request/response format, enterprise identity lookup rules if the production path adopts Team Book
2. **Jenkins/Ansible credentials** — entered at runtime via Config admin page
3. **Enterprise directory enrichment** — confirm whether a later phase should extend Access Management beyond the current provider-backed directory search and manual display-name fallback
