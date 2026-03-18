# Deployment Agent MVP — Implementation Plan

**Last Updated**: 2026-03-18 | **Status**: Phase 2 In Progress (T6.1–T6.3, T8.1b implemented; tests require internet for first Maven run)
**Tests**: 145 (pre-rewrite passing) + new tests for T6.1, T6.2, T8.1b (pending first compile) | **Stack**: Java/Spring Boot 3.2.4
**Primary source of truth for task scope**: `docs/06-tasks/tasks.md`

---

## 1. Finalized Schema (Current Repo State)

### Task Entity (`DA_TASK`)
| Column | Type | Source |
|--------|------|--------|
| `id` | UUID PK | Generated |
| `request_id` | varchar FK | Parent Request |
| `task_group_id` | varchar | Excel `Task ID` |
| `task_group_name` | varchar | Excel `Task Name` |
| `step_seq` | integer | Excel `Step seq#` |
| `task_name` | varchar | Excel `Step` |
| `execution_type` | varchar | `MANUAL` \| `AUTO` |
| `task_status` | varchar | State machine |
| `input_parameters` | text (JSON) | `{script, parameters}` |
| `expected_output` | text | Excel `Parameter (Expected Output)` |
| `owner` | varchar | Excel `Owner` |
| `planned_start_time` | datetime | Excel `Planned Start date/time` |
| `planned_end_time` | datetime | Excel `Planned End date/time` |
| `import_metadata` | text (JSON) | Raw blob: `activity_category`, `common`, `dependencies`, `validation` |
| `current_result_summary` | text (JSON) | Latest execution result |
| `latest_execution_id` | varchar | FK → TaskExecutionHistory |
| `start_time` | datetime | Actual execution start |
| `end_time` | datetime | Actual execution end |
| `last_updated_at` | datetime | Auto |
| `version` | integer | Optimistic lock |

**NOT in schema** (per finalized design decision):
- `template_status` — not stored
- `start_time_from_template` / `end_time_from_template` — ignored

### ReleaseFlow Entity (`DA_RELEASE_FLOW`)
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | Generated |
| `project_id` | varchar | Excel `Project ID`; grouping key |
| `project_name` | varchar | Excel `Project Name` |
| `release_id` | varchar | System-generated: `{stage}-{normalized_project_name}-{seq}` |
| `normalized_release_id` | varchar | Grouping key with `project_id` (unique index) |
| `current_stage` | varchar | `SIT` \| `UAT` \| `PROD` |
| `flow_status` | varchar | `Pending \| Running \| Completed \| Failed \| Rejected` |
| `review_status` | varchar | `Pending_Review \| Approved \| Rejected` |
| `review_owner` | varchar | Nullable |
| `version` | integer | Optimistic lock |

### Other Entities
- **Request** (`DA_REQUEST`): `id`, `release_flow_id`, `stage`, `request_status`, `version`
- **TaskExecutionHistory** (`DA_TASK_EXECUTION_HISTORY`): `id`, `task_id`, `attempt_number`, `execution_status`, `input_snapshot` (JSON), `result_summary` (JSON), `result_logs` (CLOB/TEXT), `start_time`, `end_time`; unique index on `(task_id, attempt_number)`
- **AuditLogEntry** (`DA_AUDIT_LOG_ENTRY`): append-only; `id`, `operator_id`, `operator_role`, `action_type`, `timestamp`, `release_flow_id`, `request_id`, `task_id`, `context_payload`
- **ConfigurationItem** (`DA_CONFIGURATION_ITEM`): `config_key` (PK), `config_value`, `description`, `updated_by`, `updated_at`

---

## 2. Locked Design Rules (Must Not Change)

1. **Stage source**: User selects SIT / UAT / PROD at upload time — not from Excel
2. **Release ID generation**: `{stage}-{normalized_project_name}-{seq}` (e.g., `sit-paymenthub-0001`); system-generated; not from Excel
3. **Release Flow grouping**: by `project_id` from Excel; same project re-uploads attach new stage to same flow
4. **Execution Type**: `MANUAL` executes externally (TL records result inline); `AUTO` submits to pipeline (receives callback)
5. **Rerun model**: same `task_id`, new `TaskExecutionHistory` row with incremented `attempt_number`
6. **Summary display**: only `Done` / `Running` / `Pending` (no raw enum exposure)
7. **RBAC**: Developer = upload + view; TL = view + edit input + decide; DevOps Admin = config + operational view; Audit/Management = audit log view
8. **Config update scope**: applies to future executions only; does not affect in-flight tasks

