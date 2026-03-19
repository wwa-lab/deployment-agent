# Detailed Design: Deployment Agent

## 1. Overview

This document provides an implementation-oriented design for the Deployment Agent MVP, translating the approved specification and architecture into concrete module responsibilities, API contracts, data models, workflows, validation rules, and frontend/backend boundaries.

The system is implemented with:
- **Frontend**: Vue 3
- **Backend**: Spring Boot 3
- **Database**: Oracle

The MVP supports a controlled, human-in-the-loop deployment workflow:

**request → process → verification → decision**

The design covers:
- Excel upload and import
- Release Flow / Request / Task orchestration
- task result viewing
- task input editing
- explicit decision processing (Approve / Reject / Rerun / Skip)
- external execution integration (Jenkins / Ansible)
- configuration management
- audit logging and audit log viewing

---

## 2. Source Architecture

**System**: Deployment Agent (embedded in WWA workspace)

**Architecture Summary**:
- Embedded Vue 3 UI inside WWA
- Spring Boot 3 backend services and REST APIs
- Oracle as primary transactional system-of-record
- Layered service structure with domain orchestration
- Callback-based external execution result handling
- Human decision gate after execution completion
- Immutable audit trail for key actions

**Key architecture carry-forward decisions**:
- Release Flow → Request → Task hierarchical domain model
- Task reruns create new execution history records while keeping the same logical task
- Atomic file-level import behavior
- Shared WWA configuration and navigation model
- Summary-level status display remains simplified for MVP

---

## 3. Design Gate Decisions

The following decisions must be treated as design-gate items.  
Implementation should not diverge from these decisions unless the document is revised.

### DG-01 Auto-Execution Behavior
For MVP, once a task enters `Ready_For_Execution`, execution is **auto-triggered by backend orchestration**.

Implications:
- No separate `Execute Task` button is required in Vue 3 UI for MVP.
- Spring Boot orchestration is responsible for moving the task into execution.
- Human-in-the-loop control applies **after execution**, not before execution.
- The system must still **not auto-progress to the next step without explicit human decision** after execution completes.

### DG-02 Release Flow Grouping Rule
Import grouping rule for MVP:

A Release Flow is identified by:

`(project, normalized_release_id)`

Where:
- `project` comes from the uploaded Excel row
- `normalized_release_id` is:
  - the provided `Release ID` if present
  - otherwise a fallback-generated release ID defined by DG-03

This means:
- Rows with the same `(project, normalized_release_id)` belong to the same Release Flow
- Stage does **not** create a separate Release Flow
- Stage creates or updates a Request under the same Release Flow

### DG-03 Release ID Fallback Rule
If `Release ID` is missing in the uploaded Excel, the backend generates:

`{project}_{yyyyMMdd}_{rowGroupHash}`

Where:
- `project` = normalized project name
- `yyyyMMdd` = import date
- `rowGroupHash` = deterministic hash derived from grouped row content

Purpose:
- deterministic for a given import content group
- suitable for grouping within the same file import
- avoids random duplication caused by pure UUID generation

### DG-04 Rerun History Model
- A logical task keeps the same `task_id` across reruns
- Each rerun creates a new `TaskExecutionHistory` record
- `attempt_number` increments by 1 for each rerun
- Result Viewer defaults to latest attempt
- Attempt-switching UI is supported as a design extension point, but latest attempt is the default display

### DG-05 Summary Status Contract
For MVP, summary-level stage display uses only:
- `Done`
- `Running`
- `Pending`

Detailed statuses like `Rejected` and `Failed` exist in internal workflow state and detail views, but are not exposed as top-level summary states in the Release Flow Summary table.

### DG-06 Result Storage Boundary
For MVP:
- `Task` stores current summary metadata and latest execution reference
- `TaskExecutionHistory` stores per-attempt execution metadata
- Full raw logs are stored in Oracle using a result-oriented persistence model
- Result payload storage may be implemented as either:
  - `CLOB` in a dedicated result table, or
  - `CLOB` in `TaskExecutionHistory`
- Final DDL choice is an implementation decision, but source-of-truth ownership must remain consistent

