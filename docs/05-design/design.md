# Detailed Design: Deployment Agent

## Overview

This document provides a detailed, implementation-friendly design for the Deployment Agent system, transforming the high-level architecture into concrete module and interface specifications suitable for Java 21 / Spring Boot 3 / Spring Data JPA backend, Vue 3 frontend, and Oracle persistence. The design covers all MVP workflows and integration points necessary for controlled, human-in-the-loop deployment request processing.

---

## Source Architecture

**System**: Deployment Agent (WWA Platform embedded workspace)

**Architecture Summary**:
- Embedded UI and backend within WWA platform
- User-initiated file upload→process→verification→decision workflow
- Multi-stage release tracking (SIT/UAT/PROD)
- Human-in-the-loop task decision control
- Audit logging and configuration management
- External integration with Jenkins and Ansible via callback webhook

**Key Assumptions Carried Forward**:
- Vue 3 UI + **Java 21 / Spring Boot 3 / Spring Data JPA** API + Oracle persistence
- Fixed Excel template for MVP (dynamic schema out of scope)
- Task reruns create new execution history records (same task_id, incremented attempt_number)
- Atomic file-level import (no partial import)
- Single review owner per Release Flow
- Initial task execution may auto-trigger upon `Ready_For_Execution` status

---

## Design Assumptions

- [Resolved] ~~HTTP callbacks from remote execution engines (Jenkins/Ansible) carry execution_id for correlation~~ → MVP uses fire-and-forget submission; no callbacks. External job URL is stored for user click-through.
- [Assumption] Oracle is the primary transactional store for all workflow state and immutable audit records
- [Resolved] ~~Result payloads (execution logs) are stored in Oracle CLOB columns~~ → Full logs stay in Jenkins/Ansible. DA stores external job URL (VARCHAR2(2000)) for click-through. MANUAL task result summaries stored in CLOB as before.
- [Implemented] Vue 3 frontend uses Pinia for state management (user, releaseFlow, task, config, audit stores)
- [Implemented] Spring Boot 3.2.4 uses Spring Data JPA with repository patterns for Oracle/H2 persistence
- [Resolved] ~~WWA authentication and authorization infrastructure is reused~~ → Replaced with session-based Team Book login. SessionAuthFilter reads UserContext from HttpSession. StubTeamBookAuthenticationProvider for dev/test; real provider pending Team Book API contract.
- [Resolved] ~~Secrets stored in managed secret store external to Oracle~~ → Jenkins/Ansible credentials (user, API token) stored in DA_CONFIGURATION_ITEM table via Config admin page. No external secret store for MVP.
- [Implemented] Configuration values stored in DA_CONFIGURATION_ITEM table; read at execution time by adapters
- [Assumption] Task state transitions follow a linear, human-gated progression; no parallel branches in MVP

---

## Design Scope

**In-Scope Modules**:
1. Upload & Import Service – Excel parsing, validation, Release Flow creation/update *(implemented)*
2. Release Flow Service – state aggregation, stage progression *(implemented)*
3. Task Management Service – task CRUD, input editing, status transitions *(implemented)*
4. Decision Engine – decision processing (Approve/Reject/Rerun/Skip) and Release Flow progression *(implemented)*
5. Auto Execution Service – fire-and-forget submission to Jenkins/Ansible; external job URL storage *(implemented, replaces callback handler)*
6. Configuration Service – configuration CRUD and retrieval *(implemented)*
7. Audit Logger – event-based audit trail recording *(implemented)*
8. Auth Service – session-based Team Book login, role-based access control *(implemented with stub provider)*
9. Vue 3 UI Modules – login, workspace navigation, summary, details, task views, dialogs, auto submit, external job link *(implemented)*
10. Spring REST Controllers – 7 HTTP endpoint controllers *(implemented)*

**Deferred from MVP**:
- Execution Callback Handler – no callbacks in MVP; tasks stay in Executing after submission
- Result log ingestion – full logs stay in Jenkins/Ansible

**Out-of-Scope Details**:
- Performance tuning and caching policies
- Advanced filtering, export, and reporting UI
- Parallel task execution or branching workflows
- Real Team Book API integration (pending external contract)

**Design Boundaries**:
- Frontend → Backend: HTTP/REST API with JSON payloads, session cookies (withCredentials)
- Backend → Oracle: Spring Data JPA with repository patterns
- Backend → External Systems: Synchronous HTTP calls to Jenkins/Ansible (10s connect / 30s read timeout); fire-and-forget; no callbacks
- Backend → Team Book: TeamBookAuthenticationProvider interface; stubbed for dev/test, real implementation pending
- Audit Logger: Event capture occurs at domain layer; persistence in Oracle; uses authenticated session identity

---

## Module Design

### 1. Upload & Import Service

**Responsibilities**:
- Receive `(file, stage)` from the upload request — Stage is a required request parameter provided by the user at upload time; it is never read from Excel rows
- Parse the **AMH_HCC_task** sheet using the fixed column schema
- Validate all rows and cells against the known field list
- Extract `Project ID` and `Project Name` for Release Flow grouping
- Look up active Release Flow by `project_id`:
  - If none exists: create new Release Flow; generate `release_id` as `{stage}-{normalized_project_name}-{seq}`
  - If one exists: attach new Request to it for the selected Stage
- Create Request for the selected Stage within the Release Flow
- Create one Task per data row under the Request
- Create audit log entry for the upload action
- Return structured import result with summary (created/updated count, errors)

**Key Interactions**:
- Receives HTTP multipart upload with `file` and `stage` parameters
- Calls Release Flow Service to create/lookup/update Release Flow
- Calls Task Management Service to create Tasks (one per row)
- Calls Audit Logger to record upload event

**Internal Design Concerns**:
- **Stage source**: Stage is always from the HTTP upload parameter; never derived from file content
- **Release ID generation**: `{stage}-{normalized_project_name}-{seq}` where `{seq}` is padded-sequential per project; generated once on new Release Flow creation
- **Atomicity**: Import is atomic at file level; all rows succeed or fail together
- **Idempotency**: Rerun of same Excel file with same Stage updates existing records, matched by `(project_id, stage, task_group_id, step_seq)`
- **Validation**: Required fields, date formats, `execution_type` must be `MANUAL` or `AUTO`
- **Error Reporting**: Accumulated errors returned to user with row number and field name
- **Transactionality**: Use `DataSource.transaction()` to ensure atomicity

**MANUAL vs AUTO task treatment on import**:
- Both `MANUAL` and `AUTO` tasks are created in the same `Pending` state on import
- `execution_type` is stored on the Task entity and determines the execution path at runtime
- No state-machine difference during import; difference manifests at the execution phase

#### Excel Template Field Parsing Specification

