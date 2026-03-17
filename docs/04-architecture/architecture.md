## Technology Stack

The MVP implementation is based on the following technology stack:

- **Frontend**: Vue 3
- **Backend**: Node.js / TypeScript / Fastify 4 / TypeORM 0.3
- **Database**: Oracle (production); sql.js in-memory SQLite (tests)
- **Validation**: Zod

> *Note: The original architecture assumed Spring Boot 3. Implementation adopted a Node.js stack. All references in this document reflect the implemented stack.*

### Technology Usage Notes
- Vue 3 is used to implement the WWA-embedded Deployment Agent workspace UI, including summary views, detail views, upload dialogs, task actions, configuration pages, and audit log viewing.
- Fastify 4 with TypeORM 0.3 is used to implement API endpoints, orchestration services, validation logic, integration adapters, audit logging, and callback handling. Zod is used for request body validation.
- Oracle is used as the primary transactional persistence store for Release Flow, Request, Task, Task Execution History, Configuration Item, and Audit Log entities unless a specific storage concern is explicitly separated.

### Architectural Implications
- Frontend-backend interaction follows a clear REST API contract suitable for Vue 3 + Fastify integration.
- Backend modules are designed with a layered architecture: domain services, repositories (TypeORM), and HTTP handlers (Fastify route handlers). Shared types live in `src/contracts/`.
- Oracle schema design must support hierarchical workflow entities, append-only audit logging, and efficient querying for summary/detail screens.
- Large execution logs or result payloads may remain outside core transactional tables if Oracle table growth or query performance becomes a concern; detailed design must confirm whether result payloads are stored in Oracle CLOB columns or externalized storage.

---

## Constraints and Assumptions

- System is embedded within the existing WWA platform and reuses WWA navigation, authentication, and persistence infrastructure.
- Excel template schema is fixed (not dynamic) for MVP; a formal schema artifact must be provided before implementation.
- For MVP, editable task statuses are limited to `Pending` and `Ready_For_Execution`; editing after `Executing` status is out of scope.
- [ASSUMPTION] Initial task execution may be auto-triggered once task reaches `Ready_For_Execution` status; if this changes, an explicit execution trigger UI element is needed.
- [ASSUMPTION] Import behavior is atomic at file level; if partial import is needed, this requires explicit revision.
- [ASSUMPTION] Review Owner cardinality (single user vs. group) must be confirmed; architecture assumes single user for MVP.
- [ASSUMPTION] Release Flow grouping rule and Release ID fallback rule are required architecture inputs that must be explicitly documented before implementation.
- [ASSUMPTION] Stage summary status aggregation uses a defined rule (see State Architecture); mixed states yield `Running` if any task is in running-like state.
- Credentials and secret storage mechanism is open and must be resolved during implementation (Vault, environment variables, or managed secret store).

### Auto-Execution Design Note
The current architecture assumes that tasks may transition automatically from `Ready_For_Execution` to `Executing` as part of backend orchestration.
If this assumption is rejected and execution must be user-triggered instead, the design phase must introduce:
- an explicit Execute action in the Vue 3 UI,
- a dedicated execution trigger API endpoint in Fastify,
- updated permission and state-transition rules,
- revised test scenarios for manual execution gating.

This decision must be confirmed before detailed design begins.

---

## Data Architecture

