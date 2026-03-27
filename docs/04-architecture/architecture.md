# System Architecture: Release Agent

**Date:** 2026-03-26
**Status:** Implemented (MVP + scoped access governance) — platform transition in progress (see `docs/06-tasks/wwa-migration-plan.md`)
**Source:** spec.md (primary), repository code (validation)

---

## Platform Context

Release Agent is the **first workspace** under the **WWA Agent Workspace Hub**. The operating model is:

```
FinBlock  →  WWA Agent Workspace Hub (`WWA`)  →  Release Agent (first workspace)
```

- **FinBlock** provides one stable entry link to WWA.
- **WWA Agent Workspace Hub** owns authentication, top-level navigation, platform access management, and platform-level audit.
- **Release Agent** owns release orchestration, deployment workflows, release flow lifecycle, and execution integrations.

Shared capabilities (Audit Log, Access Management, Configuration Management) are presented inside the WWA Agent Workspace Hub but their ownership boundary is documented in `docs/00-context/wwa-product-positioning.md`.

---

## Overview

Release Agent is a controlled, human-in-the-loop release orchestration workspace operating as the first agent workspace within the WWA Agent Workspace Hub. Users upload deployment requests via Excel, the system creates Release Flows that track deployment progress across SIT / UAT / PROD stages, and task reviewers make explicit workflow decisions before the flow can advance. The current workspace already includes deny-by-default Access Grants, scoped visibility through `Application + SNOW Group`, and an Access Management MVP.

**Architectural style:** Layered service architecture with a Vue 3 SPA frontend, Spring Boot REST API backend, Oracle persistence, and a deny-by-default authorization layer that combines platform entry grants with scoped visibility governance.

**Naming note:** `Release Agent` is the workspace display name. `WWA` is the short label for the `WWA Agent Workspace Hub`. Current technical identifiers remain unchanged for now, including `/wwa/deployment-agent`, `/api/deployment-agent`, and the `com.wwa.deploymentagent` package namespace.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Vue 3 (Composition API) · Vite 5 · Pinia · Vue Router 4 · Axios |
| Backend | Java 21 · Spring Boot 3.2.4 · Spring MVC · Spring Data JPA · Spring Security |
| Database | Oracle (production) · H2 in-memory (tests) |
| Build | Maven 3 (backend) · npm (frontend) |
| Auth | Session-based login (Team Book provider) with header fallback for tests |

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
│  Oracle DB           │  │  Jenkins              │  │  Team Book     │
│                      │  │  + Ansible Tower      │  │  Auth Provider │
│  7 implemented       │  │                       │  │  (stub for MVP)│
│  entities including  │  │  Jenkins: Basic Auth │  │                │
│  access grants       │  │  Ansible: Bearer     │  │                │
│  CLOB for JSON cols  │  │  10s connect / 30s   │  │                │
│  Append-only audit   │  │  read timeout        │  │                │
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
| C6 | Single review owner per Release Flow | Spec §9.1 |
| C7 | Task reruns preserve same `task_id`; new execution history per attempt | Spec §9.4 |
| C8 | Release Agent product entry is deny-by-default in Phase 1 | Spec FR-70 |
| C9 | Product access and scoped visibility are managed through local Access Grants rather than a separate user account system | Spec US-21 / US-24 |
| C10 | Access enforcement must be consistent across menus, routes, and APIs | Spec FR-75 / FR-76 |

### Resolved Design Decisions

| Decision | Resolution |
|----------|-----------|
| Auto-execution trigger | User-triggered: reviewer starts Run / records MANUAL result |
| Secret store | Jenkins/Ansible credentials stored in config table; no external vault for MVP |
| Execution callbacks | Deferred — MVP uses fire-and-forget; task stays in `Executing` after submission |
| Result log storage | Full logs stay in Jenkins/Ansible; DA stores external job URL for click-through |
| Authentication | Session-based Team Book login; stub provider for dev/test |
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

### Team Book (Authentication)

- **Pattern:** Interface-based provider
- **MVP:** StubTeamBookAuthenticationProvider — 5 hardcoded users, any password
- **Production:** Pending Team Book API contract (endpoint URL, request/response format, enterprise identity mapping)
- **Responsibility boundary:** Team Book authenticates enterprise identity; Release Agent resolves product access through its own Access Grant store

### Access Grant Resolution (Phase 1)

- **Pattern:** Internal authorization lookup after successful authentication
- **Source of truth:** Release Agent persistence store
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
- **Authentication / authorization split:** Team Book provides enterprise identity; local Access Grants provide product entry authorization, effective roles, and `Application + SNOW Group` scope grants for Phase 1
- **RBAC / permissions:** Enforced server-side in controllers and domain services; frontend route guards and UI visibility must align with the same effective permissions
- **Global admin rule:** `DEVOPS_ADMIN` with an empty scope list is treated as a global admin context
- **CSRF:** Disabled (REST API with session cookies)
- **Audit isolation:** AuditLoggerService uses `REQUIRES_NEW` propagation — audit writes persist even if the business transaction rolls back
- **Optimistic locking:** `@Version` on ReleaseFlow, Request, Task — concurrent updates return 409
- **Deny-by-default:** Users without an active Access Grant are blocked from Release Agent even if enterprise authentication succeeds

---

## Pending External Dependencies

1. **Team Book API contract** — endpoint URL, request/response format, enterprise identity lookup rules
2. **Jenkins/Ansible credentials** — entered at runtime via Config admin page
3. **Enterprise directory expansion** — confirm whether a later phase should extend Access Management beyond the current existing-grants-only search scope