| Template Column | Action | Parsed Into | Required | Validation Rule |
|---|---|---|---|---|
| `Project ID` | Map | `release_flow.project_id` | Yes | Non-blank string |
| `Project Name` | Map | `release_flow.project_name` | Yes | Non-blank string |
| `Task ID` | Map | `task.task_group_id` | Yes | Non-blank string; groups rows for display |
| `Task Name` | Map | `task.task_group_name` | Yes | Non-blank string |
| `Step seq#` | Map | `task.step_seq` | Yes | Positive integer; unique within `task_group_id` |
| `Step` | Map | `task.task_name` | Yes | Non-blank string |
| `Execution Type` | Map | `task.execution_type` | Yes | Must be `MANUAL` or `AUTO` (case-insensitive); reject otherwise |
| `Script to be executed` | Map | `task.input_parameters.script` | Conditional | Required when execution is automated |
| `Parameter (input)` | Map | `task.input_parameters.parameters` | No | String or parseable JSON |
| `Parameter (Expected Output)` | Map | `task.expected_output` | No | String; shown during result review |
| `Owner` | Map | `task.owner` | No | String |
| `Planned Start date/time` | Map | `task.planned_start_time` | No | ISO 8601 or Excel date format |
| `Planned End date/time` | Map | `task.planned_end_time` | No | ISO 8601 or Excel date format |
| `Activity category` | Store in metadata | `task.import_metadata.activity_category` | No | No validation |
| `Common` | Store in metadata | `task.import_metadata.common` | No | No validation |
| `Dependencies` | Store in metadata | `task.import_metadata.dependencies` | No | No validation |
| `Validation` | Store in metadata | `task.import_metadata.validation` | No | No validation |
| `Status` | **Ignore** | not stored | — | Template tracking artefact; system sets Task to `Pending`; this column is never read |
| `Start date/time` | **Drop** | not stored | — | Runtime value; system generates `start_time` at execution start |
| `End date/time` | **Drop** | not stored | — | Runtime value; system generates `end_time` from callback |
| `Release ID` | **System-generated** — not parsed from template | `release_flow.release_id` | N/A | Generated as `{stage}-{normalized_project_name}-{seq}` when Release Flow is first created |
| `Stage` | **From HTTP upload `stage` parameter** — not from template rows | `request.stage` | Yes (from request param) | SIT \| UAT \| PROD; validated from the upload request, not from Excel |

---

### 2. Release Flow Service

**Responsibilities**:
- Create Release Flow with initial state
- Retrieve Release Flow by ID or criteria
- Aggregate Request statuses to determine Release Flow summary status (Done/Running/Pending)
- Aggregate task statuses within a stage to determine Request/Stage summary status
- Update Release Flow state upon task/decision transitions
- Track Review Status and Review Owner
- Provide Release Flow detail for display

**Key Interactions**:
- Receives calls from Import Service, Task Management Service, Decision Engine, and UI via controllers
- Reads from Oracle Release Flow, Request, Task tables
- Calls Audit Logger for significant state transitions

**Internal Design Concerns**:
- **State Aggregation**: Define clear rules for aggregating child statuses to parent:
  - Request summary = aggregate of all task statuses in that Request
  - Stage summary = aggregate of all Request summaries in that stage
  - Release Flow summary = aggregate of stage summaries
  - Mixed states: `Running` if any running task; `Done` if all done; `Pending` if any pending
- **Hierarchical Navigation**: Maintain efficient parent-child relationships for querying
- **Caching**: Consider caching Release Flow list for summary view (short TTL due to frequent updates)

---

### 3. Task Management Service

**Responsibilities**:
- CRUD operations on Task records
- Maintain task input parameters and validation rules
- Support task status transitions (Pending → Ready_For_Execution → Executing → Awaiting_Review → (Approved/Rejected/Skipped))
- Edit task input parameters (only in editable states: Pending, Ready_For_Execution)
- Track latest execution reference and current result summary
- Coordinate with Task Execution History for reruns

**Key Interactions**:
- Receives calls from Import Service, UI controllers, Decision Engine, Execution Callback Handler
- Manages Task and Task Execution History records in Oracle
- Calls Audit Logger for edit actions

**Internal Design Concerns**:
- **Input Schema**: Define which fields are editable per task type; reuse existing error types from `src/errors/`
- **State Validation**: Enforce state transition rules; only allow edits in Pending/Ready_For_Execution states
- **Execution History**: On rerun, create new Task Execution History record (same task_id, incremented attempt_number)
- **Result Tracking**: Current_Result_Summary and Latest_Execution_Id fields updated by Execution Callback Handler

---

### 4. Decision Engine

**Responsibilities**:
- Process task-level decisions: Approve, Reject, Rerun, Skip
- Update task state based on decision
- Trigger Release Flow progression
- Determine next eligible task or stage advancement
- Call Execution Service for reruns
- Create audit log entries for all decisions

**Key Interactions**:
- Receives decision request from UI via controller
- Calls Task Management Service to update task state
- Calls Execution Service to trigger rerun (for Rerun decision)
- Calls Release Flow Service to advance Release Flow
- Calls Audit Logger for decision tracking

**Internal Design Concerns**:
- **Decision Validation**: Enforce role-based access, task state constraints, and release flow context
- **State Transitions**:
  - Approve: Task → Approved; Release Flow advances to next task
  - Reject: Release Flow → Rejected; all subsequent tasks in stage skipped
  - Rerun: Task → Executing; new execution history entry created
  - Skip: Task → Skipped; Release Flow continues to next task
- **Release Flow Progression**: Define logic to determine next eligible task (linear for MVP)
- **Idempotency**: Prevent duplicate decisions on same task; repeat calls return cached result

---

### 5. Execution Callback Handler

**Responsibilities**:
- Receive webhook callback from Jenkins/Ansible upon job/playbook completion
- Validate callback source and correlation (execution_id, task_id)
- Ensure request authenticity and prevent replay attacks
- Update Task status to reflect execution result
- Store execution result in Result Storage
- Update Task with result summary and latest execution reference
- Trigger Decision Engine if task reaches Awaiting_Review status
- Respond with 202 Accepted or 200 OK to callback source

**Key Interactions**:
- Accepts HTTP POST callback from external execution engines
- Calls Task Management to update current_result_summary and latest_execution_id
- Calls Result Storage to persist result logs
- Calls Task Management to transition task to Awaiting_Review
- Calls Decision Engine if auto-progression is enabled

**Internal Design Concerns**:
- **Idempotency**: Same callback received twice should be handled gracefully; use execution_id as idempotency key
- **Security**:
  - HTTPS only (enforced at infrastructure level)
  - Authenticate callback source (TBD: signed JWT, API key, mutual TLS)
  - Validate correlation: execution_id must match an active Task Execution History record
- **Error Handling**: Failed callbacks should be retried by caller; handler returns explicit error response
- **Timeout**: Callback endpoint is synchronous; should complete within acceptable window (suggest 30s)
- **Logging**: Log all callbacks (including failures) for debugging

---

### 6. Configuration Service

**Responsibilities**:
- CRUD Configuration Item records
- Retrieve current configuration for use by execution
- Validate configuration values against defined rules
- Limit updates to DevOps Admin role
- Support configuration schema (Jenkins URL, Ansible URL, Execution Callback Endpoint)

**Key Interactions**:
- Receives configuration requests from UI (Admin view) and domain services (Execution context)
- Reads/writes Configuration Item table in Oracle
- Calls Audit Logger for configuration changes