### Conceptual Entities
| Entity | Description | Key Attributes |
|---|---|---|
| Release Flow | Top-level deployment journey across stages | release_flow_id, project_id (from template `Project ID`), project_name (from `Project Name`), release_id (system-generated: `{stage}-{normalized_project_name}-{seq}`), current_stage (SIT/UAT/PROD — from upload UI), flow_status, review_status, review_owner, created_at, updated_at |
| Request | Stage-scoped unit within Release Flow | request_id, release_flow_id, stage, request_status, created_at, updated_at |
| Task | Atomic executable step within Request; one row per AMH_HCC_task template row | task_id (PK), request_id (FK), task_group_id, task_group_name, step_seq, task_name, execution_type, input_parameters (JSON: {script, parameters}), expected_output, task_status, current_result_summary, latest_execution_id, start_time, end_time, last_updated_at — plus display columns: owner, planned_start_time, planned_end_time — plus import_metadata (JSON blob for activity_category/common/dependencies/validation) |
| Task Execution History | Rerun history entry (one record per execution; same logical task may have multiple) | execution_id, task_id, attempt_number, execution_status, input_snapshot, result_summary, result_logs, start_time, end_time |
| Configuration Item | Managed configuration (Jenkins URL, Ansible URL, Execution Callback Endpoint) | config_key, config_value, description, updated_by, updated_at |
| Audit Log Entry | Immutable record of operator action | audit_log_id, operator_id, operator_role, action_type (upload, edit, view_result, approve, reject, rerun, skip), timestamp, release_flow_id (nullable), request_id (nullable), task_id (nullable), context_payload |

### Rerun History Model
For MVP, a logical task keeps the same `task_id` across reruns.
Each rerun creates a new `Task Execution History` record with an incremented `attempt_number`.
The Task entity represents the current logical step, while Task Execution History represents each concrete execution attempt.
The Result Viewer should default to the latest execution attempt.
UI support for switching across attempts is a Design phase responsibility and does not change the underlying storage model.

### Rerun State Transition
When a TL decides to rerun a task, the task must be in a terminal-error state (`Rejected` or `Failed`). The rerun transitions the task back to `Ready_For_Execution`, creates a new `TaskExecutionHistory` record, and the execution pipeline picks it up again. This is intentionally conservative — a task must be explicitly rejected before it can be rerun.

### Excel Template Field-to-Domain Mapping

The real template is **AMH_HCC_task** (steps table). One row = one system **Task**.
Multiple rows sharing the same `Task ID` form one logical task group (display-only grouping).

Fields are classified as: **Core** (workflow/execution control), **Display** (UI only), **Metadata** (stored opaque), or **Dropped** (not imported).

| Template Field | Action | Domain Entity | Attribute | Decision |
|---|---|---|---|---|
| `Project ID` | Map | Release Flow | `project_id` | **Core** — Release Flow grouping key |
| `Project Name` | Map | Release Flow | `project_name` | Display |
| `Task ID` | Map | Task | `task_group_id` | Display grouping + ordering context (NOT a new entity level) |
| `Task Name` | Map | Task | `task_group_name` | Display |
| `Step seq#` | Map | Task | `step_seq` | **Core** — execution ordering |
| `Step` | Map | Task | `task_name` | **Core** — atomic step identity |
| `Execution Type` | Map | Task | `execution_type` | **Core** — execution mode: `MANUAL` (human-executed externally) \| `AUTO` (system-submitted to pipeline) |
| `Script to be executed` | Map | Task | `input_parameters.script` (JSON) | **Core** — execution payload |
| `Parameter (input)` | Map | Task | `input_parameters.parameters` (JSON) | **Core** — execution payload |
| `Parameter (Expected Output)` | Map | Task | `expected_output` | **Core** — result verification comparison |
| `Owner` | Map | Task | `owner` | Display |
| `Planned Start date/time` | Map | Task | `planned_start_time` | Display only; does NOT control execution |
| `Planned End date/time` | Map | Task | `planned_end_time` | Display only; does NOT control execution |
| `Activity category` | Store as metadata | Task | `import_metadata` JSON | Metadata — no workflow behavior |
| `Common` | Store as metadata | Task | `import_metadata` JSON | Metadata — no workflow behavior |
| `Dependencies` | Store as metadata | Task | `import_metadata` JSON | Metadata — no gating in MVP |
| `Validation` | Store as metadata | Task | `import_metadata` JSON | Metadata — no automated validation in MVP |
| `Status` | **Ignore on import** | — | not stored | System always creates Tasks in `Pending`; template status bypasses human gate |
| `Start date/time` | **Drop** | — | not imported | System generates `start_time` at execution |
| `End date/time` | **Drop** | — | not imported | System generates `end_time` from callback |
| `Release ID` | **System-generated** — not from template | Release Flow | `release_id` | **Core** — generated as `{stage}-{normalized_project_name}-{seq}` when Release Flow is first created |
| `Stage` | **From upload UI parameter** — not from template rows | Request | `stage` | **Core** — user selects SIT \| UAT \| PROD in the upload dialog; passed as a request parameter to the import endpoint |