---

## 3. Phase 0 — Design Resolution Status

| Blocker | Status | Notes |
|---------|--------|-------|
| RESOLVE-Q1 (Excel schema) | ✅ Resolved | All field mappings finalized; see T6.1 for exact rules |
| RESOLVE-Q6 (Stage/Release ID source) | ✅ Resolved | Stage from upload param; Release ID system-generated |
| RESOLVE-Q2 (Callback auth) | ❓ Pending | Blocks T9.1, T9.2, T9.4 |
| RESOLVE-Q3 (Secret store) | ❓ Pending | Blocks T8.1 (AUTO execution), T8.2 |
| RESOLVE-Q4 (Oracle result storage) | ❓ Pending | Blocks T9.3; note: `result_logs` CLOB already in TaskExecutionHistory |
| RESOLVE-Q5 (WWA auth context) | ❓ Pending | Blocks full T10.4; middleware placeholder exists |

> **Key change from previous plan**: T6.1, T6.2, T6.3, and T8.1b are **not blocked** by any unresolved RESOLVE tasks. They can be implemented now.

---

## 4. What Is Already Implemented

### Phase 1 (Foundation) — ✅ Complete

| Task | What's in the repo |
|------|--------------------|
| **T1.1** Schema & Entities | All TypeORM entities: Task (full extended schema), ReleaseFlow (`projectId`/`projectName`), Request, TaskExecutionHistory, AuditLogEntry, ConfigurationItem |
| **T1.2** Repositories | TaskRepository, TaskExecutionHistoryRepository, ReleaseFlowRepository, RequestRepository, AuditLogRepository, ConfigurationRepository |
| **T1.3** Transactions & Locking | `@VersionColumn` on Task/ReleaseFlow/Request; `DataSource.transaction()` in DecisionEngine; all repos accept optional `EntityManager` |
| **T1.5** Test DB Setup | `tests/helpers/testDataSource.ts` with `seedReleaseFlow()`, `seedRequest()`, `seedTask()` (full field set), `seedTaskExecutionHistory()`; `clearAllTables()`; in-memory sql.js |
| **T2.1** Configuration Service | ConfigurationService with get/list/upsert, validation per key (URLs, HTTPS), audit on update |
| **T2.2** Configuration Controller | `GET/POST /api/deployment-agent/config`; DEVOPS_ADMIN auth on write |
| **T3.1** Audit Log Entity | AuditLogEntryEntity; append-only AuditLogRepository |
| **T3.2** Audit Logger Service | AuditLoggerService.log(); all audit failures swallowed; participates in caller transaction |
| **T3.3** Audit Log Endpoint | `GET /api/deployment-agent/audit-logs`; AUDIT/MANAGEMENT/DEVOPS_ADMIN auth; paginated + filtered |
| **T4.1** ReleaseFlow Service | ReleaseFlowService: create, getById, list, findByGroupKey, recomputeAndPersistStatus (bottom-up), advanceStage |
| **T4.2** Request Service | RequestService: create, getById, listByReleaseFlow, findByStage, updateStatus |
| **T5.1** Task Service CRUD | TaskService: create(CreateTaskInput), getById, listByRequestId, updateStatus (state machine + audit) |
| **T5.2** Execution History Service | TaskExecutionHistoryService: createExecution (auto-attempt, input snapshot), findByTaskId, findLatest, completeExecution |
| **T5.3** Task Input Editing | TaskService.editInput(): state guard (Pending/Ready only), JSON validation, audit |
| **T5.4** Result Metadata Update | TaskService.updateResultMetadata(): sets currentResultSummary + latestExecutionId atomically |
| **T7.1** Decision Engine | DecisionEngine.applyDecision(): approve/reject/rerun/skip; TL-only; full transaction; audit |
| **T7.2** Progression | ReleaseFlowProgressionService: request completion, stage advancement (SIT→UAT→PROD), flow completion, auto-ready next pending task, bottom-up recompute |
| **T7.3** Decision Controller | `POST /api/deployment-agent/tasks/:id/decision`; TL auth; returns updated TaskDto |
| **T10.1** ReleaseFlow Controllers | `GET /api/deployment-agent/release-flows` (paginated, filterable by projectId); `GET /api/deployment-agent/release-flows/:id` (detail with nested requests/tasks) |
| **T10.2** Task Controllers | `GET /tasks?requestId=X`; `GET /tasks/:id`; `PUT /tasks/:id/input` (TL); `GET /tasks/:id/executions` |
| **T10.3** Error Handling | Centralized Fastify error handler: AppError → HTTP; TypeORM OptimisticLock → 409; no stack leak |
| **T10.5** DTOs | TaskDto (all fields), ReleaseFlowListItemDto/DetailDto (projectId/projectName), RequestDto, TaskExecutionHistoryDto, AuditLogEntryDto, PaginatedResponseDto<T>, DecisionRequestDtoSchema (Zod) |
| **T13.1** Unit Tests | 145 tests, 10 test files — see §6 for breakdown |

