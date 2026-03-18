# Deployment Agent MVP — Implementation Plan

**Last Updated**: 2026-03-18 | **Status**: Phase 2 Complete (T6.1–T6.3, T8.1b done)
**Stack**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / H2 (test) / Oracle (prod)
**Build**: `mvn test` (requires internet on first run to pull Spring Boot + POI artifacts)
**Primary source of truth for task scope**: `docs/06-tasks/tasks.md`

---

## 1. Finalized Schema (Current Repo State)

### Task Entity (`DA_TASK`)
| Column | Type | Source |
|--------|------|--------|
| `id` | VARCHAR(36) PK | `@PrePersist` UUID |
| `request_id` | VARCHAR(36) FK | Parent Request |
| `task_group_id` | VARCHAR(255) | Excel `Task ID` |
| `task_group_name` | VARCHAR(255) | Excel `Task Name` |
| `step_seq` | INTEGER | Excel `Step seq#` |
| `task_name` | VARCHAR(255) | Excel `Step` |
| `execution_type` | VARCHAR(10) | `MANUAL` \| `AUTO` |
| `task_status` | VARCHAR(30) | State machine |
| `input_parameters` | CLOB (JSON) | `{script, parameters}` |
| `expected_output` | CLOB | Excel `Parameter (Expected Output)` |
| `owner` | VARCHAR(255) | Excel `Owner` |
| `planned_start_time` | TIMESTAMP | Excel `Planned Start date/time` |
| `planned_end_time` | TIMESTAMP | Excel `Planned End date/time` |
| `import_metadata` | CLOB (JSON) | Raw blob: `activity_category`, `common`, `dependencies`, `validation` |
| `current_result_summary` | CLOB (JSON) | Latest execution result |
| `latest_execution_id` | VARCHAR(36) | FK → TaskExecutionHistory |
| `start_time` | TIMESTAMP | Actual execution start |
| `end_time` | TIMESTAMP | Actual execution end |
| `last_updated_at` | TIMESTAMP | `@UpdateTimestamp` |
| `version` | BIGINT | `@Version` optimistic lock |

**NOT in schema** (finalized design decision):
- `template_status` — not stored
- `start_time_from_template` / `end_time_from_template` — ignored

### ReleaseFlow Entity (`DA_RELEASE_FLOW`)
| Column | Type | Notes |
|--------|------|-------|
| `id` | VARCHAR(36) PK | `@PrePersist` UUID |
| `project_id` | VARCHAR(255) | Excel `Project ID`; grouping key |
| `project_name` | VARCHAR(255) | Excel `Project Name` |
| `release_id` | VARCHAR(255) | System-generated: `{stage}-{normalized_project_id}-{seq}` |
| `normalized_release_id` | VARCHAR(255) | Part of unique index `(project_id, normalized_release_id)` |
| `current_stage` | VARCHAR(10) | `SIT` \| `UAT` \| `PROD` |
| `flow_status` | VARCHAR(30) | `Pending \| Running \| Completed \| Failed \| Rejected` |
| `review_status` | VARCHAR(30) | `Pending_Review \| Approved \| Rejected` |
| `review_owner` | VARCHAR(255) | Nullable |
| `created_at` | TIMESTAMP | `@CreationTimestamp` |
| `updated_at` | TIMESTAMP | `@UpdateTimestamp` |
| `version` | BIGINT | `@Version` optimistic lock |

### Other Entities
- **Request** (`DA_REQUEST`): `id`, `release_flow_id`, `stage`, `request_status`, `created_at`, `updated_at`, `version`; index on `(release_flow_id, stage)`
- **TaskExecutionHistory** (`DA_TASK_EXECUTION_HISTORY`): `id`, `task_id`, `attempt_number`, `execution_status`, `input_snapshot` (CLOB/JSON), `result_summary` (CLOB/JSON), `result_logs` (CLOB), `start_time`, `end_time`; unique index on `(task_id, attempt_number)`
- **AuditLogEntry** (`DA_AUDIT_LOG_ENTRY`): append-only; `id`, `operator_id`, `operator_role`, `action_type`, `timestamp`, `release_flow_id`, `request_id`, `task_id`, `context_payload` (CLOB/JSON)
- **ConfigurationItem** (`DA_CONFIGURATION_ITEM`): `config_key` (PK), `config_value`, `description`, `updated_by`, `updated_at`

