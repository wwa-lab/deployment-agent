# Implementation Task Breakdown

## 1. Overview

This document breaks the Deployment Agent MVP detailed design into actionable implementation tasks for engineering delivery.  
The system is a human-in-the-loop deployment orchestration platform embedded in the WWA workspace. It processes Excel-based deployment requests, creates and manages Release Flows across SIT / UAT / PROD stages, integrates with external execution systems such as Jenkins and Ansible, and maintains a full audit trail for key user actions.

**Implementation Stack**
- **Frontend**: Vue 3
- **Backend**: Spring Boot 3
- **Database**: Oracle

**MVP Workflow**
- Upload Excel
- Import and validate request data
- Create / update Release Flow hierarchy
- Trigger task execution
- Receive execution callback
- Review result
- Make explicit human decision
- Progress or terminate Release Flow
- Record audit trail

---

## 2. Source Design

**System**: Deployment Agent  
**Source**: Detailed Design (finalized MVP design baseline)  
**Scope**: Complete MVP workflow from upload to decision-driven progression  
**Integration Points**:
- Jenkins / Ansible execution integration
- WWA authentication context
- WWA navigation and embedded workspace model

---

## 3. Locked Design Decisions for Implementation

The following decisions are already frozen in the final design and must **not** be re-opened during task execution unless the design is explicitly revised.

1. **Auto-execution behavior**
   - Tasks auto-trigger from `Ready_For_Execution` to execution orchestration.
   - Human-in-the-loop control applies after execution, before progression.

2. **Release Flow grouping rule**
   - Group by `(project, normalized_release_id)`

3. **Release ID fallback rule**
   - If Release ID is missing, backend generates:
     - `{project}_{yyyyMMdd}_{rowGroupHash}`

4. **Rerun model**
   - Same logical `task_id`
   - New `TaskExecutionHistory` record per rerun
   - `attempt_number` increments

5. **Summary display status**
   - Summary tables use only:
     - `Done`
     - `Running`
     - `Pending`

6. **RBAC contract**
   - **Developer**: upload + view
   - **TL**: view + edit task input + decide
   - **DevOps Admin**: configuration management + operational viewing
   - **Audit / Management**: audit log viewing

7. **Configuration update policy**
   - Configuration changes apply to **future executions only**
   - They do not affect in-flight tasks already in `Executing`

---

## 4. Workstreams

### Recommended Sequencing

### Phase 0 — Design Resolution & Environment Readiness
Resolve remaining implementation blockers before feature development begins.

### Phase 1 — Foundation & Persistence
- Oracle schema definition
- JPA entities and repositories
- transaction and locking model
- test database setup
- configuration and audit foundations

### Phase 2 — Core Domain Services
- Release Flow / Request / Task services
- execution history model
- import and parsing logic
- decision engine
- execution service

### Phase 3 — API & Integration Layer
- controllers and DTOs
- execution callback handling
- result retrieval
- authorization and centralized error handling

### Phase 4 — Frontend
- Vue 3 workspace shell
- summary/detail/task views
- upload/edit/decision dialogs
- configuration and audit views
- state management and API integration

### Phase 5 — Testing & Verification
- unit tests
- integration tests
- contract tests
- security tests
- component tests
- E2E flows

---

## 5. Phase 0 — Design Resolution Tasks

These are pre-implementation tasks.  
They are not optional.

### RESOLVE-Q1: Freeze Excel Template Schema
**Priority**: Must  
**Owner**: Product / Requirements  
**Description**:
- Provide final Excel schema for MVP
- Define:
  - field names
  - required / optional
  - data types
  - validation rules
  - example rows

**Blocks**
- T6.1
- T6.2
- T6.3

**Acceptance Criteria**
- Schema artifact is published and versioned
- Engineering can validate uploads against it
- No core import column remains undefined

---

### RESOLVE-Q2: Confirm Callback Authentication Mechanism
**Priority**: Must  
**Owner**: DevOps / Integration / Security  
**Description**:
- Select callback authentication model:
  - signed token
  - shared secret signature
  - mutual TLS
  - equivalent approved model

**Blocks**
- T9.1
- T9.2
- T9.4