### Phase 1 Fidelity Fix Pass — ✅ Applied

The following corrections were applied after initial implementation to align the repo with the finalized design documents:

| What changed | Why |
|---|---|
| Task entity — added `taskGroupId`, `taskGroupName`, `stepSeq`, `executionType`, `expectedOutput`, `owner`, `plannedStartTime`, `plannedEndTime`, `importMetadataJson` | Schema finalization aligned with Excel import field list |
| ReleaseFlow entity — split `project` into `projectId` + `projectName` | Matches RESOLVE-Q6: grouping key is `project_id` from Excel |
| `ExecutionType` enum added (`MANUAL` \| `AUTO`) | Required by T6.1/T8.1 field |
| All DTOs updated to include new task and release flow fields | API contract alignment |
| All handler mappers (`mapTaskToDto()`) updated to include new fields with ISO date strings | Handler fidelity |
| `TaskService.create()` refactored to accept `CreateTaskInput` interface | Supports Import Service calling convention |
| `seedTask()` in testDataSource updated with all new field defaults | Test correctness |
| TaskService tests updated — `create()` tests now verify all new fields | Test fidelity |

### Fidelity Fix Pass #2 — ✅ Applied

The following corrections were applied after the consistency review to fix aggregation bugs and API contract gaps:

| What changed | Why |
|---|---|
| `aggregateTasksToRequestStatus()` — added `Awaiting_Review` and `Ready_For_Execution` as "Running" triggers | Tasks in these states were incorrectly aggregating to "Pending"; spec §9.5 defines them as active states |
| `aggregateTasksToRequestStatus()` — Rejected/Failed now take priority over Running | Rejected/Failed are terminal-error states and should not be masked by Running |
| `toSummaryStatus()` — Rejected/Failed now map to "Done" instead of "Pending" | Terminal states should never display as "Pending"; "Done" is the least misleading 3-value option |
| `TaskExecutionHistoryDto` — added `resultLogs: string | null` field | Entity has `resultLogs` but it was not exposed via API, blocking Result Viewer (FR-33) |
| `TaskHandler` execution mapper — added `resultLogs` mapping | DTO field needs corresponding data |
| `DecisionHandler` — throws `ValidationError` instead of generic `Error` on invalid request body | Generic `Error` bypasses centralized error handler and returns 500 instead of 400 |
| Aggregation tests — added 5 new edge-case tests, updated 2 existing | Awaiting_Review/Ready_For_Execution/Rejected/Failed aggregation paths were untested |
| Test counts updated in §6 and verification checklist | Previous count (136) was stale; actual is 145 |

---

## 5. What Remains — By Priority

### 5A. Next Batch (Unblocked, High Priority)

#### T6.1 — Excel Parsing & Validation
**File**: `src/domain/import/ExcelParserService.ts`

Parse the `AMH_HCC_task` sheet. Rules:

