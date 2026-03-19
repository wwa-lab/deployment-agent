# System Architecture: Deployment Agent

**Date:** 2026-03-19
**Status:** Implemented (MVP)
**Source:** spec.md (primary), repository code (validation)

---

## Overview

Deployment Agent is a controlled, human-in-the-loop deployment workflow system embedded within the WWA platform. Users upload deployment requests via Excel, the system creates Release Flows that track deployment progress across SIT / UAT / PROD stages, and Tech Leads make explicit decisions (Approve / Reject / Rerun / Skip) at every task before the flow can advance.

**Architectural style:** Layered service architecture with a Vue 3 SPA frontend, Spring Boot REST API backend, and Oracle persistence.

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
│  Summary View · Detail View · Upload · Config · Audit · Login        │
└──────────────────────┬───────────────────────────────────────────────┘
                       │ REST / JSON + Session Cookie
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  API Service                                     ┌─────────────────┐ │
│  Spring Boot 3 · Spring MVC                      │  Auth           │ │
│  7 REST Controllers · 16 Endpoints               │  Session Filter │ │
│  Jakarta Validation · RBAC                       │  Spring Security│ │
│                                                  └────────┬────────┘ │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────┐  ┌─────────────────────┐  ┌────────────────┐  │
│  │  Import &         │  │  Execution &         │  │  Config &      │  │
│  │  Workflow Engine  │  │  Decision Engine     │  │  Audit         │  │
│  │                   │  │                      │  │                │  │
│  │  Excel Parser     │  │  Task State Machine  │  │  Config CRUD   │  │
│  │  Import Service   │  │  Decision Engine     │  │  Audit Logger  │  │
│  │  Release Flow Svc │  │  Progression Service │  │  (REQUIRES_NEW)│  │
│  │  Task Service     │  │  Auto Execution Svc  │  │                │  │
│  └──────────────────┘  └──────────┬────────────┘  └────────────────┘  │
│                                   │                                   │
├──────────────────────────────────────────────────────────────────────┤
│  Persistence                                                         │
│  Spring Data JPA · 6 Repositories · Optimistic Locking               │
└──────────────┬──────────────────────┬────────────────────────────────┘
               │                      │ REST (fire-and-forget)
               ▼                      ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌────────────────┐
│  Oracle DB           │  │  Jenkins              │  │  Team Book     │
│                      │  │  Basic Auth · 10s/30s │  │  Auth Provider │
│  6 Tables            │  ├───────────────────────┤  │  (stub for MVP)│
│  CLOB for JSON cols  │  │  Ansible Tower        │  │                │
│  Append-only audit   │  │  Bearer · 10s/30s     │  └────────────────┘
└──────────────────────┘  └───────────────────────┘
```

---

## Constraints and Assumptions

| # | Constraint | Source |
|---|-----------|--------|
| C1 | System is embedded within the WWA platform | Spec §1 |
| C2 | Excel template schema is fixed for MVP (AMH_HCC_task sheet) | Spec §10 |
| C3 | Editable task statuses limited to `Pending` and `Ready_For_Execution` | Spec §7.7 |
| C4 | Import is atomic at file level — all rows succeed or fail together | Spec FR-14 |
| C5 | No auto-progression after execution without explicit human decision | Spec FR-53 |
| C6 | Single review owner per Release Flow | Spec §9.1 |
| C7 | Task reruns preserve same `task_id`; new execution history per attempt | Spec §9.4 |

### Resolved Design Decisions

| Decision | Resolution |
|----------|-----------|
| Auto-execution trigger | User-triggered: TL clicks "Submit Auto" or "Record Result" |
| Secret store | Jenkins/Ansible credentials stored in config table; no external vault for MVP |
| Execution callbacks | Deferred — MVP uses fire-and-forget; task stays in `Executing` after submission |
| Result log storage | Full logs stay in Jenkins/Ansible; DA stores external job URL for click-through |
| Authentication | Session-based Team Book login; stub provider for dev/test |

---

## Data Architecture

### Conceptual Entities

| Entity | Description | Key Attributes |
|--------|------------|----------------|
| Release Flow | Deployment journey across stages | project_id, release_id (system-generated), current_stage, flow_status, review_status |
| Request | Stage-scoped unit within a Release Flow | stage, request_status |
| Task | Atomic executable step (one per Excel row) | execution_type (MANUAL/AUTO), task_status, input_parameters (JSON), expected_output |
| Task Execution History | Per-attempt execution record | attempt_number, execution_status, result_summary, external job fields (6) |
| Configuration Item | Runtime config (Jenkins/Ansible URLs, credentials) | config_key (enum PK), config_value |
| Audit Log Entry | Immutable operator action record | operator_id, action_type, context_payload (JSON) |

### Entity Relationships

```
Release Flow ──1:N──► Request ──1:N──► Task ──1:N──► Task Execution History

Configuration Item  (independent)
Audit Log Entry     (independent, soft references to Release Flow / Request / Task)
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
- **Production:** Pending Team Book API contract (endpoint URL, request/response format, role mapping)

---

## API Boundaries

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | /auth/login | Session login | Public |
| GET | /auth/me | Current user | Session |
| POST | /auth/logout | End session | Session |
| POST | /upload | Excel import | DEVELOPER, TL |
| GET | /release-flows | List flows (paginated) | Any |
| GET | /release-flows/{id} | Flow detail with tasks | Any |
| GET | /tasks | List tasks by request | Any |
| GET | /tasks/{id} | Task detail | Any |
| PUT | /tasks/{id}/input | Edit task input | TL |
| GET | /tasks/{id}/executions | Execution history | Any |
| POST | /tasks/{id}/record-result | Record MANUAL result | TL |
| POST | /tasks/{id}/submit-auto | Submit AUTO task | TL, DEVOPS_ADMIN |
| POST | /tasks/{id}/decision | Apply decision | TL |
| GET | /config | List config items | Any |
| POST | /config | Upsert config item | DEVOPS_ADMIN |
| GET | /audit-logs | List audit entries | AUDIT, MANAGEMENT, DEVOPS_ADMIN |

All endpoints prefixed with `/api/deployment-agent`.

---

## Security Architecture

- **Session management:** `IF_REQUIRED` — session created on login, read by SessionAuthFilter
- **Filter chain:** SessionAuthFilter → HeaderAuthFilter (test fallback) → Spring Security
- **RBAC:** Enforced server-side in controllers and domain services; frontend hides UI elements
- **CSRF:** Disabled (REST API with session cookies)
- **Audit isolation:** AuditLoggerService uses `REQUIRES_NEW` propagation — audit writes persist even if the business transaction rolls back
- **Optimistic locking:** `@Version` on ReleaseFlow, Request, Task — concurrent updates return 409

---

## Pending External Dependencies

1. **Team Book API contract** — endpoint URL, request/response format, role mapping rules
2. **Jenkins/Ansible credentials** — entered at runtime via Config admin page