---

## 2. Locked Design Rules (Must Not Change)

1. **Stage source**: User selects SIT / UAT / PROD at upload time — not from Excel
2. **Release ID generation**: `{stage}-{normalized_project_id}-{seq}` (e.g., `sit-paymenthub-0001`); system-generated; seq = count of existing flows for projectId + 1
3. **Release Flow grouping**: by `project_id` from Excel; same project re-uploads attach new stage to same flow
4. **Execution Type**: `MANUAL` executes externally (TL records result); `AUTO` submits to pipeline (receives callback)
5. **Rerun model**: same `task_id`, new `TaskExecutionHistory` row with incremented `attempt_number`
6. **Summary display**: only `Done` / `Running` / `Pending` (no raw enum exposure)
7. **RBAC**: Developer = upload + view; TL = view + edit input + decide + record result; DevOps Admin = config + operational view; Audit/Management = audit log view
8. **Config update scope**: applies to future executions only; does not affect in-flight tasks

---

## 3. Phase 0 — Design Resolution Status

| Blocker | Status | Notes |
|---------|--------|-------|
| RESOLVE-Q1 (Excel schema) | ✅ Resolved | All field mappings finalized and implemented in T6.1 |
| RESOLVE-Q6 (Stage/Release ID source) | ✅ Resolved | Stage from upload param; Release ID system-generated |
| RESOLVE-Q2 (Callback auth) | ❓ Pending | Blocks T9.1, T9.2, T9.4 |
| RESOLVE-Q3 (Secret store) | ❓ Pending | Blocks T8.1 (AUTO execution), T8.2 |
| RESOLVE-Q4 (Oracle result storage) | ❓ Pending | Blocks T9.3; `result_logs` CLOB already in TaskExecutionHistory schema |
| RESOLVE-Q5 (WWA auth context) | ❓ Pending | Blocks full T10.4; header-based placeholder exists |

---

## 4. What Is Already Implemented

### Phase 1 (Foundation) — ✅ Complete