| Excel Column | Action | Target Field |
|---|---|---|
| `Project ID` | Required, non-blank | `release_flow.project_id` |
| `Project Name` | Required, non-blank | `release_flow.project_name` |
| `Task ID` | Required, non-blank | `task.task_group_id` |
| `Task Name` | Required, non-blank | `task.task_group_name` |
| `Step seq#` | Required; positive integer; unique within `Task ID` | `task.step_seq` |
| `Step` | Required, non-blank | `task.task_name` |
| `Execution Type` | Required; `MANUAL` or `AUTO` (case-insensitive); reject any other | `task.execution_type` |
| `Script to be executed` | Required when AUTO; optional when MANUAL | `task.input_parameters.script` |
| `Parameter (input)` | Optional | `task.input_parameters.parameters` |
| `Parameter (Expected Output)` | Optional | `task.expected_output` |
| `Owner` | Optional | `task.owner` |
| `Planned Start date/time` | Optional; validate format | `task.planned_start_time` |
| `Planned End date/time` | Optional; validate format | `task.planned_end_time` |
| `Activity category`, `Common`, `Dependencies`, `Validation` | No validation; store as-is | `task.import_metadata` JSON blob |
| `Status`, `Start date/time`, `End date/time` | **Ignore completely** | (not stored) |

**Tests**: `tests/domain/import/ExcelParserService.test.ts`
- Valid file parses correctly
- Required field missing → row-level error with row + column detail
- Invalid Execution Type → row error
- `Step seq#` uniqueness enforced within `Task ID`
- Ignored columns are not stored

#### T6.2 — Release Flow Grouping & Release ID Generation
**File**: `src/domain/import/ImportService.ts`

- Group parsed rows by `project_id`
- Look up active Release Flow by `project_id` (`findByGroupKey`)
  - **None exists**: create new ReleaseFlow; generate `release_id` = `{stage}-{normalized_project_id}-{seq}` (zero-padded 4 digits, e.g., `sit-paymenthub-0001`); `seq` is per-project counter from DB
  - **Exists**: attach new Request to that Release Flow for the selected stage
- Create Request with stage from upload parameter
- Upsert tasks by `(release_flow_id, stage, task_group_id, step_seq)` on re-upload
- Entire import runs in a single transaction; roll back on any error

**Tests**: `tests/domain/import/ImportService.test.ts`
- New project → new Release Flow + ID generated in correct format
- Existing project → new Request attached to same flow
- Re-upload same project + stage → tasks updated, not duplicated
- Transaction rollback on parse error

#### T6.3 — Upload Controller & Endpoint
**File**: `src/http/handlers/UploadHandler.ts`

- `POST /api/deployment-agent/upload`
- Accept multipart: `file` (XLSX) + `stage` (SIT | UAT | PROD)
- Validate `stage` before file processing — return 400 if missing or invalid
- DEVELOPER or TL role required (upload permission)
- Return on success: `{ releaseFlowId, releaseId, stage, taskCount }`
- Return on validation failure: structured error array with `{ row, column, message }` per error
- Audit the upload event regardless of outcome

**Register** in `buildServer()` / `ServerDeps`.

**Tests**: `tests/http/handlers/UploadHandler.test.ts`
- Missing stage → 400 before file processing
- Invalid stage → 400
- Valid file → 200 with release info
- Validation errors → 422 with structured error rows
- DEVELOPER role → allowed; unauthenticated → 401

#### T8.1b — Record Result Endpoint (MANUAL Tasks)
**File**: `src/http/handlers/RecordResultHandler.ts`
**Service method**: add to existing `TaskService` or create `RecordResultService`

- `POST /api/deployment-agent/tasks/:id/record-result`
- Guards: task must be `MANUAL` + in `Ready_For_Execution`; TL role required
- Creates `TaskExecutionHistory` with:
  - `executionStatus = "Completed"`
  - `attemptNumber` = max + 1
  - `resultSummaryJson` = operator-entered value
  - `startTime` = task.lastUpdatedAt (proxy for when task became ready)
  - `endTime` = now
- Transitions task: `Ready_For_Execution` → `Awaiting_Review`
- Updates `task.latestExecutionId`
- Calls `progressAfterDecision` → triggers decision context
- Audit entry: action = `"view_result"` (or introduce `"record_result"` if preferred)

**Tests**: `tests/http/handlers/RecordResultHandler.test.ts`
- MANUAL + Ready_For_Execution → success; task is Awaiting_Review
- AUTO task → 409 (wrong execution type)
- Wrong state → 409 (state guard)
- Non-TL → 403
- History record created with correct attempt number