**Acceptance Criteria**
- Callback auth mechanism is chosen
- Validation approach is documented
- Backend can implement trusted callback verification

---

### RESOLVE-Q3: Confirm Secret Store Technology
**Priority**: Must  
**Owner**: DevOps / Infrastructure  
**Description**:
- Select runtime secret provider for Jenkins / Ansible credentials
- Confirm Spring Boot 3 integration approach

**Blocks**
- T8.1
- T8.2
- T9.5

**Acceptance Criteria**
- Secret store approach is chosen
- Runtime access pattern is documented
- No credential handling remains undefined

---

### RESOLVE-Q4: Confirm Oracle Result Storage Strategy
**Priority**: Must  
**Owner**: DBA / Backend / Architecture  
**Description**:
- Decide whether full raw result logs live in:
  - dedicated Oracle result table with `CLOB`, or
  - `TaskExecutionHistory` with `CLOB`

**Blocks**
- T7.3
- T9.3
- T12.4

**Acceptance Criteria**
- Result storage strategy is decided
- Oracle persistence ownership is documented
- DDL direction is unambiguous

---

### RESOLVE-Q5: Confirm WWA Auth Context Contract
**Priority**: Must  
**Owner**: Platform / Backend  
**Description**:
- Confirm how user identity and role are exposed to Spring Boot 3 services
- Confirm role names / claims mapping

**Blocks**
- T10.4
- T10.5
- T12.5

**Acceptance Criteria**
- Authentication context contract is available
- RBAC implementation can be coded against stable claims/roles
- No role-resolution ambiguity remains

---

## 6. Task Breakdown by Domain

### Domain 1: Persistence & Data Layer
Foundation tasks for Oracle schema, JPA entities, repositories, locking, and test infrastructure.

### Domain 2: Configuration Management
Configuration CRUD, validation, and admin endpoints.

### Domain 3: Audit Logging
Audit log persistence, append-only logging service, and retrieval endpoint.

### Domain 4: Release Flow & Request Services
Release Flow / Request lifecycle, aggregation, stage handling, and hierarchical navigation.

### Domain 5: Task Management Service
Task CRUD, task state transitions, input editing, and execution history support.

### Domain 6: Upload & Import Service
Excel parsing, validation, grouping, atomic import, and upload endpoint.

### Domain 7: Decision Engine & Progression
Decision handling, flow progression, and idempotent decision processing.

### Domain 8: Execution Service & Adapters
Execution submission orchestration, adapter routing, credential/config resolution, and correlation handling.

### Domain 9: Execution Callback & Result Handling
Webhook handling, result persistence, callback idempotency, and result retrieval.

### Domain 10: HTTP Controllers & API Layer
DTOs, controller endpoints, validation, authorization, and centralized error handling.

### Domain 11: Frontend UI Components
Vue 3 views, dialogs, and workspace interactions.

### Domain 12: Frontend State Management & API Integration
Pinia stores, API client wrappers, refresh behavior, and user context.

### Domain 13: Testing & Verification
Unit, integration, contract, security, frontend, and E2E tests.

---

## 7. Task Details

---

## Domain 1: Persistence & Data Layer

### T1.1: Define Oracle Schema and JPA Entities
**Priority**: Must  
**Owner**: Backend / Database  
**Description**:
- Create Oracle DDL for:
  - Release Flow
  - Request
  - Task
  - TaskExecutionHistory
  - ConfigurationItem
  - AuditLogEntry
- Define PKs, FKs, indexes
- Create Spring Boot 3 JPA entities
- Ensure parent-child hierarchy is represented correctly
- Include version fields for optimistic locking where needed

**Acceptance Criteria**
- Entities compile and map correctly
- Oracle schema supports all MVP entities
- Foreign key constraints enforce integrity
- Indexing plan documented

---

### T1.2: Implement JPA Repositories
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Create Spring Data JPA repositories for all core entities
- Add key custom query methods:
  - find Release Flows with filters
  - find Requests by stage
  - find Tasks by Request
  - find latest execution by task
  - find audit logs by filters
- Add pagination support where needed

**Acceptance Criteria**
- Repositories cover all core query patterns
- No blocking query gap remains for domain services
- Pagination works for list endpoints

---

