## Technology Stack

The MVP implementation is based on the following technology stack:

- **Frontend**: Vue 3
- **Backend**: Spring Boot 3
- **Database**: Oracle

### Technology Usage Notes
- Vue 3 is used to implement the WWA-embedded Deployment Agent workspace UI, including summary views, detail views, upload dialogs, task actions, configuration pages, and audit log viewing.
- Spring Boot 3 is used to implement API endpoints, orchestration services, validation logic, integration adapters, audit logging, and callback handling.
- Oracle is used as the primary transactional persistence store for Release Flow, Request, Task, Task Execution History, Configuration Item, and Audit Log entities unless a specific storage concern is explicitly separated.

### Architectural Implications
- Frontend-backend interaction should follow a clear API contract suitable for Vue 3 + Spring Boot 3 integration.
- Backend modules should be designed in a way that maps cleanly to Spring Boot 3 service, controller, repository, and integration packages.
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
- a dedicated execution command API in Spring Boot 3,
- updated permission and state-transition rules,
- revised test scenarios for manual execution gating.

This decision must be confirmed before detailed design begins.

---

## Data Architecture

### Conceptual Entities
| Entity | Description | Key Attributes |
|---|---|---|
| Release Flow | Top-level deployment journey across stages | release_flow_id, project, release_id, current_stage (SIT/UAT/PROD), flow_status, review_status, review_owner, created_at, updated_at |
| Request | Stage-scoped unit within Release Flow | request_id, release_flow_id, stage, request_status, created_at, updated_at |
| Task | Executable step within Request | task_id, request_id, task_name, task_type, task_status, input_parameters, current_result_summary, latest_execution_id, start_time, end_time, last_updated_at |
| Task Execution History | Rerun history entry (one record per execution; same logical task may have multiple) | execution_id, task_id, attempt_number, execution_status, input_snapshot, result_summary, result_logs, start_time, end_time |
| Configuration Item | Managed configuration (Jenkins URL, Ansible URL, Execution Callback Endpoint) | config_key, config_value, description, updated_by, updated_at |
| Audit Log Entry | Immutable record of operator action | audit_log_id, operator_id, operator_role, action_type (upload, edit, view_result, approve, reject, rerun, skip), timestamp, release_flow_id (nullable), request_id (nullable), task_id (nullable), context_payload |

### Rerun History Model
For MVP, a logical task keeps the same `task_id` across reruns.  
Each rerun creates a new `Task Execution History` record with an incremented `attempt_number`.  
The Task entity represents the current logical step, while Task Execution History represents each concrete execution attempt.  
The Result Viewer should default to the latest execution attempt.  
UI support for switching across attempts is a Design phase responsibility and does not change the underlying storage model.

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

For Spring Boot 3 implementation, this callback should be modeled as a dedicated controller endpoint with explicit request validation and idempotent processing behavior.

---

## API / Interface Boundaries

### Design Handoff Note
The endpoint list in this architecture document defines boundary ownership and interaction intent only.  
Detailed request/response schemas, validation contracts, error models, and role-specific authorization behavior are Design phase artifacts.

The design phase must produce:
- OpenAPI specifications for synchronous HTTP endpoints,
- JSON schema or equivalent request validation definitions,
- callback contract specification for execution result delivery,
- frontend-backend contract alignment suitable for Vue 3 and Spring Boot 3 integration.

### Major Inbound Interfaces (Frontend → Backend)
| Endpoint / Resource | Consumer | Purpose |
|---|---|---|
| POST /api/deployment-agent/upload | Upload UI | Submit Excel file for import |
| GET /api/deployment-agent/release-flows | Summary UI | Retrieve Release Flow list (with optional filters) |
| GET /api/deployment-agent/release-flows/{id} | Details UI | Retrieve Release Flow details |
| GET /api/deployment-agent/release-flows/{id}/requests/{requestId}/tasks | Task UI | Retrieve task list for selected Request |
| POST /api/deployment-agent/tasks/{id}/edit | Task Input Editor | Update task input parameters |
| POST /api/deployment-agent/tasks/{id}/decision | Decision UI | Submit Approve / Reject / Rerun / Skip decision |
| GET /api/deployment-agent/tasks/{id}/result | Result Viewer | Retrieve execution result (summary + logs) |
| GET /api/deployment-agent/config | Config UI | Retrieve current configuration items |
| POST /api/deployment-agent/config | Config UI | Create/update configuration item (DevOps Admin only) |
| GET /api/deployment-agent/audit-logs | Audit UI | Retrieve recent audit log list (role-gated) |

### API Style Note
For Spring Boot 3 implementation, API design should prefer:
- resource-oriented controller grouping,
- explicit DTOs for request and response payloads,
- validation through standard request validation mechanisms,
- centralized error handling,
- server-side RBAC enforcement regardless of client-side UI visibility.

---

## Frontend and Backend Enforcement Note

Frontend role-awareness in Vue 3 is a usability feature, not a security boundary.  
The UI may hide or disable actions based on role, state, and ownership context, but all enforcement must also occur server-side in Spring Boot 3 APIs.

This means:
- Vue 3 controls visibility and user guidance,
- Spring Boot 3 enforces authorization, validation, and state-transition legality,
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
- Task Execution History stores attempt-level summary metadata,
- full raw logs are stored in a result-oriented persistence structure.

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

1. **Frozen Excel template schema** (JSON Schema or spreadsheet with field definitions)
2. **Task input schema** (defining editable fields, types, and validation per task type)
3. **Release Flow grouping rule** (logic for determining new vs. update)
4. **Release ID fallback rule** (handling missing Release ID)
5. **Integration adapter specifications** (Jenkins and Ansible API contracts, callback payload format)
6. **Secret store architecture decision** (Vault, env vars, managed service, or other)
7. **Execution callback endpoint OpenAPI specification**
   - request schema
   - response schema
   - error codes
   - security model
   - retry semantics
8. **Configuration items validation schema**
   - format rules
   - required/optional flags
   - update policy
9. **Performance baseline tests** or acceptance criteria (target response times)