---

### 5B. Unblocked, Medium Priority

#### T4.3 — Hierarchical Query Optimization
**File**: modify `src/domain/releaseflow/ReleaseFlowRepository.ts`

Add `findByIdWithFullHierarchy(id)` using TypeORM `createQueryBuilder` with left joins across ReleaseFlow → Requests → Tasks. Replaces the current N+1 pattern in `ReleaseFlowHandler`'s `mapFlowToDetailDto()`.

**Tests**: existing ReleaseFlow tests cover correctness; add a hierarchy test to `tests/domain/releaseflow/`.

---

### 5C. Blocked — Awaiting Phase 0 Resolution

| Task | Blocker | What's needed |
|------|---------|---------------|
| T8.1 (AUTO execution orchestration) | RESOLVE-Q3 | Secret store for Jenkins/Ansible credentials |
| T8.2 (Execution adapter — AUTO) | RESOLVE-Q3 | Same as above |
| T8.3 (Execution error handling) | RESOLVE-Q3 | Depends on T8.1 |
| T9.1 (Callback handler service) | RESOLVE-Q2 | Callback auth mechanism (signed token / shared secret / mTLS) |
| T9.2 (Callback controller) | RESOLVE-Q2 | Depends on T9.1 |
| T9.3 (Result retrieval) | RESOLVE-Q4 | Oracle CLOB result storage strategy; `result_logs` already in TaskExecutionHistory schema as candidate location |
| T9.4 (Callback retry strategy) | RESOLVE-Q2 | Depends on T9.1 |
| T10.4 (Full authorization framework) | RESOLVE-Q5 | WWA auth context contract (exact header names, role claim values) |

---

### 5D. Frontend Phase — Awaits API Completeness

> Can begin workspace shell and read-only views in parallel. Write paths (upload dialog, record-result dialog, decision dialog) should wait until relevant backends are stable.

| Task | Depends on | Notes |
|------|------------|-------|
| T11.1 Workspace shell | — | Can start now |
| T11.2 Release Flow summary view | T10.1 ✅ | Can start now |
| T11.3 Release Flow detail view | T10.1 ✅ | Can start now |
| T11.4 Task detail view | T10.2 ✅, T8.1b | Basic view now; Record Result button after T8.1b |
| T11.5 Upload dialog | T6.3 | After upload endpoint |
| T11.5b Record Result dialog | T8.1b | MANUAL path dialog |
| T11.6 Task edit dialog | T10.2 ✅ | Can start now |
| T11.7 Decision dialog | T7.3 ✅ | Can start now |
| T11.8 Audit log view | T3.3 ✅ | Can start now |
| T12.1 Pinia state management | T10.x ✅ | Can start now |
| T12.2 REST client | T10.x ✅ | Can start now |

---

## 6. Test Status

```
Test Files: 10 (all passing)
Total Tests: 145 passing

tests/domain/task/
  taskStateMachine.test.ts              18 tests  (all transitions, valid and invalid)
  TaskService.test.ts                   23 tests  (CRUD, state machine, input editing, audit, optimistic lock)
  TaskExecutionHistoryService.test.ts   14 tests  (creation, attempt numbering, snapshots, completion)
  taskInputValidation.test.ts            7 tests  (accept valid JSON, reject undefined)

tests/domain/decision/
  DecisionEngine.test.ts                12 tests  (approve/reject/rerun/skip, role guard, state guard, audit)
  ReleaseFlowProgressionService.test.ts  7 tests  (request completion, SIT→UAT→PROD, flow completion, auto-ready)

tests/domain/releaseflow/
  ReleaseFlowService.test.ts            20 tests  (create, getById, list, advanceStage, recompute)
  releaseFlowAggregation.test.ts        29 tests  (bottom-up aggregation, summary status, edge cases)

tests/domain/audit/
  AuditLoggerService.test.ts             5 tests  (append, swallow failure)

tests/domain/configuration/
  ConfigurationService.test.ts          10 tests  (get/upsert, validation, audit)
```