### DG-07 RBAC Contract
For MVP:
- **Developer**: upload + view
- **Tech Lead (TL)**: view + edit task input + make decisions
- **DevOps Admin**: configuration management + operational viewing
- **Audit / Management**: audit log viewing

Developer does **not** edit task input and does **not** perform Approve / Reject / Rerun / Skip in MVP.

---

## 4. Design Assumptions

- Oracle is the primary transactional persistence store for workflow state and audit records
- Spring Boot 3 uses layered application structure (controller / service / repository / integration)
- Spring Data JPA is the preferred persistence pattern unless implementation constraints require otherwise
- Vue 3 frontend uses centralized page-level state management, preferably Pinia
- WWA provides authenticated user identity and role context in backend request scope
- Secrets are not stored in Oracle application tables
- Configuration items are persisted in Oracle and used for future executions
- Task progression is linear within MVP; no parallel branch workflow
- Full Excel schema artifact and callback contract artifact will exist before implementation begins

---

## 5. Design Scope

### In-Scope Modules
1. Upload & Import Service
2. Release Flow Service
3. Task Management Service
4. Decision Engine
5. Execution Service
6. Execution Callback Handler
7. Configuration Service
8. Audit Logger
9. Result Storage
10. Vue 3 UI Modules
11. Spring Boot Controllers

### Out-of-Scope Details
- Dynamic Excel schema management
- advanced analytics and reporting
- parallel task execution
- dynamic rule engine
- notification center
- environment-specific configuration override matrix
- advanced audit filtering
- full operational runbook
- final Oracle DDL and DBA optimization decisions

### Design Boundaries
- Frontend ↔ Backend: HTTP/REST with JSON
- Backend ↔ Oracle: transactional persistence and query access
- Backend ↔ Execution Engines: HTTP-based execution initiation and callback
- Backend ↔ Secret Store: abstracted secret access, implementation selected separately

---

## 6. Module Design

### 6.1 Upload & Import Service

**Responsibilities**
- receive uploaded Excel file
- validate file type and size
- parse Excel according to fixed schema
- validate row data and business constraints
- normalize Release ID using grouping and fallback rules
- group rows into Release Flows
- create or update Release Flow / Request / Task hierarchy atomically
- create upload audit entry
- return import summary and validation errors

**Key Interactions**
- called by Upload Controller
- uses Release Flow Service for create/update behavior
- uses Task Management Service to create tasks
- uses Audit Logger to record upload result

**Internal Design Concerns**
- file-level atomic transaction
- deterministic grouping behavior
- actionable validation output
- duplicate import updates existing Release Flow instead of blind duplication

**Import Grouping Algorithm**
For each parsed row:
1. normalize `project`
2. resolve `normalized_release_id`
   - use Excel `Release ID` if present
   - otherwise apply fallback rule
3. group by `(project, normalized_release_id)`
4. under each group, create or update:
   - one Release Flow
   - one Request per stage
   - one or more Tasks per Request

---

### 6.2 Release Flow Service

**Responsibilities**
- create and retrieve Release Flows
- update Release Flow context and state
- maintain `current_stage`, `flow_status`, `review_status`, `review_owner`
- aggregate child status for display and progression
- provide summary and detail projections for UI

**Key Interactions**
- called by Import Service
- called by Task Management Service and Decision Engine
- queried by controllers for summary/detail views

**Internal Design Concerns**
- efficient hierarchy traversal
- clear stage advancement logic
- separation between internal status and summary display status

**Stage Advancement Rule**
A Release Flow advances from one stage to the next when:
- all tasks in the current stage's active Request(s) are in terminal-success-like states:
  - `Approved`
  - `Skipped`

Stage progression rule:
- SIT complete → activate UAT
- UAT complete → activate PROD
- PROD complete → mark Release Flow `Completed`

If any task is `Rejected`, Release Flow becomes `Rejected` and does not advance.

---

### 6.3 Task Management Service

**Responsibilities**
- create and query task records
- validate task editability
- persist edited input parameters
- maintain task status
- manage latest execution reference
- coordinate task execution history creation and updates