**Internal Design Concerns**:
- **Validation**: Each configuration item has format rules (URI format, HTTPS requirement, etc.)
- **Caching**: Load-on-startup with periodic refresh (or on demand with per-item caching) to avoid repeated DB queries
- **Immutability**: Store original values for audit; never overwrite in place
- **Update Semantics**: Changes take effect immediately for new executions (future tasks)
- **Authorization**: Enforce role check in controller; cannot rely on UI-side visibility

---

### 7. Audit Logger

**Responsibilities**:
- Record immutable audit log entries for all key actions
- Capture action metadata: operator, action type, timestamp, context
- Support audit log retrieval for display
- Ensure append-only persistence to Oracle

**Key Interactions**:
- Called by all domain services to record actions
- Receives log requests from UI via controller for display
- Writes Audit Log Entry records (append-only) to Oracle

**Internal Design Concerns**:
- **Event Capture**: Define supported action types: upload, edit, view_result, approve, reject, rerun, skip, config_update
- **Context Payload**: Store nested context (release_flow_id, request_id, task_id, additional metadata as JSON)
- **Immutability**: No updates or deletes; only appends
- **Indexing**: Index on timestamp and operator_id for efficient query
- **Role-Based Retrieval**: Audit/Management users can view all logs; other users see only their own actions or relevant context

---

### 8. Result Storage

**Responsibilities**:
- Persist task execution results (logs, summary, status)
- Retrieve full result for Result Viewer
- Associate result with Task Execution History record

**Key Interactions**:
- Receives result payload from Execution Callback Handler
- Reads result for UI result viewer
- Supports switching between execution attempts (handled at UI layer for MVP)

**Internal Design Concerns**:
- **Storage Strategy**: [Assumption] Store in Oracle using dedicated result table or CLOB within Task Execution History
  - If Oracle: use CLOB for large logs, index on task_id for efficient retrieval
  - If externalized (future): persist reference URL in Oracle and actual content in S3/file system
- **Format**: Store execution_status, result_summary, result_logs (raw output); schema to be finalized in implementation
- **Retrieval**: Default to latest execution attempt; Result Viewer explicitly specifies attempt_number if switching

---

### 9. Vue 3 UI Modules

**Main UI Views / Components**:

#### 9.1 Workspace Layout
- **Purpose**: Top-level container for all Deployment Agent features
- **Structure**:
  - Left sidebar: navigation menu (Summary, Task Management sections)
  - Top bar: workspace title, user profile, actions
  - Main content area: dynamic based on selected view

#### 9.2 Release Flow Summary View
- **Purpose**: Monitor all Release Flows with stage-level status
- **Components**:
  - Release Flow table with columns: Release ID, Project, Current Stage, SIT status, UAT status, PROD status
  - Filter controls (by project, stage, status)
  - Upload button to initiate new import
  - On row click: select Release Flow and update Details view

#### 9.3 Selected Release Flow Details View
- **Purpose**: Show context for selected Release Flow
- **Components**:
  - Detail cards: Project, Release ID, Current Stage, Current Request ID, Review Status, Review Owner
  - Breadcrumb or context indicator showing hierarchy

#### 9.4 Task Details and Results View
- **Purpose**: Display tasks for selected Request with actions
- **Components**:
  - Task table with columns: Task Name, Execution Type, Status, Result Summary, Start Time, End Time
  - Action controls per row (shown based on task state and user role):
    - **Edit** — for Pending / Ready_For_Execution tasks (TL only)
    - **View Result** — for tasks with available result output
    - **Record Result** — for MANUAL tasks in `Ready_For_Execution` state; opens inline form to enter actual result/output; on submit the system creates execution history and transitions task to `Awaiting_Review`
    - **Decision** dropdown — for tasks in `Awaiting_Review` state: Approve / Reject / Rerun / Skip (TL only)
  - Result modal: displays result summary, `expected_output` for comparison, and option to view raw logs
- **MANUAL task visual indicator**: rows with `execution_type = MANUAL` should display a clear visual label so the TL knows no automated execution will occur

#### 9.5 Upload Dialog
- **Purpose**: Initiate Excel file import with stage selection
- **Components**:
  - **Stage selector** (required dropdown: SIT / UAT / PROD) — must be selected before upload is enabled
  - File input control
  - Download Template button
  - View Sample button
  - Upload button (disabled until both Stage is selected and file is chosen)
  - Import status message (success/error detail)
- **Behavior**:
  - Stage selection is a prerequisite; upload is blocked without it
  - Stage is submitted as a request parameter alongside the file; it is not read from the Excel content
  - On success, display release_id of the created/updated Release Flow

#### 9.6 Task Edit Dialog
- **Purpose**: Edit task input parameters
- **Components**:
  - Form fields for editable input parameters (per task type)
  - Validation feedback (real-time or on submit)
  - Save and Cancel buttons

#### 9.6b Record Result Dialog (MANUAL tasks only)
- **Purpose**: Allow operator to record the outcome of a manually-executed step
- **Triggered by**: "Record Result" button on a MANUAL task row in `Ready_For_Execution` state
- **Components**:
  - Read-only display of `input_parameters.script`, `input_parameters.parameters`, and `expected_output` as reference context
  - Text area or form field for operator to enter the actual result/output
  - Save and Cancel buttons
- **Behavior on Save**:
  - System creates a `TaskExecutionHistory` record with `execution_type = MANUAL`, `attempt_number` incremented, and the entered result as `result_summary`
  - System transitions task from `Ready_For_Execution` to `Awaiting_Review`
  - Task row refreshes; Decision actions become available
- **Authorization**: TL (same role as decision-making)

#### 9.7 Decision Dialog
- **Purpose**: Confirm and submit task-level decision
- **Components**:
  - Radio or button selection: Approve / Reject / Rerun / Skip
  - Optional comment or rejection reason field (future scope)
  - Confirm and Cancel buttons

#### 9.8 Configuration Management View
- **Purpose**: Manage system configuration (DevOps Admin only)
- **Components**:
  - Configuration table with columns: Config Key, Current Value
  - Edit button per row to open inline or modal editor
  - Save/Cancel buttons for each edit
  - Validation feedback and error messages

#### 9.9 Audit Log View
- **Purpose**: Display audit records (read-only for Audit/Management users)
- **Components**:
  - Audit log table with columns: Timestamp, Operator, Action Type, Context (Release ID / Task Name)
  - Optional: pagination or lazy-load for large datasets
  - No edit/delete capabilities

**State Management**:
- [Assumption] Use Pinia or provide/inject to manage:
  - Currently selected Release Flow ID
  - Currently selected Request ID (context)
  - Release Flow list and detail
  - Task list for selected Request
  - Configuration items
  - Current user context (role, identity)

**API Integration**:
- Use REST client (axios or fetch) to call backend endpoints
- Handle HTTP errors and display user-friendly messages
- Poll or subscribe for Release Flow status updates (polling for MVP; consider WebSocket or SSE for real-time in future)

---

## API / Interface Design

### HTTP Endpoints

All endpoints use Fastify patterns: resource-oriented route handlers, explicit TypeScript DTOs, validation through Zod schemas, centralized Fastify error handler, server-side RBAC via `requireRole()`.