> **Import Responsibility**: The Import Service receives `(file, stage)` as inputs. Stage is the user-selected stage from the upload dialog — never read from Excel rows. Release ID is generated internally when a new Release Flow is created. The Import Service groups uploads by `project_id` and creates/attaches to Release Flows accordingly.

---

### Persistence Note for Oracle
Oracle is the system-of-record database for workflow state and auditability.
Detailed design must confirm:
- table structure and indexing strategy,
- use of Oracle `CLOB` for result logs if stored in-database,
- foreign key strategy across Release Flow / Request / Task / Task Execution History / Audit Log,
- partitioning or archival approach if audit/result growth becomes significant.

---

## Integration Architecture

### Execution Callback Endpoint
- **Interaction Pattern**: Webhook callback from Jenkins/Ansible back to Deployment Agent; payload includes execution_id, task_id, status, result_summary, result_logs, timestamp.
- **Triggered by**: Remote execution engine upon job/playbook completion.
- **Responsibility**: Execution Callback Handler receives callback, validates correlation, updates Task status, stores results, triggers Decision Engine if task reaches `Awaiting_Review`.

### Callback Contract Note
A formal callback contract is required before implementation.
The design phase must produce an API contract artifact for the callback endpoint that defines:
- request schema,
- response schema,
- validation rules,
- error codes,
- timeout expectations,
- retry semantics,
- idempotency behavior,
- authentication / signing model.

Minimum security expectation:
- HTTPS transport,
- authenticated callback source,
- replay / duplicate protection,
- request correlation via execution identifier.

For Fastify implementation, this callback should be modeled as a dedicated route handler with explicit Zod request validation and idempotent processing behavior.

---

## API / Interface Boundaries

### Design Handoff Note
The endpoint list in this architecture document defines boundary ownership and interaction intent only.
Detailed request/response schemas, validation contracts, error models, and role-specific authorization behavior are Design phase artifacts.

The design phase must produce:
- OpenAPI specifications for synchronous HTTP endpoints,
- JSON schema or equivalent request validation definitions (Zod schemas in practice),
- callback contract specification for execution result delivery,
- frontend-backend contract alignment suitable for Vue 3 and Fastify integration.

### Major Inbound Interfaces (Frontend → Backend)
| Endpoint / Resource | Consumer | Purpose |
|---|---|---|
| POST /api/deployment-agent/upload | Upload UI | Submit Excel file with selected stage for import; accepts `stage` (SIT/UAT/PROD) as required parameter alongside the file |
| GET /api/deployment-agent/release-flows | Summary UI | Retrieve Release Flow list (with optional filters, paginated) |
| GET /api/deployment-agent/release-flows/:id | Details UI | Retrieve Release Flow details with nested requests and tasks |
| GET /api/deployment-agent/tasks?requestId=X | Task UI | Retrieve task list for selected Request |
| GET /api/deployment-agent/tasks/:id | Task UI | Retrieve single task detail |
| PUT /api/deployment-agent/tasks/:id/input | Task Input Editor | Update task input parameters (TL role) |
| POST /api/deployment-agent/tasks/:id/decision | Decision UI | Submit Approve / Reject / Rerun / Skip decision (TL role) |
| GET /api/deployment-agent/tasks/:id/executions | Result Viewer | Retrieve execution history with result summary and logs |
| GET /api/deployment-agent/config | Config UI | Retrieve current configuration items |
| POST /api/deployment-agent/config | Config UI | Create/update configuration item (DevOps Admin only) |
| GET /api/deployment-agent/audit-logs | Audit UI | Retrieve recent audit log list (role-gated: AUDIT/MANAGEMENT/DEVOPS_ADMIN) |