**Key Interactions**
- called by Import Service
- called by Decision Engine
- called by Execution Callback Handler
- queried by Task Controller

**Internal Design Concerns**
- task edit allowed only in:
  - `Pending`
  - `Ready_For_Execution`
- task input schema validation
- latest summary vs per-attempt history separation
- optimistic locking / versioning recommended for concurrent safety

**Task State Model**
Supported states:
- `Pending`
- `Ready_For_Execution`
- `Executing`
- `Awaiting_Review`
- `Approved`
- `Rejected`
- `Skipped`
- `Failed`
- `Rerun_Queued`

Valid transitions:
- `Pending` → `Ready_For_Execution`
- `Ready_For_Execution` → `Executing`
- `Executing` → `Awaiting_Review`
- `Awaiting_Review` → `Approved`
- `Awaiting_Review` → `Rejected`
- `Awaiting_Review` → `Skipped`
- `Awaiting_Review` → `Rerun_Queued`
- `Rerun_Queued` → `Executing`

---

### 6.4 Decision Engine

**Responsibilities**
- process task-level decisions:
  - Approve
  - Reject
  - Rerun
  - Skip
- validate decision legality against task state and role
- update task state
- update parent Request / Release Flow state
- trigger rerun execution when needed
- create audit log entries for decisions

**Key Interactions**
- called by Decision Controller
- uses Task Management Service
- uses Release Flow Service
- uses Execution Service for rerun
- uses Audit Logger

**Decision Rules**
- **Approve**
  - task → `Approved`
  - if next task exists in active Request, move it to `Ready_For_Execution`
  - if no next task exists, mark Request `Completed`
  - then evaluate stage advancement
- **Reject**
  - current task → `Rejected`
  - current Request → `Rejected`
  - Release Flow → `Rejected`
  - no further tasks are executable in this Release Flow
- **Rerun**
  - create new TaskExecutionHistory attempt
  - task → `Rerun_Queued`
  - trigger Execution Service
- **Skip**
  - task → `Skipped`
  - determine next available task or stage progression

**Decision Idempotency**
Decision idempotency is enforced by:
- task current state check
- optimistic locking / version validation

Duplicate decision requests should return `409 Conflict` if the task is no longer in `Awaiting_Review`.

---

### 6.5 Execution Service

**Responsibilities**
- submit tasks to external execution engines
- resolve which adapter to use (Jenkins / Ansible)
- create execution attempt records
- prepare execution payload
- resolve configuration and credentials
- send execution request
- correlate `execution_id` with `task_id`
- support rerun execution

**Key Interactions**
- called by Task Management orchestration or Decision Engine
- uses Configuration Service for runtime configuration
- uses secret provider abstraction for credentials
- uses Jenkins / Ansible adapters
- updates TaskExecutionHistory

**Internal Design Concerns**
- adapter selection by task type
- request correlation
- retry/error semantics for submission
- no direct flow progression after execution submission
- submission success should not imply execution success

**Execution Trigger Rule**
For MVP:
- when a task enters `Ready_For_Execution`, backend orchestration invokes Execution Service automatically
- no explicit manual execute API is required

---

### 6.6 Execution Callback Handler

**Responsibilities**
- receive webhook callbacks from Jenkins / Ansible
- authenticate callback source
- validate payload structure
- validate `execution_id` correlation
- enforce idempotent callback processing
- persist execution result
- update task and execution history status
- move task to `Awaiting_Review`

**Key Interactions**
- called externally by execution engines
- uses Task Management Service
- uses Result Storage
- uses Audit Logger for callback-related trace events if needed

**Internal Design Concerns**
- idempotency by `execution_id`
- authenticated callback processing
- replay protection
- explicit invalid correlation handling
- callback should update state, not decide progression

**Callback Processing Rule**
On valid callback:
1. find execution history by `execution_id`
2. verify the execution belongs to an active logical task
3. persist full result payload
4. update execution history status
5. update task:
   - `current_result_summary`
   - `latest_execution_id`
   - `task_status = Awaiting_Review`

---

### 6.7 Configuration Service