| Task | Java class / file |
|------|-------------------|
| **T1.1** Entities | `Task`, `ReleaseFlow`, `Request`, `TaskExecutionHistory`, `AuditLogEntry`, `ConfigurationItem` — all with `@PrePersist` UUID, `@Version`, `@UpdateTimestamp` |
| **T1.2** Repositories | Spring Data JPA interfaces: `TaskRepository`, `TaskExecutionHistoryRepository`, `ReleaseFlowRepository`, `RequestRepository`, `AuditLogRepository`, `ConfigurationRepository` |
| **T1.3** Transactions & Locking | `@Version Long version` on Task/ReleaseFlow/Request; `@Transactional` on all service methods; `ObjectOptimisticLockingFailureException` → `OptimisticLockConflictException` → 409 |
| **T1.5** Test DB Setup | `TestDataHelper` Spring `@Component` with `seedReleaseFlow()`, `seedRequest()`, `seedTask()`; H2 in-memory (`application-test.properties`); `@SpringBootTest @ActiveProfiles("test") @Transactional` pattern |
| **T2.1** Configuration Service | `ConfigurationService`: get/list/upsert, key validation (URLs, HTTPS), audit on update |
| **T2.2** Configuration Controller | `ConfigurationController`: `GET/POST /api/deployment-agent/config`; DEVOPS_ADMIN auth on write |
| **T3.1** Audit Log Entity | `AuditLogEntry`; append-only `AuditLogRepository` |
| **T3.2** Audit Logger Service | `AuditLoggerService.log()`: `Propagation.REQUIRES_NEW`; failures swallowed; never aborts caller |
| **T3.3** Audit Log Endpoint | `AuditLogController`: `GET /api/deployment-agent/audit-logs`; AUDIT/MANAGEMENT/DEVOPS_ADMIN auth; paginated + filtered |
| **T4.1** ReleaseFlow Service | `ReleaseFlowService`: `create`, `getById`, `list` (paginated+filtered), `findByGroupKey`, `recomputeAndPersistStatus` (bottom-up), `advanceStage` |
| **T4.2** Request handling | `RequestRepository` with `findByReleaseFlowIdWithTasks`, `findByReleaseFlowId`, `findByReleaseFlowIdAndStage` |
| **T5.1** Task Service CRUD | `TaskService`: `create(CreateTaskInput)`, `getById`, `listByRequestId`, `updateStatus` (state machine + audit), `updateResultMetadata` |
| **T5.2** Execution History Service | `TaskExecutionHistoryService`: `createExecution` (auto-attempt + input snapshot), `findByTaskId`, `findLatest`, `completeExecution` |
| **T5.3** Task Input Editing | `TaskService.editInput()`: state guard (Pending/Ready only), null check, audit |
| **T7.1** Decision Engine | `DecisionEngine.applyDecision()`: approve/reject/rerun/skip; TL-only; `@Transactional`; audit |
| **T7.2** Progression Service | `ReleaseFlowProgressionService.progressAfterDecision()`: request completion, SIT→UAT→PROD advancement, flow completion, auto-ready next Pending task, bottom-up recompute |
| **T7.3** Decision Controller | `DecisionController`: `POST /api/deployment-agent/tasks/{id}/decision`; TL auth |
| **T10.1** ReleaseFlow Controllers | `ReleaseFlowController`: `GET /release-flows` (paginated, filterable); `GET /release-flows/{id}` (full hierarchy) |
| **T10.2** Task Controllers | `TaskController`: `GET /tasks?requestId=X`; `GET /tasks/{id}`; `PUT /tasks/{id}/input` (TL); `GET /tasks/{id}/executions` |
| **T10.3** Error Handling | `GlobalExceptionHandler` (`@RestControllerAdvice`): `AppException` → HTTP; `ObjectOptimisticLockingFailureException` → 409; `ImportValidationException` → 422; no stack leak |
| **T10.5** DTOs | Java records: `TaskDto`, `ReleaseFlowListItemDto`, `ReleaseFlowDetailDto`, `RequestDto`, `TaskExecutionHistoryDto`, `AuditLogEntryDto`, `ConfigurationItemDto`, `PaginatedResponseDto<T>`, `DecisionRequestDto`, `ErrorResponseDto` |
| **Security** | `HeaderAuthFilter` reads `X-User-Id`/`X-User-Role`; `UserContextAuthentication`; `SecurityConfig` permits all (role checked per endpoint) |

### Phase 2 (Upload & MANUAL Result) — ✅ Complete

| Task | Java class / file |
|------|-------------------|
| **T6.1** Excel Parsing | `ExcelParserService`: parses `AMH_HCC_task` sheet from XLSX bytes; validates all required fields with row+column errors; enforces `Step seq#` uniqueness per `Task ID`; ignores `Status`/`Start date/time`/`End date/time` |
| **T6.2** Import Service | `ImportService`: groups rows by `project_id`; finds or creates `ReleaseFlow` (with formatted `release_id`); finds or creates `Request` per stage; upserts tasks by `(requestId, taskGroupId, stepSeq)`; preserves execution state on re-upload; single `@Transactional` rollback on any error |
| **T6.3** Upload Controller | `UploadController`: `POST /api/deployment-agent/upload` (multipart: `file` + `stage`); validates stage before reading file (400 on bad stage); DEVELOPER/TL only (403 otherwise); 422 with structured errors on parse failures; returns `UploadResponseDto { releaseFlowId, releaseId, stage, taskCount }` |
| **T8.1b** Record Result | `RecordResultService`: MANUAL + `Ready_For_Execution` guard → creates `TaskExecutionHistory` (Completed, auto-incremented attempt) → transitions task to `Awaiting_Review` → audits → calls `progressAfterDecision`; endpoint: `POST /tasks/{id}/record-result` (TL only) |

### Repository additions (Phase 2)
| Method | Where |
|--------|-------|
| `findFirstByProjectId(projectId)` | `ReleaseFlowRepository` — import lookup |
| `findByReleaseFlowIdAndStage(rfId, stage)` | `RequestRepository` — re-upload upsert |
| `findByRequestIdAndTaskGroupIdAndStepSeq(...)` | `TaskRepository` — task upsert |