#### Upload Endpoint
- **Path**: `POST /api/deployment-agent/upload`
- **Purpose**: Submit Excel file for import at a specified stage
- **Input**: Multipart form with `file` field and required `stage` field (`SIT` | `UAT` | `PROD`)
- **Output**:
  - 200 OK: `{ success: true, message: "...", importLog: {...}, releaseFlowId: "...", releaseId: "...", stage: "..." }`
  - 400 Bad Request: `{ success: false, errors: [...], details: "..." }`
- **Validation**: Stage required and valid; file required; file format (XLSX); schema compliance; `execution_type` must be `MANUAL` or `AUTO`
- **Authorization**: Developer, TL (any authenticated user with upload permission)

#### Release Flow List Endpoint
- **Path**: `GET /api/deployment-agent/release-flows`
- **Purpose**: Retrieve Release Flow list with optional filters
- **Query Parameters**: `?project=X&stage=Y&status=Z&page=0&size=20`
- **Output**: `{ data: [{ id, project, releaseId, currentStage, sitStatus, uatStatus, prodStatus, flowStatus, reviewStatus }], total, page, size }`
- **Authorization**: Any authenticated user

#### Release Flow Detail Endpoint
- **Path**: `GET /api/deployment-agent/release-flows/{id}`
- **Purpose**: Retrieve full Release Flow details
- **Output**: `{ id, project, releaseId, currentStage, currentRequestId, flowStatus, reviewStatus, reviewOwner, createdAt, updatedAt }`
- **Authorization**: Any authenticated user

#### Task List Endpoint
- **Path**: `GET /api/deployment-agent/release-flows/{id}/requests/{requestId}/tasks`
- **Purpose**: Retrieve task list for selected Request
- **Output**: `{ data: [{ id, name, type, status, inputParameters, currentResultSummary, latestExecutionId, startTime, endTime, lastUpdatedAt }] }`
- **Authorization**: Any authenticated user

#### Edit Task Input Endpoint
- **Path**: `POST /api/deployment-agent/tasks/{id}/edit`
- **Purpose**: Update task input parameters
- **Input**: `{ inputParameters: {...} }`
- **Output**: 200 OK or 400 Bad Request with validation errors
- **Validation**: Schema matching, type checking, required field checking
- **Authorization**: TL, DevOps Admin (RoleBasedAccess)

#### Decision Endpoint
- **Path**: `POST /api/deployment-agent/tasks/{id}/decision`
- **Purpose**: Submit task-level decision
- **Input**: `{ decision: "Approve" | "Reject" | "Rerun" | "Skip", context: {...} }`
- **Output**: 200 OK with updated Release Flow state or error
- **Validation**: Decision validity for task state, authorization
- **Authorization**: TL, DevOps Admin (RoleBasedAccess)

#### Get Task Result Endpoint
- **Path**: `GET /api/deployment-agent/tasks/{id}/result`
- **Purpose**: Retrieve execution result for display
- **Query Parameters**: `?executionId=X` (optional; defaults to latest)
- **Output**: `{ taskId, executionId, attemptNumber, status, resultSummary, resultLogs }`
- **Authorization**: Any authenticated user (may be gated by result sensitivity in future)

#### Configuration List Endpoint
- **Path**: `GET /api/deployment-agent/config`
- **Purpose**: Retrieve current configuration items
- **Output**: `{ data: [{ key, value, description, updatedBy, updatedAt }] }`
- **Authorization**: Any authenticated user (read-only via API)

#### Update Configuration Endpoint
- **Path**: `POST /api/deployment-agent/config`
- **Purpose**: Create or update configuration item
- **Input**: `{ key, value, description }`
- **Output**: 200 OK or 400 Bad Request
- **Validation**: Format rules per configuration key, required/optional constraints
- **Authorization**: DevOps Admin only

#### Audit Log List Endpoint
- **Path**: `GET /api/deployment-agent/audit-logs`
- **Purpose**: Retrieve audit log entries
- **Query Parameters**: `?releaseFlowId=X&page=0&size=50`
- **Output**: `{ data: [{ id, operatorId, operatorRole, actionType, timestamp, releaseFlowId, requestId, taskId, contextPayload }], total }`
- **Authorization**: Audit/Management users (role-gated), or own actions for operators

#### Execution Callback Endpoint
- **Path**: `POST /api/deployment-agent/callback/execution`
- **Purpose**: Receive task execution result from Jenkins/Ansible
- **Input**: `{ executionId, taskId, status, resultSummary, resultLogs, timestamp }`
- **Output**: 200 OK or 202 Accepted
- **Validation**:
  - Execution ID must correlate with active Task Execution History
  - Status must be valid (Completed, Failed, Timed Out, etc.)
  - Request signature/token validation
- **Authorization**: Callback source authentication (API key or signed token)
- **Side Effects**:
  - Update Task Execution History with result
  - Store result in Result Storage
  - Update Task status to Awaiting_Review
  - Trigger Decision Engine (if auto-progression enabled)

---

## Data Design

### Logical Entities

#### Release Flow
- **Attributes**:
  - release_flow_id (PK)
  - project_id (from template `Project ID`; primary grouping key)
  - project_name (from template `Project Name`; display)
  - release_id (from workbook structure per OQ-25; nullable; fallback applied if absent)
  - current_stage (SIT | UAT | PROD; from workbook structure per OQ-25)
  - flow_status (Pending | Running | Completed | Failed | Rejected)
  - review_status (Pending_Review | Approved | Rejected)
  - review_owner (user_id)
  - created_at
  - updated_at
- **State Transitions**:
  - New Release Flows start in `Pending` status
  - Move to `Running` on task execution
  - Move to `Completed` when all stages done
  - Can be `Rejected` by reviewer
  - Can be `Failed` if any stage fails unrecoverably

#### Request
- **Attributes**:
  - request_id (PK)
  - release_flow_id (FK)
  - stage (SIT | UAT | PROD)
  - request_status (Pending | Running | Completed | Failed | Skipped | Rejected)
  - created_at
  - updated_at
- **State Transitions**:
  - New Requests start in `Pending`
  - Move to `Running` on first task execution
  - Move to `Completed` when all tasks done (Approved/Skipped)
  - Can be `Rejected` en masse
  - Can be `Failed` if critical task fails

#### Task
Represents one atomic execution step from the AMH_HCC_task template. One row = one Task.

- **Core workflow attributes**:
  - task_id (PK, system-generated)
  - request_id (FK)
  - task_group_id (VARCHAR; from template `Task ID`; groups related steps for display ordering)
  - task_group_name (VARCHAR; from template `Task Name`; display label)
  - step_seq (INTEGER; from template `Step seq#`; execution ordering within task_group_id)
  - task_name (VARCHAR; from template `Step`; name of this atomic step)
  - execution_type (VARCHAR; enum: `MANUAL` | `AUTO`; determines execution path at runtime — MANUAL = human-executed externally; AUTO = system-submitted to pipeline)
  - input_parameters (CLOB/JSON: `{ "script": "...", "parameters": "..." }` from template fields)
  - expected_output (VARCHAR; nullable; from template `Parameter (Expected Output)`; shown during result review)
  - task_status (Pending | Ready_For_Execution | Executing | Awaiting_Review | Approved | Rejected | Skipped | Failed)
  - current_result_summary (CLOB, nullable)
  - latest_execution_id (FK to Task Execution History, nullable)
  - start_time (TIMESTAMP, nullable; populated by execution service — NOT from template)
  - end_time (TIMESTAMP, nullable; populated from execution callback — NOT from template)
  - last_updated_at
  - editable_statuses: [Pending, Ready_For_Execution] (enforced in service layer)