### T1.3: Implement Transaction and Locking Strategy
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Configure transaction boundaries for:
  - import
  - decision processing
  - callback handling
  - configuration update
- Implement optimistic locking for concurrent mutation safety
- Define rollback behavior for failed import and failed state transitions

**Acceptance Criteria**
- File import is atomic
- Concurrent decisions on same task are protected
- Stale updates return conflict behavior
- Rollback behavior verified by tests

---

### T1.4: Implement Query Performance and Caching Baseline
**Priority**: Should  
**Owner**: Backend  
**Description**:
- Add cache baseline for:
  - Release Flow summary list
  - configuration items
- Define invalidation points
- Verify no obvious N+1 query problems in detail loading

**Acceptance Criteria**
- Summary list query load reduced
- Config reads avoid unnecessary DB hits
- Cache invalidates correctly after updates

---

### T1.5: Implement Test Database Setup
**Priority**: Must  
**Owner**: Backend / QA  
**Description**:
- Set up test persistence environment
- Prepare reproducible schema bootstrap
- Seed sample Release Flow / Request / Task / Audit data
- Support integration and contract tests without external Oracle dependency when possible

**Acceptance Criteria**
- Automated tests can run with stable DB setup
- Seed data is reusable
- Tests can reset cleanly between runs

---

## Domain 2: Configuration Management

### T2.1: Implement Configuration Service
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement configuration service methods:
  - get by key
  - list all
  - update value
- Validate keys:
  - `jenkins_url`
  - `ansible_url`
  - `execution_callback_endpoint`
- Apply future-execution-only semantics

**Acceptance Criteria**
- Config values can be retrieved and updated
- Validation rejects invalid values
- Changes apply only to future executions

---

### T2.2: Implement Configuration Controller and Endpoints
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement:
  - `GET /api/deployment-agent/config`
  - `POST /api/deployment-agent/config`
- Enforce DevOps Admin access for update
- Create audit log entry on update

**Acceptance Criteria**
- Endpoints work as designed
- Authorization enforced server-side
- Audit log entry created for config update

---

### T2.3: Implement Configuration Admin View
**Priority**: Should  
**Owner**: Frontend  
**Description**:
- Create Vue 3 config management view
- Show config items
- Allow DevOps Admin edit workflow
- Show validation errors and success feedback

**Acceptance Criteria**
- DevOps Admin can view and update config
- Non-admin users cannot use config update UI
- Config changes reflect in backend state

---

## Domain 3: Audit Logging

### T3.1: Implement Audit Log Entity and Repository
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement append-only audit log entity
- Include operator, action, timestamp, related entity references, context payload
- Add query methods and indexes

**Acceptance Criteria**
- Audit records can be written and queried
- No update/delete business methods exposed
- Audit filtering queries are supported

---

### T3.2: Implement Audit Logger Service
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Centralize audit logging for:
  - upload
  - edit
  - view_result
  - approve/reject/rerun/skip
  - config_update
- Standardize payload structure

**Acceptance Criteria**
- All supported actions can produce audit records
- Audit logger is reusable across services
- Audit failures do not silently corrupt business flow

---

### T3.3: Implement Audit Log Retrieval Endpoint
**Priority**: Should  
**Owner**: Backend  
**Description**:
- Implement `GET /api/deployment-agent/audit-logs`
- Enforce access for Audit / Management users
- Support pagination and basic filters

**Acceptance Criteria**
- Audit users can retrieve audit records
- Non-audit users are denied
- Pagination works

---

## Domain 4: Release Flow & Request Services

### T4.1: Implement Release Flow Service and Aggregation Logic
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement Release Flow creation, retrieval, and update
- Implement Request retrieval within Release Flow
- Implement internal aggregation for Request and Flow state
- Implement summary-display mapping to:
  - `Done`
  - `Running`
  - `Pending`

**Acceptance Criteria**
- Release Flows can be created and queried
- Aggregation logic supports detail and summary needs
- Summary mapping does not expose extra summary enums

---

### T4.2: Implement Request Service
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Create Request creation and retrieval logic
- Maintain Request state transitions within Release Flow stage context
- Support querying Requests by stage

**Acceptance Criteria**
- Requests are created and retrieved correctly
- Stage-scoped navigation works
- Request state updates are supported