---

## 5. What Remains — By Priority

### 5A. Unblocked, Medium Priority

#### T4.3 — Hierarchical Query Optimization
**File**: modify `ReleaseFlowRepository.java`

Add `findByIdWithFullHierarchy(id)` using JPQL/`@Query` with `LEFT JOIN FETCH` across
`ReleaseFlow → requests → tasks`. Replaces the current N+1 pattern in `ReleaseFlowController`'s
detail endpoint.

**Tests**: add a hierarchy-load test to `ReleaseFlowServiceTest` or a new
`src/test/java/.../domain/releaseflow/ReleaseFlowServiceTest.java` (currently missing from test suite).

---

### 5B. Blocked — Awaiting Phase 0 Resolution

| Task | Blocker | What's needed |
|------|---------|---------------|
| T8.1 (AUTO execution orchestration) | RESOLVE-Q3 | Secret store for Jenkins/Ansible credentials |
| T8.2 (Execution adapter — AUTO) | RESOLVE-Q3 | Same as above |
| T8.3 (Execution error handling) | RESOLVE-Q3 | Depends on T8.1 |
| T9.1 (Callback handler service) | RESOLVE-Q2 | Callback auth mechanism (signed token / shared secret / mTLS) |
| T9.2 (Callback controller) | RESOLVE-Q2 | Depends on T9.1 |
| T9.3 (Result retrieval) | RESOLVE-Q4 | Oracle CLOB result storage strategy; `result_logs` already in `TaskExecutionHistory` schema |
| T9.4 (Callback retry strategy) | RESOLVE-Q2 | Depends on T9.1 |
| T10.4 (Full authorization framework) | RESOLVE-Q5 | WWA auth context contract (exact header names, role claim values) |

---

### 5C. Frontend Phase — Awaits API Completeness

> Read-only views and workspace shell can begin now. Write dialogs unblock as backends complete.

| Task | Depends on | Notes |
|------|------------|-------|
| T11.1 Workspace shell | — | Can start now |
| T11.2 Release Flow summary view | T10.1 ✅ | Can start now |
| T11.3 Release Flow detail view | T10.1 ✅ | Can start now |
| T11.4 Task detail view | T10.2 ✅, T8.1b ✅ | Can start now; Record Result button unblocked |
| T11.5 Upload dialog | T6.3 ✅ | Can start now |
| T11.5b Record Result dialog | T8.1b ✅ | Can start now |
| T11.6 Task edit dialog | T10.2 ✅ | Can start now |
| T11.7 Decision dialog | T7.3 ✅ | Can start now |
| T11.8 Audit log view | T3.3 ✅ | Can start now |
| T12.1 State management | T10.x ✅ | Can start now |
| T12.2 REST client | T10.x ✅ | Can start now |

---

## 6. Test Status

```
Build: mvn test  (requires internet on first run for Maven artifact download)
Test runtime DB: H2 in-memory (application-test.properties, MODE=Oracle)

src/test/java/.../domain/task/
  TaskStateMachineTest.java                 ~18 tests  (all transitions, valid + invalid)
  TaskServiceTest.java                      ~23 tests  (CRUD, state machine, input editing, audit)
  TaskExecutionHistoryServiceTest.java      ~14 tests  (creation, attempt numbering, completion)
  RecordResultServiceTest.java               6 tests  (success path, AUTO guard, state guard, attempt#) ← NEW

src/test/java/.../domain/decision/
  DecisionEngineTest.java                   ~12 tests  (approve/reject/rerun/skip, role/state guard, audit)
  ReleaseFlowProgressionServiceTest.java     ~7 tests  (request completion, SIT→UAT→PROD, auto-ready)

src/test/java/.../domain/releaseflow/
  ReleaseFlowAggregationTest.java           ~29 tests  (bottom-up aggregation, summary status, edge cases)
  ReleaseFlowServiceTest.java               MISSING — to be added with T4.3

src/test/java/.../domain/audit/
  AuditLoggerServiceTest.java                ~5 tests  (append, swallow failure)

src/test/java/.../domain/configuration/
  ConfigurationServiceTest.java             ~10 tests  (get/upsert, validation, audit)

src/test/java/.../domain/fileimport/
  ExcelParserServiceTest.java                7 tests  (valid parse, required-field errors, invalid exec type,
                                                        AUTO without script, dup seq#, ignored columns,
                                                        wrong sheet name) ← NEW
  ImportServiceTest.java                     4 tests  (new project, existing project new stage,
                                                        re-upload upsert, validation error rollback) ← NEW
```