- **Display-only attributes** (explicit columns; no workflow role):
  - owner (VARCHAR; nullable; from template `Owner`)
  - planned_start_time (TIMESTAMP; nullable; from template `Planned Start date/time`; shown in task list)
  - planned_end_time (TIMESTAMP; nullable; from template `Planned End date/time`; shown in task list)

- **Raw import metadata** (single JSON blob; no business logic reads this in MVP):
  - import_metadata (VARCHAR2/CLOB; JSON object containing `activity_category`, `common`, `dependencies`, `validation` from the template; preserved for reference only)

- **Fields NOT stored** (explicitly excluded):
  - template `Status` — ignored; system always creates Tasks in `Pending`
  - template `Start date/time` — not imported; system generates actual start
  - template `End date/time` — not imported; system generates actual end from callback

- **State Transitions**:
  - Pending → Ready_For_Execution (on import or progression rule)
  - Pending → Skipped (TL skip decision)
  - Ready_For_Execution → Executing (auto-triggered or manual record-result)
  - Ready_For_Execution → Skipped (TL skip decision)
  - Executing → Awaiting_Review (callback from engine or manual result recording)
  - Executing → Failed (execution failure)
  - Awaiting_Review → Approved | Rejected (TL decision)
  - Rejected → Ready_For_Execution (TL rerun decision; creates new execution history)
  - Failed → Ready_For_Execution (TL rerun decision; creates new execution history)

#### Task Execution History
- **Attributes**:
  - execution_id (PK)
  - task_id (FK)
  - attempt_number
  - execution_status (Running | Completed | Failed | Timed_Out)
  - input_snapshot (JSON, copy of inputs at execution time)
  - result_summary (JSON, nullable)
  - result_logs (CLOB, nullable)
  - start_time
  - end_time (nullable)
- **Keys**:
  - Composite unique key: (task_id, attempt_number)
  - Latest execution identified by max(attempt_number) for given task_id

#### Configuration Item
- **Attributes**:
  - config_key (PK, e.g., "jenkins_url", "ansible_url", "callback_endpoint")
  - config_value
  - description
  - updated_by (user_id)
  - updated_at
- **Validation**:
  - jenkins_url: URI format, HTTPS recommended
  - ansible_url: URI format, HTTPS recommended
  - callback_endpoint: URI format, HTTPS required

#### Audit Log Entry
- **Attributes**:
  - audit_log_id (PK)
  - operator_id
  - operator_role
  - action_type (enum: upload, edit, view_result, approve, reject, rerun, skip, config_update)
  - timestamp
  - release_flow_id (FK, nullable)
  - request_id (FK, nullable)
  - task_id (FK, nullable)
  - context_payload (JSON, arbitrary context for the action)
- **Immutability**: Append-only; no updates or deletes after creation
- **Indexing**: timestamp, operator_id, action_type for efficient query

### Entity Relationships (ER)
```
Release Flow
  ├── 1:N → Request (by stage)
       ├── 1:N → Task
            ├── 1:N → Task Execution History
            └── Result Storage (CLOB or reference)

Configuration Item (independent)
Audit Log Entry (independent, soft references to Release Flow / Request / Task)
```

### Execution Payload Mapping

`execution_type` has two values and determines completely different execution paths:

#### AUTO tasks (execution_type = `AUTO`)
The Execution Service submits the task to the configured execution pipeline:

| Task Field | Execution Payload Role |
|---|---|
| `input_parameters.script` | The script or job name to invoke |
| `input_parameters.parameters` | Runtime parameters passed to the script |
| `task_id` | Correlation reference for callback matching |
| `execution_id` | System-generated per attempt; included in callback for result correlation |
| `task_group_id` + `step_seq` | Contextual metadata for observability |

The Execution Service reads `jenkins_url` / `ansible_url` from Configuration Items and submits to the appropriate endpoint. A callback is expected to mark the task result and transition to `Awaiting_Review`.

#### MANUAL tasks (execution_type = `MANUAL`)
No automated submission. The Execution Service treats MANUAL tasks differently:

- Task transitions to a "manual execution required" state (or stays at `Ready_For_Execution` with a MANUAL indicator)
- The system displays `input_parameters.script`, `input_parameters.parameters`, and `expected_output` as reference instructions to the operator
- The operator performs execution externally
- The TL or operator records the result through an inline "Record Result" action in the UI
- The system then transitions the task to `Awaiting_Review` for the normal decision gate
- No callback endpoint is triggered for MANUAL tasks

> **R-07 resolved**: MANUAL task result recording uses an inline "Record Result" button in the Task Details row. Clicking it opens a form where the operator enters the actual result/output. On form submission, the system creates a `TaskExecutionHistory` record and transitions the task to `Awaiting_Review`.

---

### Expected Output and Verification Handling

`expected_output` (from template `Parameter (Expected Output)`) is a first-class field in the result review step.

**Design decision**:
- `expected_output` is stored on the Task entity and displayed side-by-side with the actual execution result in the Result Viewer
- The TL manually compares actual vs. expected output during the verification step of the core workflow
- The system does **not** auto-pass or auto-fail based on expected_output; the human decision gate is the authoritative verification mechanism in MVP

---

### Dependency, Validation, and Common Handling

`Dependencies`, `Validation`, and `Common` from the template are **raw metadata fields with no workflow behavior in MVP**.

**Design decision for all three**:
- Stored in the `import_metadata` JSON blob on the Task entity
- No gating logic, no automated processing, no state-transition control
- These fields are preserved for future reference or post-MVP enhancement only
- No open questions remain for these fields — the decision is final for MVP scope

Execution sequencing in MVP is controlled solely by `step_seq` within `task_group_id`. No additional dependency resolution engine is needed.

---

### Scheduling Field Usage

`planned_start_time` and `planned_end_time` (from template `Planned Start date/time` / `Planned End date/time`) are display-only fields.

**Design decision**:
- Stored as explicit timestamp columns on the Task entity for UI display in the task list
- The system does **not** auto-start, delay, or gate execution based on planned dates in MVP
- `start_time` (actual) is populated by the Execution Service when execution begins
- `end_time` (actual) is populated from the execution callback when execution completes
- Template `Start date/time` and `End date/time` columns are **not imported** — they are runtime values that the system generates, not planning inputs from the spreadsheet

---

### State Aggregation Rules

**Request Summary Status** (from Task statuses within Request):
- If any Task is `Executing` or in running state → `Running`
- If all Tasks are `Approved` or `Skipped` → `Completed`
- If any Task is `Failed` → `Failed`
- If any Task is `Rejected` → `Rejected`
- Otherwise → `Pending`

**Stage Summary Status** (from Request summaries within Stage):
- If any Request is `Running` → `Running`
- If all Requests are `Completed` → `Done`
- If any Request is `Failed` → `Failed`
- If any Request is `Rejected` → `Rejected`
- Otherwise → `Pending`