---

### T4.3: Implement Efficient Hierarchical Query Patterns
**Priority**: Should  
**Owner**: Backend  
**Description**:
- Optimize Release Flow → Request → Task query loading
- Avoid N+1 patterns in detail views
- Support summary query performance for list pages

**Acceptance Criteria**
- Detail views load efficiently
- Summary queries perform acceptably
- Query strategy documented

---

## Domain 5: Task Management Service

### T5.1: Implement Task Service and Core CRUD
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement create/read/update task behavior
- Enforce task state model
- Persist task summary metadata and latest execution reference

**Acceptance Criteria**
- Tasks can be created and queried
- State transitions are validated
- Latest execution reference updates correctly

---

### T5.2: Implement Task Execution History Service
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement creation and retrieval of per-attempt execution history
- Enforce unique `(task_id, attempt_number)`
- Support latest-attempt lookup

**Acceptance Criteria**
- New attempts are created on rerun
- Latest attempt retrieval works
- Attempt history is queryable

---

### T5.3: Implement Task Input Editing and Validation
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Allow task input edit only when task is:
  - `Pending`
  - `Ready_For_Execution`
- Validate task input schema by task type
- Audit successful edits

**Acceptance Criteria**
- Edit allowed only in supported states
- Validation errors are returned clearly
- Audit log created for successful edits

---

### T5.4: Implement Result Metadata Update Handling
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Update task-level summary metadata after callback processing
- Maintain `current_result_summary`
- Maintain `latest_execution_id`

**Acceptance Criteria**
- Task shows latest result metadata
- Latest execution link is always current
- Metadata is consistent with execution history

---

## Domain 6: Upload & Import Service

### T6.1: Implement Excel Parsing and Validation
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Parse XLSX file using fixed schema
- Validate structure, required fields, and field formats
- Accumulate row-level validation errors

**Acceptance Criteria**
- Valid files parse successfully
- Invalid files return structured errors
- Errors identify row and field where possible

---

### T6.2: Implement Release Flow Grouping and Upsert Logic
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement grouping by:
  - `(project, normalized_release_id)`
- Apply deterministic fallback when Release ID is missing
- Create or update Release Flow hierarchy accordingly
- Ensure repeated imports do not create duplicates incorrectly

**Acceptance Criteria**
- Grouping rule matches locked design
- Missing Release ID handled deterministically
- Repeated upload does not create unintended duplicates

---

### T6.3: Implement Upload Controller and Endpoint
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement upload endpoint
- Call Import Service
- Return success summary or structured validation errors
- Audit upload result

**Acceptance Criteria**
- Upload endpoint matches design contract
- Authenticated upload works
- Import results are returned correctly
- Upload action is audited

---

## Domain 7: Decision Engine & Progression

### T7.1: Implement Decision Engine
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement decisions:
  - Approve
  - Reject
  - Rerun
  - Skip
- Validate task state and role
- Update task / request / flow states
- Audit decision actions

**Acceptance Criteria**
- All decision types are supported
- Decision legality is enforced
- Decision actions create audit records

---

### T7.2: Implement Release Flow Progression Logic
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement next-task progression after Approve / Skip
- Complete Request when all active tasks reach terminal-success-like states
- Advance stage:
  - SIT → UAT
  - UAT → PROD
- Complete Release Flow after PROD success
- Reject terminates the Release Flow and prevents further execution

**Acceptance Criteria**
- Progression follows frozen design rules
- Stage advancement works
- Reject terminates flow correctly
- No duplicate progression occurs

---

### T7.3: Implement Decision Controller and Endpoint
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement `POST /api/deployment-agent/tasks/{id}/decision`
- Enforce TL authorization
- Return updated task / flow context
- Return conflict for stale or invalid task state

**Acceptance Criteria**
- Decision endpoint works for all supported decisions
- Authorization is correct
- Duplicate/stale decisions return conflict behavior

---

## Domain 8: Execution Service & Adapters

### T8.1: Implement Execution Service Orchestration
**Priority**: Must  
**Owner**: Backend / Integration  
**Description**:
- Implement execution submission orchestration
- Auto-trigger execution when task enters `Ready_For_Execution`
- Resolve config and secret dependencies
- Prepare execution payload and create execution correlation