**Not yet covered** (will be added with respective next-batch tasks):
- Import / Upload service tests (T6.1, T6.2, T6.3)
- Record Result endpoint tests (T8.1b)
- API contract tests (T13.3)
- Authorization / security tests (T13.5, full coverage)
- E2E workflow tests (T13.2, T13.7)

---

## 7. Critical Path

```
[Complete] Foundation (T1.x, T2.x, T3.x, T4.x, T5.x, T7.x, T10.1-10.3, T10.5)
    │
    ▼
[NEXT BATCH] Upload & Import + MANUAL record-result
    T6.1 Excel Parser
    T6.2 Import Service + Release ID generation
    T6.3 Upload Endpoint
    T8.1b Record Result Endpoint
    ───────────────────────
    T4.3 Hierarchy query optim. (parallel, low risk)
    │
    ▼
[BLOCKED — awaiting RESOLVE-Q3] AUTO Execution
    T8.1 Execution orchestration (AUTO path)
    T8.2 Execution adapter
    T8.3 Error handling
    │
    ▼
[BLOCKED — awaiting RESOLVE-Q2] Callback Handling
    T9.1 Callback handler service
    T9.2 Callback endpoint
    T9.4 Retry strategy
    │
    ▼
[BLOCKED — awaiting RESOLVE-Q4] Result Retrieval
    T9.3 Result retrieval service + endpoint
    │
    ▼
[BLOCKED — awaiting RESOLVE-Q5] Full Auth
    T10.4 WWA auth framework integration
    │
    ▼
[CAN PARALLELIZE NOW] Frontend
    T11.x, T12.x — read views and state management can begin
    Write dialogs unblock as upload (T6.3), record-result (T8.1b), etc. complete
    │
    ▼
[LAST] Integration & E2E
    T13.2, T13.3, T13.5, T13.7
```

---

## 8. Next Recommended Batch — Rationale

**Implement T6.1 → T6.2 → T6.3 → T8.1b in sequence.**

Why this is the safest coherent next slice:

1. **All are unblocked** — RESOLVE-Q1 and RESOLVE-Q6 are marked resolved in tasks.md. No Phase 0 dependencies remain for these tasks.

2. **High system value unlock** — without upload, no real Release Flows can be created in production. This is the primary user entry point.

3. **T6.2 depends on T6.1** (parser output feeds grouping logic); T6.3 depends on T6.1+T6.2 (controller calls import service). Sequential within the batch.

4. **T8.1b (Record Result)** is self-contained — it only touches Task / TaskExecutionHistory / ReleaseFlowProgressionService, all of which are already implemented and tested. It is the MANUAL-path completion for the task execution model.

5. **T4.3 (hierarchy query)** can run in parallel with the above as a low-risk, self-contained optimization against already-stable repositories.

6. **Frontend read-only views** (T11.1–T11.3, parts of T11.4, T11.7, T11.8, T12.1, T12.2) are unblocked and can run in parallel with backend upload work.

---

## 9. Architecture Summary

### Stack
- **Runtime**: Node.js / TypeScript
- **HTTP**: Fastify 4 (not Express; all handlers use Fastify `request`/`reply`)
- **ORM**: TypeORM 0.3; repositories with optional EntityManager for transaction participation
- **Validation**: Zod (DTOs + request body schemas)
- **DB**: Oracle (production); sql.js in-memory SQLite (tests)
- **Auth**: Header-based (`X-User-Id`, `X-User-Role`); `extractUserContext` hook; `requireRole()` per route

### Architecture Boundaries (enforced)
- HTTP handlers live in `src/http/handlers/` — no persistence logic
- Domain logic in `src/domain/` — no HTTP concerns
- Shared types in `src/contracts/` — entities must not leak into handler layer

### Key Patterns
| Pattern | Where |
|---------|-------|
| State machine (pure function) | `taskStateMachine.ts` → `isValidTaskTransition()` |
| Bottom-up status aggregation | `releaseFlowAggregation.ts` (pure functions) |
| Optimistic locking | `@VersionColumn` on Task, ReleaseFlow, Request |
| Transaction wrapping | `DataSource.transaction()` in DecisionEngine; all repos accept `em?` |
| Audit-first | `AuditLoggerService.log()` called in every state-changing operation; failures swallowed |
| DTO separation | Entities never returned directly; mapper functions in each handler |
| ISO date serialization | All Date fields → `.toISOString()` in DTO mappers |