**Release Flow Summary** (from Stage statuses):
- Aggregate all three stages (SIT, UAT, PROD) similarly to stage aggregation

---

## UI / User Flow Design

### User Journey: Request → Process → Verification → Decision

#### 1. Access Workspace
- User logs into WWA platform
- Selects Deployment Agent from level-2 menu
- Workspace loads with Release Flow Summary view visible

#### 2. Upload Deployment Request
- User clicks "Upload" or "Upload Excel" button
- Upload dialog opens
- User can: Download Template, View Sample, or select Excel file
- User selects valid Excel file and clicks Upload
- System processes import and displays success message with import log link
- New or updated Release Flows appear in Summary list

#### 3. View Release Flow Progress
- User sees Release Flow list with SIT/UAT/PROD stage statuses
- Can apply filters (by project, stage, status)
- Clicks on Release Flow row to select it
- Details section updates with Release Flow context

#### 4. View and Edit Task Input
- Selected Release Flow Details shows Current Request
- Task Details section shows tasks in that Request
- User clicks "Edit" on a task in Pending or Ready_For_Execution state
- Edit dialog opens with input parameters
- User modifies parameters, sees validation feedback, saves
- Task input updated; audit log entry created

#### 5. Monitor Task Execution
- Task status shows Executing
- External engine (Jenkins/Ansible) runs job
- Upon completion, external engine sends callback to Execution Callback Endpoint
- System updates task status to Awaiting_Review with result summary
- UI reflects updated status (polling or SSE-based refresh)

#### 6. Review Result and Make Decision
- User clicks "View Result" on completed task
- Result modal shows result summary and raw logs
- User makes decision: Approve | Reject | Rerun | Skip
- Decision dialog opens; user confirms choice
- System processes decision:
  - **Approve**: Task marked Approved; Release Flow continues to next task
  - **Reject**: Release Flow marked Rejected; all remaining tasks skipped
  - **Rerun**: Task re-executes (new execution history entry)
  - **Skip**: Task marked Skipped; Release Flow continues to next task
- Audit log entry created
- UI updates to reflect new state

#### 7. Review Audit Trail
- User navigates to Audit Log view
- Sees chronological list of actions: uploads, edits, decisions, config updates
- Each entry shows operator, action type, timestamp, and context

### State Visibility Rules (UI-Side)

**Available Actions by Task Status**:
- Pending / Ready_For_Execution: Edit, View Result (if available), Decision (if applicable)
- Executing: View Result (if streaming available; otherwise disabled)
- Awaiting_Review: View Result, Decision (Approve/Reject/Rerun/Skip)
- Approved / Rejected / Skipped: View Result (read-only); no actions

**Release Flow Details Visibility**:
- Always visible: Project, Release ID, Current Stage, Review Status, Review Owner
- Dynamic: Current Request ID updates as Release Flow progresses

**Decision Options Visibility**:
- Approve / Reject / Rerun / Skip enabled only for tasks in Awaiting_Review state
- Role check (TL/Admin) enforced server-side; UI hides actions for other roles

---

## Workflow / Execution Design

### Import Processing Workflow

**Trigger**: User uploads Excel file

**Steps**:
1. Receive multipart file upload in Upload Controller
2. Validate file format (XLSX)
3. Parse Excel using fixed schema:
   - Extract columns: Project, Release ID, Stage, Task Name, Task Type, Input Parameters
   - Rows represent tasks
4. Validate data:
   - Required fields present
   - Release ID present or apply fallback rule
5. Group rows into Release Flows using grouping rule:
   - [TBD] Grouping logic: by (Project, Release_ID) or another key
6. For each Release Flow group:
   - Lookup existing Release Flow by (Project, Release_ID)
   - If not found: create new with initial state Pending
   - If found and status allows: update with new/modified Requests and Tasks
7. For each Request in Release Flow:
   - Create new Request record with stage from Excel
8. For each Task in Request:
   - Create new Task record with status Pending and input_parameters
9. Create single Audit Log entry summarizing the import
10. Return success response with created/updated IDs and import summary

**Atomicity**: All-or-nothing at file level; validation failures abort entire import

**Error Handling**:
- Schema validation errors: return 400 with detailed error list (row, column, error message)
- Business rule violations: accumulate and return as part of validation response

---

### Task Execution Workflow (High-Level)

**Trigger**: Task transitions to Ready_For_Execution (manual or auto)

**Steps**:
1. Execution Service (external, or within Backend Orchestration) receives task execution request
2. Read task from Task Management Service
3. Read configuration values from Configuration Service
4. Prepare execution payload: task_id, execution_id, input_parameters, config
5. Call external engine (Jenkins/Ansible):
   - POST job/playbook with payload
   - Provide callback URL (Execution Callback Endpoint)
   - External engine runs asynchronously
6. Return 202 Accepted to caller (if called from API)
7. [Async] External engine completes job
8. [Async] External engine POSTs result to Execution Callback Endpoint with execution_id, status, result_summary, result_logs
9. Execution Callback Handler validates and processes:
   - Verify execution_id matches active Task Execution History
   - Store result in Result Storage
   - Update Task status → Awaiting_Review
   - Update Task current_result_summary and latest_execution_id
   - [If auto-progression] Trigger Decision Engine to auto-approve/continue
10. Task state now reflects execution result; UI updates on poll/refresh

**Failure Handling**:
- Callback timeout: external engine implements retry; callback endpoint is idempotent
- Invalid callback: return 400 Bad Request; external system should not retry invalid requests
- Missing task/execution: return 404; external system should investigate correlation

---

### Decision Processing Workflow

**Trigger**: User submits Decision action (Approve/Reject/Rerun/Skip)

**Steps**:
1. UI collects decision choice and submits to Decision Endpoint
2. Decision Controller validates:
   - User role (TL only)
   - Task exists and current state is valid for the decision (Awaiting_Review for approve/reject; Rejected/Failed for rerun; Pending/Ready_For_Execution for skip)
3. Decision Engine processes:
   - **Approve**:
     - Update Task status → Approved
     - Determine next task in Request
     - If next task exists: update it to Ready_For_Execution or trigger execution
     - If no next task: update Request status → Completed
     - Determine if all Requests in stage completed; if yes, advance Release Flow stage
   - **Reject**:
     - Update Task status → Rejected
     - Update Release Flow status → Rejected
     - Skip all remaining tasks in Release Flow
   - **Rerun** (only from `Rejected` or `Failed` state):
     - Create new Task Execution History record (attempt_number + 1)
     - Update Task status → Ready_For_Execution
     - Execution pipeline picks up the task again (auto-trigger or manual record-result)
   - **Skip**:
     - Update Task status → Skipped
     - Determine next task; update similarly to Approve
4. Create Audit Log entry for decision
5. Return updated Release Flow state to UI
6. UI refreshes to display new state

**Idempotency**: Same decision submitted twice should result in idempotent operation; leverage execution_id as idempotency key for reruns

---

## Integration Design

### Jenkins/Ansible Execution Integration

**Purpose**: Orchestrate external job execution and receive results