**Acceptance Criteria**
- Execution can be submitted from ready state
- Correlation between task and execution attempt is created
- No manual execute endpoint is required for MVP

---

### T8.2: Implement Jenkins / Ansible Adapter Abstraction
**Priority**: Must  
**Owner**: Backend / Integration  
**Description**:
- Create adapter abstraction for execution engines
- Route execution by task type
- Support request submission and error handling

**Acceptance Criteria**
- Adapter routing works
- Execution request abstraction supports both integrations
- Submission failures are handled consistently

---

### T8.3: Implement Execution Submission Error Handling
**Priority**: Should  
**Owner**: Backend / Integration  
**Description**:
- Handle submission failures, retries, and correlation-safe failure states
- Ensure task state does not falsely imply successful execution submission

**Acceptance Criteria**
- Submission failure paths are handled safely
- State remains consistent when external submission fails

---

## Domain 9: Execution Callback & Result Handling

### T9.1: Implement Execution Callback Handler Service
**Priority**: Must  
**Owner**: Backend / Integration  
**Description**:
- Validate callback correlation by `execution_id`
- Enforce callback idempotency
- Update execution history
- Persist result payload
- Move task to `Awaiting_Review`
- Update task summary metadata

**Acceptance Criteria**
- Valid callback updates system state correctly
- Duplicate callback does not duplicate processing
- Invalid correlation returns appropriate error

---

### T9.2: Implement Execution Callback Controller and Endpoint
**Priority**: Must  
**Owner**: Backend / Integration  
**Description**:
- Implement callback endpoint
- Enforce callback authentication
- Return correct response codes for valid / invalid / conflict scenarios
- Log callback attempts for observability

**Acceptance Criteria**
- Callback endpoint matches design contract
- Auth is enforced
- Timeouts and errors are handled properly

---

### T9.3: Implement Result Retrieval Service and Endpoint
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Retrieve latest execution result by default
- Support explicit historical execution lookup
- Efficiently load large result payloads

**Acceptance Criteria**
- Result retrieval works for latest and historical attempts
- Large logs are retrievable without breaking API behavior

---

### T9.4: Implement Callback Retry and Error Strategy
**Priority**: Should  
**Owner**: Backend / Integration  
**Description**:
- Document and implement callback error handling expectations
- Ensure external retry compatibility
- Ensure duplicate delivery is safe

**Acceptance Criteria**
- Callback error semantics are documented
- Idempotency verified through tests

---

## Domain 10: HTTP Controllers & API Layer

### T10.1: Implement Release Flow Controllers
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement Release Flow list and detail endpoints
- Support filtering, paging, and summary projections

**Acceptance Criteria**
- Summary and detail endpoints work
- Filters and paging behave correctly

---

### T10.2: Implement Task Controllers
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Implement task list endpoint
- Implement edit task input endpoint
- Enforce TL-only edit access

**Acceptance Criteria**
- Task list endpoint works
- Edit endpoint validates and persists correctly
- Access control is correct

---

### T10.3: Implement Error Handling Framework
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Create centralized exception handling
- Standardize API error response shape
- Map validation, auth, not found, conflict, server errors

**Acceptance Criteria**
- Errors are consistent across API layer
- HTTP codes match design rules
- Stack traces are not leaked to client

---

### T10.4: Implement Authorization Framework
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Integrate with WWA auth context
- Enforce RBAC on controllers and mutation flows
- Align role mapping with frozen MVP RBAC

**Acceptance Criteria**
- Authorization works across endpoints
- Wrong-role access returns forbidden
- RBAC matches locked design decisions

---

### T10.5: Implement Request / Response DTOs and API Contracts
**Priority**: Must  
**Owner**: Backend  
**Description**:
- Create DTOs for all major API contracts
- Add validation annotations
- Generate API documentation

**Acceptance Criteria**
- Controllers use DTOs consistently
- Contracts match design
- Validation annotations are in place

---

## Domain 11: Frontend UI Components

### T11.1: Implement Workspace Layout and Navigation
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Build Vue 3 workspace shell
- Support summary / details / task area layout
- Embed under WWA navigation model