**Responsibilities**
- read and write configuration items
- validate format by config key
- restrict updates to DevOps Admin
- provide current configuration for future executions

**Key Interactions**
- queried by Execution Service
- managed via Configuration Controller
- logged through Audit Logger

**MVP Configuration Keys**
- `jenkins_url`
- `ansible_url`
- `execution_callback_endpoint`

**Update Semantics**
Configuration changes apply to:
- **future executions only**

They do **not** affect tasks already in `Executing` state.

**Persistence Rule**
For MVP:
- configuration values may be updated in place
- each change must generate an audit log entry
- full config versioning/history table is future scope

---

### 6.8 Audit Logger

**Responsibilities**
- persist immutable audit records
- standardize audit event structure
- record all key operator actions
- support audit log querying for UI

**Key Interactions**
- called by all major domain services
- queried by Audit Log Controller

**Supported Action Types**
- `upload`
- `edit`
- `view_result`
- `approve`
- `reject`
- `rerun`
- `skip`
- `config_update`

**Audit Logging Rule**
Audit entries are append-only.  
No update or delete operation is supported from business logic or UI.

---

### 6.9 Result Storage

**Responsibilities**
- persist full execution result payload
- persist raw logs
- retrieve result by execution attempt
- support latest-attempt and explicit-attempt lookup

**Key Interactions**
- written by Execution Callback Handler
- queried by Result Controller / Result Viewer
- linked to TaskExecutionHistory

**Ownership Model**
- `Task` stores latest summary metadata
- `TaskExecutionHistory` stores attempt-level metadata
- Result Storage stores full result content

**Oracle Strategy**
For MVP, full raw logs may be stored:
- in a dedicated Oracle result table with `CLOB`, or
- in `TaskExecutionHistory` via `CLOB`

Final DDL choice is implementation-specific, but the ownership model must stay unchanged.

---

### 6.10 Vue 3 UI Modules

#### Workspace Layout
- WWA-embedded workspace shell
- left navigation
- main summary/detail/task area

#### Release Flow Summary View
- Release Flow list
- filter controls
- upload action
- row selection behavior

#### Selected Release Flow Details View
- Project
- Release ID
- Current Stage
- Current Request ID
- Review Status
- Review Owner

#### Task Details View
- task table
- action dropdown
- result access
- edit access
- decision access

#### Upload Dialog
- file select
- template download
- sample view
- validation feedback
- import result summary

#### Task Edit Dialog
- schema-driven form
- validation feedback
- save / cancel

#### Decision Dialog
- explicit decision confirmation
- optional future comment support

#### Configuration Management View
- config list
- edit interaction
- validation feedback

#### Audit Log View
- read-only table
- pagination or lazy loading

**Vue State Management**
Recommended Pinia store domains:
- `releaseFlowStore`
- `requestTaskStore`
- `configStore`
- `auditStore`
- `userContextStore`

**Polling Rule**
For MVP:
- Release Flow Summary view polls every **10 seconds** while active
- Selected Task / Result view refreshes on demand or after user action
- Callback-driven push is future scope

---

### 6.11 Spring Boot Controllers

Controllers should be resource-oriented and DTO-based.

Recommended controller groups:
- `UploadController`
- `ReleaseFlowController`
- `TaskController`
- `DecisionController`
- `ResultController`
- `ConfigurationController`
- `AuditLogController`
- `ExecutionCallbackController`

Server-side authorization must be enforced regardless of frontend visibility rules.

---

## 7. API / Interface Design

### General API Rules
- JSON request/response unless file upload
- server-side validation required
- centralized exception handling required
- role-based access control enforced in backend
- optimistic locking recommended for mutation endpoints

### 7.1 Upload Endpoint
- **Path**: `POST /api/deployment-agent/upload`
- **Purpose**: upload Excel for import
- **Input**: multipart file
- **Authorization**: Developer, TL, DevOps Admin
- **Response 200**:
```json
{
  "success": true,
  "message": "Import completed",
  "releaseFlowIds": ["RF-001", "RF-002"],
  "importSummary": {
    "createdCount": 2,
    "updatedCount": 1,
    "taskCount": 8
  }
}