**Not yet covered** (will be added with respective tasks):
- `ReleaseFlowServiceTest` — missing from Phase 1 coverage (add with T4.3)
- API contract / HTTP integration tests (T13.3)
- Authorization / security tests (T13.5)
- E2E workflow tests (T13.2, T13.7)

---

## 7. Critical Path

```
[✅ Complete] Foundation (T1.x, T2.x, T3.x, T4.x, T5.x, T7.x, T10.1-10.3, T10.5)
    │
    ▼
[✅ Complete] Upload & Import + MANUAL record-result
    T6.1 ExcelParserService
    T6.2 ImportService + Release ID generation
    T6.3 UploadController
    T8.1b RecordResultService + endpoint
    │
    ▼
[NEXT — unblocked] Optimization + Test Coverage
    T4.3 Hierarchy query optimization (ReleaseFlowRepository)
    ReleaseFlowServiceTest (missing test file)
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
[UNBLOCKED — can parallelize now] Frontend
    T11.x, T12.x — all read-only views + write dialogs for upload/result/decision
    │
    ▼
[LAST] Integration & E2E
    T13.2, T13.3, T13.5, T13.7
```

---

## 8. Next Recommended Work

**T4.3 + ReleaseFlowServiceTest** — the two remaining unblocked items.

1. **T4.3** reduces N+1 on `GET /release-flows/{id}`. The current detail endpoint loads
   requests and tasks in separate queries. A single `LEFT JOIN FETCH` query eliminates this.
   Risk: low (read-only, no state changes).

2. **ReleaseFlowServiceTest** — the only domain service without a dedicated test file.
   Covers `create`, `getById`, `list` filters, `advanceStage`, `recomputeAndPersistStatus`.

Once RESOLVE blockers are cleared, the priority order is:
`RESOLVE-Q3 → T8.1/8.2/8.3 → RESOLVE-Q2 → T9.1/9.2/9.4 → RESOLVE-Q4 → T9.3 → RESOLVE-Q5 → T10.4`

Frontend work is fully parallelizable with all the above.

---

## 9. Architecture Summary