**Acceptance Criteria**
- Workspace shell renders correctly
- Navigation works
- Main content updates correctly

---

### T11.2: Implement Release Flow Summary View
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Build summary table
- Support filters and pagination
- Trigger Release Flow selection
- Support polling every 10s while active

**Acceptance Criteria**
- Summary list renders correctly
- Filters and paging work
- Polling refresh works

---

### T11.3: Implement Release Flow Details View
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Display selected Release Flow detail context
- Show current stage / request / review owner / review status

**Acceptance Criteria**
- Detail panel updates correctly with selection
- Context fields match backend data

---

### T11.4: Implement Task Details View
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Display task list for selected Request
- Render status and action visibility by task state and role
- Support result viewing

**Acceptance Criteria**
- Task list renders correctly
- Action visibility is correct
- Result access works

---

### T11.5: Implement Upload Dialog
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- File select
- upload action
- validation feedback
- import success/error display
- refresh summary after success

**Acceptance Criteria**
- Upload flow works end-to-end
- Success and error states display correctly

---

### T11.6: Implement Task Edit Dialog
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Schema-driven task input edit UI
- Validation feedback
- save / cancel behavior
- refresh task state after success

**Acceptance Criteria**
- Edit dialog works for eligible tasks
- Validation is shown clearly
- Save updates task successfully

---

### T11.7: Implement Decision Dialog
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Present Approve / Reject / Rerun / Skip
- Require explicit confirmation
- Refresh flow/task context after decision

**Acceptance Criteria**
- Decision dialog works for reviewable tasks
- Confirmation required
- UI refreshes correctly after submit

---

### T11.8: Implement Audit Log View
**Priority**: Should  
**Owner**: Frontend  
**Description**:
- Build read-only audit log page
- Support pagination
- Restrict view to Audit / Management users

**Acceptance Criteria**
- Audit page renders correctly
- Audit data is paginated
- Unauthorized users do not access it

---

## Domain 12: Frontend State Management & API Integration

### T12.1: Implement Vue 3 State Management
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Create Pinia stores for:
  - release flow list/detail
  - selected request/task context
  - config
  - audit
  - user context

**Acceptance Criteria**
- Shared state works without prop-drilling
- Selection state persists across interactions
- UI refreshes correctly after actions

---

### T12.2: Implement REST Client Integration
**Priority**: Must  
**Owner**: Frontend  
**Description**:
- Create API wrappers for major backend resources
- Handle auth headers from WWA context
- Standardize error handling and request behavior

**Acceptance Criteria**
- API client works across views
- Errors are handled consistently
- Auth context is attached correctly

---

## Domain 13: Testing & Verification

### T13.1: Implement Domain Unit Tests
**Priority**: Must  
**Owner**: Backend / QA  
**Description**:
- Unit test core domain services:
  - Release Flow
  - Task
  - Import
  - Decision
  - Config
  - Audit

**Acceptance Criteria**
- Core service behaviors are unit tested
- Edge cases included
- Domain logic coverage is strong

---

### T13.2: Implement Integration Workflow Tests
**Priority**: Must  
**Owner**: Backend / QA  
**Description**:
- Test end-to-end backend workflows:
  - upload → import → task lifecycle
  - callback → review
  - approve / reject / rerun / skip
  - stage progression

**Acceptance Criteria**
- Core workflow integration tests pass
- State remains consistent through workflows

---

### T13.3: Implement API Contract Tests
**Priority**: Should  
**Owner**: Backend / QA  
**Description**:
- Validate controller request/response contracts
- Validate error code behavior

**Acceptance Criteria**
- API formats match design
- Validation and error responses are correct

---

### T13.4: Implement Result Persistence Tests
**Priority**: Should  
**Owner**: Backend / QA  
**Description**:
- Test result payload storage and retrieval
- Test multiple attempts and large logs

**Acceptance Criteria**
- Result payloads persist and retrieve correctly
- Multiple attempts behave correctly

---

### T13.5: Implement Authorization and Security Tests
**Priority**: Should  
**Owner**: Backend / Security  
**Description**:
- Verify RBAC
- Verify callback authentication behavior
- Verify unauthorized access handling