**Interaction Pattern**:
- Synchronous request initiation (Job Submission API)
- Asynchronous result delivery (Execution Callback Webhook)

**Request Initiation** (Backend → Jenkins/Ansible):
- Endpoint: `POST https://jenkins.example.com/api/job` or equivalent
- Payload:
  ```json
  {
    "jobName": "deploy-app",
    "parameters": { "app": "myapp", "version": "1.2.3" },
    "callbackUrl": "https://deployment-agent.wwa.com/api/deployment-agent/callback/execution",
    "executionId": "exec-12345"
  }
  ```
- Auth: [TBD] API key or OAuth token (stored in secret store)
- Success: Returns job ID and 202 Accepted

**Result Callback** (Jenkins/Ansible → Backend):
- Endpoint: `POST /api/deployment-agent/callback/execution`
- Payload:
  ```json
  {
    "executionId": "exec-12345",
    "taskId": "task-789",
    "status": "Completed | Failed | Timed_Out",
    "resultSummary": { "output": "Deployment succeeded", "returnCode": 0 },
    "resultLogs": "full console output as string"
  }
  ```
- Auth: Request validation (TBD: signed token, API key, mutual TLS)
- Idempotency: Use executionId; repeated callbacks result in 200 OK (no duplicate processing)
- Response: `{ status: "accepted", message: "Result recorded" }` (200 OK)

### Secret / Credential Handling

**Current Scope**: Open; implementation to finalize

**Assumptions**:
- Jenkins credentials (API key) stored in external secret store (Vault, platform secrets, env vars)
- Ansible credentials similarly managed
- Backend loads credentials at startup or on-demand via envelope pattern
- Credentials never logged or exposed in audits

**Design Note**: Detailed secret access pattern deferred to implementation phase; likely via Spring Cloud Config or a secrets management adapter

### Configuration Management Integration

**Purpose**: Centralize system configuration for operational flexibility

**Interaction Pattern**: Synchronous read/write via Configuration Service

**Configuration Retrieval** (Domain Service → Config Service):
- When Execution Service prepares a job submission, it reads Jenkins URL, Ansible URL from Configuration Service
- Config Service returns current value; caching layer (optional) reduces repeated DB queries
- If config missing or invalid: default behavior or error (TBD)

**Configuration Update** (Admin UI → Config Service):
- Admin submits updated config value via Configuration Endpoint
- Config Service validates format (URI, HTTPS requirement, etc.)
- Persists to Configuration Item table
- Applies immediately to future executions

---

## Security / Audit / Reliability Design

### Access Control

**Role-Based Access Control (RBAC)**:
- **Developer**: Can upload files, edit own tasks, view Release Flows/results
- **TL** (Team Lead): Can view all Release Flows, edit all tasks, make decisions (Approve/Reject/Rerun/Skip)
- **DevOps Admin**: Full access including configuration management
- **Audit/Management**: Read-only access to audit logs and Release Flow summaries

**Enforcement**:
- Frontend (Vue 3) enforces visibility via conditional rendering (usability only)
- Backend (Fastify) enforces authorization in every handler via `requireRole()` middleware
- Authorization rule examples:
  - Edit Task: `requireRole(req, "task_edit", "TL")`
  - Update Config: `requireRole(req, "config_update", "DEVOPS_ADMIN")`
  - View Audit: `requireRole(req, "audit_log_view", "AUDIT", "MANAGEMENT", "DEVOPS_ADMIN")`

**Assumption**: User identity and roles available from WWA authentication context (automatically injected by platform)

### Secrets Handling

**Approach**: [Assumption] Envelope pattern with external secret store

**Details**:
- Credentials (Jenkins API key, Ansible token) never stored in Oracle
- At runtime, Backend retrieves credentials from secret store when preparing job submission
- Callback payloads do not contain credentials; caller adds credentials to request headers
- Audit logs may reference secret names (e.g., "jenkins_api_key") but never values

**Implementation TBD**: Spring Cloud Config, HashiCorp Vault, platform-managed secrets, or environment variables

### Audit Logging

**What is Logged**:
- upload action: File name, line count, created/updated counts, operator
- edit action: Task ID, changed fields, old/new values, operator
- view_result action: Task ID, timestamp, operator
- Approve/Reject/Rerun/Skip actions: Task ID, decision, operator, timestamp
- config_update action: Config key, old/new value, operator

**Where**: Oracle Audit Log Entry table (append-only)

**When**: Immediately after action completes successfully

**Immutability**: No updates or deletes; audit log is append-only

**Retrieval**: Audit Log controller provides role-gated access (Audit/Management users see all; others see own actions or context-relevant)

### Resilience & Reliability

**Retries**:
- Callback-driven: External engine responsible for retries on network failure
- Idempotency key (executionId): Repeated callbacks with same ID result in same state (no duplicates)

**Timeouts**:
- Callback endpoint: 30 seconds (configurable)
- Job submission to Jenkins/Ansible: 30 seconds (configurable)
- Task result retrieval: 5 seconds (can fail if result not ready; client retries)

**Circuit Breakers** [Optional, future]:
- If Jenkins/Ansible callback endpoint unreachable: log error, optionally alert; do not block task

**Observability**:
- Log all callback attempts (success, failure, correlation info)
- Log all decision outcomes
- Structured logging for stacktraces and domain context
- Audit logs serve as compliance/traceability trail

---

## Validation and Error Handling

### Input Validation

**File Upload**:
- File must be XLSX (MIME type check + file extension check)
- File size < 10MB (example; to be finalized)
- Sheet must contain expected columns

**Excel Data**:
- Required fields present in each row
- Data types match schema (e.g., Stage must be SIT/UAT/PROD)
- Release ID present or fallback rule applied

**Task Input Edit**:
- Input parameters match declared schema for task type
- Required fields present
- Type validation (e.g., numeric fields are numeric)
- Custom business rule validation (e.g., version format)

**Configuration Update**:
- Configuration value matches format rule (e.g., valid URI for URLs)
- HTTPS enforced if required
- No null values for required config items

**Decision Submission**:
- Decision value is one of: Approve, Reject, Rerun, Skip
- Task is in Awaiting_Review state
- User role allows decision

### Error Handling & User Feedback

**Validation Errors**:
- Return 400 Bad Request with structured error response
- Example: `{ success: false, errors: [{ field: "stage", message: "Invalid stage; must be SIT/UAT/PROD" }] }`
- User sees clear, actionable error messages in UI

**Authorization Errors**:
- Return 403 Forbidden if user lacks required role
- User sees "You do not have permission to perform this action"

**Not Found Errors**:
- Return 404 if Release Flow / Task / Config not found
- User sees "The requested item does not exist" or navigated away before requesting

**Conflict Errors**:
- Return 409 if state conflict (e.g., task already completed, cannot decide again)
- Design: Idempotency key ensures repeated decisions are safe

**Server Errors**:
- Return 500 with structured error; log full stacktrace server-side
- User sees "An unexpected error occurred; please try again or contact support"

**Callback Errors**:
- 400 Bad Request: Invalid payload structure or missing correlation
- 404 Not Found: Execution ID does not match any active task
- 409 Conflict: Task already completed or in inconsistent state
- 500 Server Error: Unexpected issue; callback source should retry