---

## 10. File Map

### Implemented
```
src/
├── contracts/
│   ├── enums.ts           ExecutionType, TaskStatus, FlowStatus, Stage, RBAC roles...
│   ├── dtos.ts            TaskDto, ReleaseFlowDetailDto, DecisionRequestDtoSchema, Paginated...
│   └── UserContext.ts
├── domain/
│   ├── task/
│   │   ├── Task.entity.ts
│   │   ├── TaskRepository.ts
│   │   ├── TaskService.ts              create(CreateTaskInput), updateStatus, editInput, updateResultMetadata
│   │   ├── taskStateMachine.ts         isValidTaskTransition()
│   │   ├── taskInputValidation.ts      validateTaskInput()
│   │   ├── TaskExecutionHistory.entity.ts
│   │   ├── TaskExecutionHistoryRepository.ts
│   │   └── TaskExecutionHistoryService.ts
│   ├── releaseflow/
│   │   ├── ReleaseFlow.entity.ts       projectId, projectName, releaseId...
│   │   ├── ReleaseFlowRepository.ts
│   │   ├── ReleaseFlowService.ts
│   │   ├── Request.entity.ts
│   │   ├── RequestRepository.ts
│   │   ├── RequestService.ts
│   │   └── releaseFlowAggregation.ts
│   ├── decision/
│   │   ├── DecisionEngine.ts
│   │   └── ReleaseFlowProgressionService.ts
│   ├── audit/
│   │   ├── AuditLogEntry.entity.ts
│   │   ├── AuditLogRepository.ts
│   │   └── AuditLoggerService.ts
│   └── configuration/
│       ├── ConfigurationItem.entity.ts
│       ├── ConfigurationRepository.ts
│       └── ConfigurationService.ts
├── http/
│   ├── server.ts                       buildServer(ServerDeps); error handler; all routes registered
│   ├── middleware/auth.ts              extractUserContext hook; requireRole()
│   └── handlers/
│       ├── ConfigurationHandler.ts    GET/POST /api/deployment-agent/config
│       ├── ReleaseFlowHandler.ts      GET /release-flows, /release-flows/:id
│       ├── TaskHandler.ts             GET/PUT /tasks, /tasks/:id/executions
│       ├── DecisionHandler.ts         POST /tasks/:id/decision
│       └── AuditLogHandler.ts         GET /audit-logs
├── db/dataSource.ts
├── errors/AppError.ts
└── main.ts                            Instantiates all repos/services; passes to buildServer()
```

### To Be Created (Next Batch)
```
src/
├── domain/import/
│   ├── ExcelParserService.ts          T6.1
│   └── ImportService.ts               T6.2
└── http/handlers/
    ├── UploadHandler.ts               T6.3
    └── RecordResultHandler.ts         T8.1b

tests/
├── domain/import/
│   ├── ExcelParserService.test.ts
│   └── ImportService.test.ts
└── http/handlers/
    ├── UploadHandler.test.ts
    └── RecordResultHandler.test.ts
```

---

## 11. Verification Checklist

| Item | Status |
|------|--------|
| All entities finalized with correct columns | ✅ |
| Optimistic locking on Task, ReleaseFlow, Request | ✅ |
| State machine: frozen and tested | ✅ |
| Decision engine: TL-only, transactional, audited | ✅ |
| Progression: request → stage → flow, auto-ready | ✅ |
| Backend API: 8 endpoints registered and wired | ✅ |
| Error handling: centralized Fastify handler | ✅ |
| DTO separation: no entity leaks to HTTP layer | ✅ |
| 145 tests passing | ✅ |
| TypeScript clean | ✅ |
| ESLint clean | ✅ |
| Upload/import implemented | ✅ T6.1 ExcelParserService, T6.2 ImportService, T6.3 UploadController |
| Record Result (MANUAL path) implemented | ✅ T8.1b RecordResultService + endpoint |
| AUTO execution (Jenkins/Ansible) | ❌ Blocked (RESOLVE-Q3) |
| Callback endpoint | ❌ Blocked (RESOLVE-Q2) |
| Full WWA auth integration | ❌ Blocked (RESOLVE-Q5) |
| Frontend | ❌ Not started |