**Acceptance Criteria**
- Sensitive endpoints are protected
- Unauthorized behavior is correct

---

### T13.6: Implement Frontend Component Tests
**Priority**: Should  
**Owner**: Frontend / QA  
**Description**:
- Test major Vue 3 components and dialog flows
- Mock backend calls and error states

**Acceptance Criteria**
- Core UI components render and behave correctly
- Error/loading states are covered

---

### T13.7: Implement E2E Workflow Tests
**Priority**: Could  
**Owner**: QA / DevOps  
**Description**:
- Test full user workflows in integrated environment

**Acceptance Criteria**
- MVP happy path covered end-to-end
- Core decision flows validated

---

## 8. Dependency Plan

### Critical Path
1. RESOLVE-Q1 through RESOLVE-Q5
2. T1.1 → T1.2 → T1.3 → T1.5
3. T2.1 + T3.1 + T3.2
4. T4.1 + T4.2 + T5.1 + T5.2
5. T6.1 + T6.2 + T6.3
6. T7.1 + T7.2
7. T8.1 + T8.2
8. T9.1 + T9.2 + T9.3
9. T10.1 through T10.5
10. T11.x + T12.x
11. T13.x verification

### Recommended Parallel Workstreams

**Backend Foundation**
- T1.x
- T2.x
- T3.x

**Backend Domain**
- T4.x
- T5.x
- T6.x
- T7.x
- T8.x
- T9.x
- T10.x

**Frontend**
- T11.x
- T12.x

**Testing**
- T13.x throughout

---

## 9. Recommended Sequencing by Phase

### Phase 0
- RESOLVE-Q1
- RESOLVE-Q2
- RESOLVE-Q3
- RESOLVE-Q4
- RESOLVE-Q5

### Phase 1
- T1.1
- T1.2
- T1.3
- T1.5
- T2.1
- T2.2
- T3.1
- T3.2

### Phase 2
- T4.1
- T4.2
- T5.1
- T5.2
- T5.3
- T6.1
- T6.2
- T6.3
- T13.1

### Phase 3
- T7.1
- T7.2
- T7.3
- T8.1
- T8.2
- T8.3
- T9.1
- T9.2
- T9.3
- T13.2

### Phase 4
- T10.1
- T10.2
- T10.3
- T10.4
- T10.5
- T13.3
- T13.5

### Phase 5
- T11.1
- T11.2
- T11.3
- T11.4
- T11.5
- T11.6
- T11.7
- T11.8
- T12.1
- T12.2
- T13.6

### Phase 6
- T13.4
- T13.7
- full integration verification
- release readiness review

---

## 10. Risks / Blockers

### Key Risks
1. Excel schema changes after backend import implementation
2. callback contract mismatch with external execution systems
3. Oracle result log growth impacting performance
4. secret store decision delay impacting secure execution submission
5. WWA auth contract mismatch affecting RBAC implementation

### Current Blockers
- final Excel schema artifact
- callback authentication decision
- secret store technology decision
- Oracle result storage decision
- WWA auth role context contract

---

## 11. Implementation Readiness Matrix

| Blocker | Affected Tasks | Owner | Required By |
|---|---|---|---|
| Excel schema | T6.1, T6.2, T6.3 | Product | Phase 2 |
| Callback authentication | T9.2, T13.5 | DevOps / Security | Phase 3 |
| Secret store technology | T8.1, T8.2 | DevOps / Infra | Phase 3 |
| Oracle result storage decision | T9.1, T9.3, T13.4 | DBA / Backend | Phase 3 |
| WWA auth context contract | T10.4, T12.2, T13.5 | Platform | Phase 1 |

---

## 12. Summary

This task breakdown converts the Deployment Agent MVP design into an implementation-ready engineering plan across backend, frontend, persistence, integration, and testing workstreams.

It is based on the finalized design decisions and is intended for:
- sprint planning
- ownership assignment
- dependency tracking
- implementation sequencing

The plan includes:
- **5 Phase 0 resolution tasks**
- **41 implementation tasks**
- **clear phase sequencing**
- **owner guidance**
- **blocker mapping**
- **testing coverage**

This version is aligned with the frozen MVP design and is suitable for sprint planning once Phase 0 blockers are assigned and scheduled.