### Stack
- **Runtime**: Java 21
- **Framework**: Spring Boot 3.2.4 (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`)
- **ORM**: Spring Data JPA / Hibernate 6; `@SpringDataWebAutoConfiguration`
- **DB**: Oracle (production, `ojdbc11`); H2 in-memory with `MODE=Oracle` (tests)
- **Excel parsing**: Apache POI 5.2.5 (`poi-ooxml`)
- **Auth**: Header-based (`X-User-Id`, `X-User-Role`); `HeaderAuthFilter` → `UserContextAuthentication`; role checked per endpoint
- **Build**: Maven 3 / `spring-boot-maven-plugin`

### Architecture Boundaries (enforced)
- Controllers live in `web/controller/` — no persistence logic, no domain logic
- Domain logic in `domain/` — no HTTP concerns
- Shared types (DTOs, enums, UserContext) in `contracts/` — entities never returned directly

### Key Patterns
| Pattern | Where |
|---------|-------|
| State machine (pure static) | `TaskStateMachine.isValid(from, to)` |
| Bottom-up status aggregation | `ReleaseFlowAggregation` (pure static methods) |
| Optimistic locking | `@Version Long version` on `Task`, `ReleaseFlow`, `Request` |
| Transaction boundary | `@Transactional` on service methods; `Propagation.REQUIRES_NEW` in `AuditLoggerService` |
| Audit-first | `AuditLoggerService.log()` in every state-changing op; failures swallowed (`REQUIRES_NEW`) |
| DTO separation | Entities never returned from controllers; Java records with `static from(Entity)` |
| JSON columns | `@Convert(converter = JsonAttributeConverter.class)` + `columnDefinition = "CLOB"` |
| UUID generation | `@PrePersist` sets `id = UUID.randomUUID().toString()` |

---

## 10. File Map

### Implemented
```
src/main/java/com/wwa/deploymentagent/
├── contracts/
│   ├── UserContext.java
│   ├── enums/
│   │   ├── ExecutionType.java       MANUAL | AUTO
│   │   ├── TaskStatus.java
│   │   ├── FlowStatus.java
│   │   ├── RequestStatus.java
│   │   ├── Stage.java               SIT | UAT | PROD  (Stage.next())
│   │   ├── ExecutionStatus.java
│   │   ├── ReviewStatus.java
│   │   ├── AuditActionType.java     upload | edit | view_result | approve | reject | rerun | skip | config_update
│   │   ├── ConfigKey.java
│   │   ├── Role.java
│   │   └── SummaryStatus.java
│   └── dto/
│       ├── TaskDto.java
│       ├── ReleaseFlowListItemDto.java
│       ├── ReleaseFlowDetailDto.java
│       ├── RequestDto.java
│       ├── TaskExecutionHistoryDto.java
│       ├── AuditLogEntryDto.java
│       ├── ConfigurationItemDto.java
│       ├── DecisionRequestDto.java
│       ├── PaginatedResponseDto.java
│       ├── ErrorResponseDto.java
│       ├── UploadResponseDto.java          ← T6.3
│       └── RecordResultRequestDto.java     ← T8.1b
├── domain/
│   ├── task/
│   │   ├── Task.java
│   │   ├── TaskRepository.java
│   │   ├── TaskService.java                create, getById, listByRequestId, updateStatus, editInput, updateResultMetadata
│   │   ├── TaskStateMachine.java           isValid(from, to) — pure static
│   │   ├── CreateTaskInput.java
│   │   ├── TaskExecutionHistory.java
│   │   ├── TaskExecutionHistoryRepository.java
│   │   ├── TaskExecutionHistoryService.java
│   │   └── RecordResultService.java        ← T8.1b
│   ├── releaseflow/
│   │   ├── ReleaseFlow.java
│   │   ├── ReleaseFlowRepository.java      findFirstByProjectId, countByProjectId, filtered pages
│   │   ├── ReleaseFlowService.java
│   │   ├── ReleaseFlowAggregation.java     aggregateTasksToRequestStatus, aggregateRequestsToStageStatus, aggregateStagesToFlowStatus
│   │   ├── Request.java
│   │   └── RequestRepository.java          findByReleaseFlowIdWithTasks, findByReleaseFlowId, findByReleaseFlowIdAndStage
│   ├── decision/
│   │   ├── DecisionEngine.java
│   │   ├── DecisionType.java
│   │   └── ReleaseFlowProgressionService.java
│   ├── audit/
│   │   ├── AuditLogEntry.java
│   │   ├── AuditLogRepository.java
│   │   └── AuditLoggerService.java
│   ├── configuration/
│   │   ├── ConfigurationItem.java
│   │   ├── ConfigurationRepository.java
│   │   └── ConfigurationService.java
│   └── fileimport/                         ← T6.1 / T6.2
│       ├── ParsedTaskRow.java
│       ├── ImportError.java
│       ├── ParseResult.java
│       ├── ImportResult.java
│       ├── ExcelParserService.java
│       └── ImportService.java
├── errors/
│   ├── AppException.java
│   ├── NotFoundAppException.java
│   ├── ConflictAppException.java
│   ├── ValidationAppException.java
│   ├── ForbiddenAppException.java
│   ├── UnauthorizedAppException.java
│   ├── InvalidStateTransitionException.java
│   ├── OptimisticLockConflictException.java
│   └── ImportValidationException.java      ← T6.1
├── web/
│   ├── controller/
│   │   ├── TaskController.java             GET/PUT /tasks, GET /executions, POST /record-result ← T8.1b
│   │   ├── DecisionController.java         POST /tasks/{id}/decision
│   │   ├── ReleaseFlowController.java      GET /release-flows, /release-flows/{id}
│   │   ├── AuditLogController.java         GET /audit-logs
│   │   ├── ConfigurationController.java    GET/POST /config
│   │   └── UploadController.java           POST /upload  ← T6.3
│   ├── exception/
│   │   └── GlobalExceptionHandler.java
│   └── security/
│       ├── HeaderAuthFilter.java
│       └── UserContextAuthentication.java
├── config/
│   └── SecurityConfig.java
├── util/
│   └── JsonAttributeConverter.java
└── DeploymentAgentApplication.java

src/test/java/com/wwa/deploymentagent/
├── helper/
│   └── TestDataHelper.java
├── domain/
│   ├── task/
│   │   ├── TaskStateMachineTest.java
│   │   ├── TaskServiceTest.java
│   │   ├── TaskExecutionHistoryServiceTest.java
│   │   └── RecordResultServiceTest.java    ← T8.1b
│   ├── decision/
│   │   ├── DecisionEngineTest.java
│   │   └── ReleaseFlowProgressionServiceTest.java
│   ├── releaseflow/
│   │   └── ReleaseFlowAggregationTest.java
│   ├── audit/
│   │   └── AuditLoggerServiceTest.java
│   ├── configuration/
│   │   └── ConfigurationServiceTest.java
│   └── fileimport/
│       ├── ExcelParserServiceTest.java     ← T6.1
│       └── ImportServiceTest.java          ← T6.2
```

### To Be Created (Next Work)
```
src/main/java/.../domain/releaseflow/
└── (modify) ReleaseFlowRepository.java     T4.3 — add findByIdWithFullHierarchy

src/test/java/.../domain/releaseflow/
└── ReleaseFlowServiceTest.java             missing test file
```

---

## 11. REST API Reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/deployment-agent/release-flows` | any | List release flows (paginated, filterable) |
| `GET`  | `/api/deployment-agent/release-flows/{id}` | any | Release flow detail with requests + tasks |
| `GET`  | `/api/deployment-agent/tasks?requestId=X` | any | List tasks for a request |
| `GET`  | `/api/deployment-agent/tasks/{id}` | any | Single task detail |
| `PUT`  | `/api/deployment-agent/tasks/{id}/input` | TL | Edit task input parameters |
| `GET`  | `/api/deployment-agent/tasks/{id}/executions` | any | Execution history for a task |
| `POST` | `/api/deployment-agent/tasks/{id}/decision` | TL | Apply decision (approve/reject/rerun/skip) |
| `POST` | `/api/deployment-agent/tasks/{id}/record-result` | TL | Record MANUAL task result |
| `POST` | `/api/deployment-agent/upload` | DEV/TL | Upload XLSX file (multipart: file + stage) |
| `GET`  | `/api/deployment-agent/audit-logs` | AUDIT/MGMT/DEVOPS | Paginated audit log |
| `GET`  | `/api/deployment-agent/config` | any | List configuration items |
| `POST` | `/api/deployment-agent/config` | DEVOPS_ADMIN | Upsert configuration item |

---

## 12. Verification Checklist

| Item | Status |
|------|--------|
| All entities finalized with correct columns | ✅ |
| Optimistic locking on Task, ReleaseFlow, Request | ✅ |
| State machine: frozen and tested | ✅ |
| Decision engine: TL-only, transactional, audited | ✅ |
| Progression: request → stage → flow, auto-ready | ✅ |
| 12 REST endpoints wired | ✅ |
| Error handling: GlobalExceptionHandler, no stack leak | ✅ |
| DTO separation: no entity leaks to controller layer | ✅ |
| Excel import (T6.1–T6.3): parse + validate + upsert | ✅ |
| Record Result MANUAL path (T8.1b) | ✅ |
| `ReleaseFlowServiceTest` present | ❌ Missing — add with T4.3 |
| T4.3 Hierarchy query optimization | ❌ Next item |
| AUTO execution (T8.1–T8.3) | ❌ Blocked (RESOLVE-Q3) |
| Callback endpoint (T9.x) | ❌ Blocked (RESOLVE-Q2) |
| Full WWA auth integration (T10.4) | ❌ Blocked (RESOLVE-Q5) |
| Frontend (T11.x, T12.x) | ❌ Not started (unblocked) |
| Integration & E2E tests (T13.x) | ❌ Not started |