---

## Testing Considerations

### Key Test Areas

1. **Import Service**:
   - Valid Excel parsing and Release Flow creation
   - Invalid/malformed Excel rejection with appropriate errors
   - Duplicate import (same file uploaded twice) updates existing records
   - Atomic failure on validation error (no partial import)

2. **Release Flow State Aggregation**:
   - Task state changes propagate to Request and Release Flow summaries
   - Mixed task states aggregate correctly (Running > Completed > Pending)
   - Stage status reflects Request statuses correctly

3. **Task Management**:
   - Task input edit allowed only in Pending/Ready_For_Execution states
   - Task status transitions follow allowed paths
   - New execution history records created on rerun

4. **Execution Callback**:
   - Callback with valid correlation updates task and result
   - Callback with invalid execution_id returns 404
   - Duplicate callback (same execution_id) handled idempotently
   - Task status transitions to Awaiting_Review after callback

5. **Decision Processing**:
   - Approve advances Release Flow to next task
   - Reject marks Release Flow as rejected and skips remaining tasks
   - Rerun creates new execution history and re-executes task
   - Skip skips current task without executing

6. **Audit Logging**:
   - All supported actions create audit log entries
   - Audit log is append-only (immutability)
   - Operator identity captured correctly

7. **Authorization**:
   - Roles enforce access to sensitive endpoints (configuration update, decisions)
   - Cross-tenant isolation (one Release Flow cannot access another's data)

### Critical Test Scenarios

- **End-to-End Workflow**: Upload → Release Flow creation → Task execution → Callback → Decision → Progression
- **Rerun After Failure**: Task fails, decision Rerun, new execution created, succeeds
- **Reject Cascade**: Task rejected, all subsequent tasks in Release Flow marked skipped
- **Concurrent Decisions**: Two users attempt to decide on same task simultaneously (only one wins; second gets conflict)
- **Long-Running Callback**: Callback delayed >30 seconds; retry mechanism handles gracefully

### State Transition Coverage

- All valid transitions (Pending → Ready → Executing → Awaiting_Review → Approved/Rejected/Skipped verified)
- Invalid transitions rejected with error (e.g., cannot transition directly from Pending to Rejected)
- Rerun loop test: Can task be rerun multiple times? (Yes, new execution history records created)

---

## Risks / Design Tradeoffs

### Design Risks

1. **Callback Reliability**: Depends on external engine retry logic. If Jenkins/Ansible does not retry failed callbacks, tasks may hang.
   - **Mitigation**: Document callback retry requirements; monitor for hanging tasks; implement polling fallback if needed (future).

2. **State Consistency Under Concurrent Writes**: Multiple users making decisions on same Release Flow simultaneously.
   - **Mitigation**: Optimistic locking (version field) or database locks on Update operations; decision endpoints validate state before update.

3. **audit Log Growth**: Append-only audit logs may grow unbounded over time.
   - **Mitigation**: Implement archival/retention policy; index on timestamp for efficient purge; consider partitioning.

4. **Excel Schema Flexibility**: Fixed Excel schema limits user flexibility; changes require code updates.
   - **Mitigation**: Document schema clearly; plan for dynamic schema in future version.

5. **Secrets Storage**: Currently unresolved; default behavior may be insecure.
   - **Mitigation**: Confirm secret store technology during implementation; do not use environment variables in production without encryption.

### Notable Tradeoffs

| Tradeoff | Choice | Rationale |
|---|---|---|
| **File-level vs. Row-level Import Atomicity** | File-level atomic | Simpler to reason about; prevents partial imports causing confusion |
| **Manual vs. Auto Task Execution** | [Assumption] Auto on Ready_For_Execution | Reduces clicks; design assumes auto but can be switched to manual via toggle |
| **Task Input Edi Permission** | TL / DevOps Admin | Prevents developers from re-importing if data incorrect; allows controlled editing by leads |
| **Callback Sync vs. Async** | Synchronous POST | Simpler integration; allows immediate response to caller; acceptable load for MVP |
| **Single Review Owner** | Assumption for MVP | Simpler authorization; group-based review deferred to future |
| **Oracle vs. Externalized Result Storage** | Oracle (via CLOB or dedicated table) | Simpler deployment; no external dependencies; acceptable for MVP volumes |

---

## Open Questions

1. **Excel Template Schema**: What are the exact mandatory fields, optional fields, and data types?
   - Impact: Import Service validation implementation
   - **Stakeholder**: Product / Requirements team
   - **Deliverable**: JSON Schema or spreadsheet with field definitions

2. **Release Flow Grouping Rule**: What criteria determine when to create a new Release Flow vs. update existing?
   - Example: By (Project, Release_ID) or by (Project, Release_ID, Stage)?
   - **Stakeholder**: Product / Domain expert
   - **Deliverable**: Formal rule specification

3. **Release ID Fallback Rule**: If Release ID is missing in Excel, how should it be handled?
   - Options: Generate UUID, use Row Number, require manual input, use Project + Date
   - **Stakeholder**: Product
   - **Deliverable**: Explicit fallback rule

4. **Auto-Execution Confirmation**: Should tasks auto-transition from Ready_For_Execution → Executing, or require manual trigger?
   - If manual: Need UI button and endpoint for "Execute Task"
   - **Stakeholder**: Product / UX
   - **Deliverable**: Boolean flag + UI/backend implementation if manual

5. **Secret Store Technology**: Vault, environment variables, or platform-managed secrets?
   - **Stakeholder**: DevOps / Infrastructure team
   - **Deliverable**: Secret store integration design (out of scope for initial design)

6. **Configuration Update Semantics**: Do configuration changes apply immediately, or at task boundary?
   - **Stakeholder**: Product / DevOps
   - **Deliverable**: Policy clarification

7. **Result Payload Format**: Are result_summary and result_logs fixed JSON structures, or flexible?
   - **Stakeholder**: Integration team (Jenkins/Ansible)
   - **Deliverable**: Result schema specification and callback contract

8. **Stage Tie-Breaking**: If stage has mixed state (some tasks Done, some Failed, some Rejected), what priority applies?
   - Current assumption: Running > Failed > Rejected > Done > Pending
   - **Stakeholder**: Product
   - **Deliverable**: Confirmation or revised tie-breaking rule

9. **User Role Reuse from WWA**: Can we assume user roles (Developer, TL, Admin) and identity are available from WWA authentication context?
   - **Stakeholder**: Platform team
   - **Deliverable**: Confirmation of available user context fields

10. **Oracle Schema Finalization**: Should we use a separate result table or CLOB within Task Execution History?
    - **Stakeholder**: Database / DBA team
    - **Deliverable**: Oracle DDL and indexing strategy

---

## Summary

This design document translates the architecture into actionable module designs, API contracts, data models, and workflows. It establishes clear boundaries between frontend, backend, and persistence layers, and defines state transitions and validation rules necessary for implementation. Key areas requiring confirmation before development (Excel schema, grouping rules, secret store, auto-execution behavior) are surfaced as open questions and pre-design deliverables. The design is structured to support the core MVP workflow, with clear extension points for future enhancements (dynamic templates, advanced audit filtering, real-time notifications, etc.).