### API Style Note
For Fastify implementation, API design follows:
- resource-oriented route handler grouping,
- explicit DTOs (TypeScript interfaces) for request and response payloads,
- validation through Zod schemas,
- centralized Fastify error handler mapping AppError types to HTTP status codes,
- server-side RBAC enforcement via `requireRole()` middleware regardless of client-side UI visibility.

---

## Frontend and Backend Enforcement Note

Frontend role-awareness in Vue 3 is a usability feature, not a security boundary.
The UI may hide or disable actions based on role, state, and ownership context, but all enforcement must also occur server-side in Fastify APIs.

This means:
- Vue 3 controls visibility and user guidance,
- Fastify handlers enforce authorization, validation, and state-transition legality,
- Oracle persists only validated and authorized state changes.

---

## Result Storage Note

Result Storage is a persistence concern separate from Task state.

Architectural requirement:
- full execution logs and large result payloads must not bloat the main Task state model,
- the Result Viewer must still retrieve the latest execution result efficiently.

Design phase must decide whether Result Storage is implemented as:
- Oracle tables with `CLOB` payloads,
- Oracle plus a separated result table strategy,
- or an externalized storage mechanism with references persisted in Oracle.

For MVP, the architecture assumes:
- Task stores current summary metadata and latest execution reference,
- Task Execution History stores attempt-level summary metadata and result logs,
- full raw logs are stored in `result_logs` column (TEXT/CLOB) on TaskExecutionHistory entity.

---

## Configuration Schema Note

The following configuration items are currently assumed for MVP:

| Name | Type | Validation | Required | Update Semantics |
|---|---|---|---|---|
| Jenkins URL | string | URI / URL format | Yes | Applies to future executions |
| Ansible URL | string | URI / URL format | Yes | Applies to future executions |
| Execution Callback Endpoint | string | URI / URL format | Yes | Applies to callback registration / routing behavior |

Detailed design must confirm:
- exact format rules,
- whether HTTPS is mandatory,
- whether changes apply immediately or at task boundary,
- whether any item should be system-defined rather than user-editable.

---

## Pre-Design Confirmation List

The following items must be confirmed before design is finalized:

1. **Auto-execution trigger behavior**
   - Is `Ready_For_Execution -> Executing` automatic, or user-triggered?

2. **Rerun history model**
   - Confirm that `task_id` remains constant and each rerun creates a new execution history record.

3. **Execution callback contract**
   - Provide formal schema, security model, and retry semantics.

4. **Secret store technology**
   - Confirm Vault, environment variables, managed secret service, or equivalent.

5. **Configuration schema**
   - Confirm validation rules, required/optional status, and update semantics.

6. **Stage summary tie-breaking**
   - Confirm how `Rejected` and `Failed` should affect stage summary display if mixed with other states.

---

## Artifacts Required Before Implementation

1. ~~OQ-25 (Stage/Release ID source)~~ — **Resolved**: Stage from upload UI; Release ID system-generated
2. ~~OQ-28 (Execution Type values)~~ — **Resolved**: `MANUAL` | `AUTO`
3. ~~MANUAL task execution UX decision~~ — **Resolved**: inline "Record Result" button; TL enters result; transitions to `Awaiting_Review`
4. **Task input schema per `execution_type`** — defines editable fields per MANUAL and AUTO; MANUAL tasks may have different editable input fields than AUTO tasks
5. **Integration adapter specifications** (Jenkins and Ansible API contracts, callback payload format)
6. **Secret store architecture decision** (Vault, env vars, managed service, or other)
7. **Execution callback endpoint OpenAPI specification** (request schema, response schema, error codes, security model, retry semantics)
8. **Configuration items validation schema** (format rules, required/optional flags, update policy)
9. **Performance baseline tests** or acceptance criteria (target response times)

> Fields `Common`, `Status`, `Validation`, and `Dependencies` do **not** require pre-implementation artifacts. Their behavior is resolved by architecture decision (stored as raw metadata or ignored).
