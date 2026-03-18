# Deployment Agent MVP — Implementation Plan

**Last Updated**: 2026-03-18
**Stack**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / H2 (test) / Oracle (prod) + Vue 3 / Vite / Pinia / Vue Router / Axios (frontend)
**Build**: `mvn test` (requires internet on first run) | `cd frontend && npm install && npm run dev` (requires internet on first run)
**Primary source of truth for task scope**: `docs/06-tasks/tasks.md`

---

## Phase Status Summary

| Phase | Name | Status | Blocking reason |
|-------|------|--------|-----------------|
| Phase 0 | Design Resolution & Environment Readiness | ✅ Complete | — |
| Phase 1 | Foundation & Persistence | ✅ Complete | — |
| Phase 2 | Core Domain Services | ⚠️ Partial | T8.1/T8.2/T8.3 blocked on RESOLVE-Q3 (secret store) |
| Phase 3 | API & Integration Layer | ⚠️ Partial | T9.1–T9.4 blocked on RESOLVE-Q2/Q4; T10.4 blocked on RESOLVE-Q5 |
| Phase 4 | Frontend | ✅ Complete | — |
| Phase 5 | Testing & Verification | ✅ Complete | All T13.1–T13.5, T13.7 done; T13.6 (frontend component tests) deferred |

> **Phase 3 is not complete.** The API layer (T10.1–T10.3, T10.5) and the MANUAL execution path (T8.1b) are done, but the callback/result-retrieval integration (T9.1–T9.4) and full WWA auth (T10.4) are all blocked pending RESOLVE tasks.
> **Phase 4 is complete.** Vue 3 frontend in `frontend/` with all T11.x + T12.x + T2.3 views, dialogs, stores, and API client.

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
| RESOLVE-Q4 (Oracle result storage) | ❓ Pending | Blocks T9.3; `result_logs` CLOB already in `TaskExecutionHistory` schema |
| RESOLVE-Q5 (WWA auth context) | ❓ Pending | Blocks full T10.4; header-based placeholder (`X-User-Id`/`X-User-Role`) exists |

---

## 4. Phase 1 — Foundation & Persistence ✅ Complete

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T1.1** Entities | `Task`, `ReleaseFlow`, `Request`, `TaskExecutionHistory`, `AuditLogEntry`, `ConfigurationItem` | ✅ |
| **T1.2** Repositories | Spring Data JPA: `TaskRepository`, `TaskExecutionHistoryRepository`, `ReleaseFlowRepository`, `RequestRepository`, `AuditLogRepository`, `ConfigurationRepository` | ✅ |
| **T1.3** Transactions & Locking | `@Version Long version` on Task/ReleaseFlow/Request; `@Transactional`; `ObjectOptimisticLockingFailureException` → 409 | ✅ |
| **T1.4** Query Performance | N+1 resolved via `LEFT JOIN FETCH` in `findByReleaseFlowIdWithTasks` and `findByIdWithFullHierarchy` (T4.3); caching baseline not yet added (Should priority) | ⚠️ Partial |
| **T1.5** Test DB Setup | `TestDataHelper` `@Component`; H2 in-memory (`application-test.properties`, `MODE=Oracle`); `@SpringBootTest @ActiveProfiles("test") @Transactional` pattern | ✅ |
| **T2.1** Configuration Service | `ConfigurationService`: get/list/upsert, key validation, audit on update | ✅ |
| **T2.2** Configuration Controller | `ConfigurationController`: `GET/POST /api/deployment-agent/config`; DEVOPS_ADMIN auth on write | ✅ |
| **T3.1** Audit Log Entity | `AuditLogEntry`; append-only `AuditLogRepository` | ✅ |
| **T3.2** Audit Logger Service | `AuditLoggerService.log()`: `Propagation.REQUIRES_NEW`; failures swallowed | ✅ |
| **T3.3** Audit Log Endpoint | `AuditLogController`: `GET /api/deployment-agent/audit-logs`; paginated + filtered | ✅ |

> T1.4 caching baseline is a **Should** priority item deferred until post-Phase 3 stabilization.

---

## 5. Phase 2 — Core Domain Services ⚠️ Partial

### Done

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T4.1** ReleaseFlow Service | `ReleaseFlowService`: create, getById, `getByIdWithFullHierarchy` (T4.3), list, recomputeAndPersistStatus, advanceStage | ✅ |
| **T4.2** Request handling | `RequestRepository`: `findByReleaseFlowIdWithTasks`, `findByReleaseFlowId`, `findByReleaseFlowIdAndStage` | ✅ |
| **T4.3** Hierarchical Query | `findByIdWithFullHierarchy` in `ReleaseFlowRepository`; `ReleaseFlowController` uses single-query path | ✅ |
| **T5.1** Task Service CRUD | `TaskService`: `create(CreateTaskInput)`, `getById`, `listByRequestId`, `updateStatus`, `updateResultMetadata` | ✅ |
| **T5.2** Execution History Service | `TaskExecutionHistoryService`: `createExecution`, `findByTaskId`, `findLatest`, `completeExecution` | ✅ |
| **T5.3** Task Input Editing | `TaskService.editInput()`: state guard (Pending/Ready only), null check, audit | ✅ |
| **T5.4** Result Metadata Update | `TaskService.updateResultMetadata()`: sets `currentResultSummary` + `latestExecutionId` | ✅ |
| **T6.1** Excel Parsing | `ExcelParserService`: parses `AMH_HCC_task` sheet; row-level validation; `Step seq#` uniqueness; ignores `Status`/`Start date/time`/`End date/time` | ✅ |
| **T6.2** Import Service | `ImportService`: groups by `project_id`; finds/creates `ReleaseFlow`; finds/creates `Request` per stage; upserts tasks; single `@Transactional` | ✅ |
| **T6.3** Upload Controller | `UploadController`: `POST /upload` (multipart); stage validated before file read; DEVELOPER/TL only; 422 on parse errors | ✅ |
| **T7.1** Decision Engine | `DecisionEngine.applyDecision()`: approve/reject/rerun/skip; TL-only; `@Transactional`; audit | ✅ |
| **T7.2** Progression Service | `ReleaseFlowProgressionService.progressAfterDecision()`: request completion, stage advancement, auto-ready next task, bottom-up recompute | ✅ |
| **T7.3** Decision Controller | `DecisionController`: `POST /tasks/{id}/decision`; TL auth | ✅ |
| **T8.1b** Record Result (MANUAL) | `RecordResultService`: MANUAL + `Ready_For_Execution` guard; creates history; transitions to `Awaiting_Review`; triggers progression | ✅ |

### Blocked

| Task | Blocker | What's needed |
|------|---------|---------------|
| **T8.1** AUTO execution orchestration | RESOLVE-Q3 | Secret store for Jenkins/Ansible credentials at runtime |
| **T8.2** Execution adapter (AUTO) | RESOLVE-Q3 | Adapter reads `jenkins_url`/`ansible_url` from ConfigItems; submits with `execution_id` for callback correlation |
| **T8.3** Execution submission error handling | RESOLVE-Q3 | Depends on T8.1; failed submission → task → `Failed` |

---

## 6. Phase 3 — API & Integration Layer ⚠️ Partial

### Done

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T10.1** Release Flow Controllers | `ReleaseFlowController`: list (paginated + filtered), detail (single-query via T4.3) | ✅ |
| **T10.2** Task Controllers | `TaskController`: list, get, edit input (TL), executions, record-result (TL) | ✅ |
| **T10.3** Error Handling | `GlobalExceptionHandler`: `AppException` → HTTP; optimistic lock → 409; import validation → 422; no stack leak | ✅ |
| **T10.4** Authorization (partial) | Per-endpoint role checks enforced (DEVELOPER/TL/DEVOPS_ADMIN); header-based `X-User-Id`/`X-User-Role` placeholder in place | ⚠️ Partial |
| **T10.5** DTOs | Java records: `TaskDto`, `ReleaseFlowListItemDto/DetailDto`, `RequestDto`, `TaskExecutionHistoryDto`, `AuditLogEntryDto`, `ConfigurationItemDto`, `PaginatedResponseDto<T>`, `DecisionRequestDto`, `UploadResponseDto`, `RecordResultRequestDto`, `ErrorResponseDto` | ✅ |

### Blocked

| Task | Blocker | What's needed |
|------|---------|---------------|
| **T10.4** Full authorization | RESOLVE-Q5 | WWA SSO/JWT integration — exact header names, token format, role claim values |
| **T9.1** Callback handler service | RESOLVE-Q2 | Callback auth mechanism (signed token / shared secret / mTLS) |
| **T9.2** Callback controller | RESOLVE-Q2 | Depends on T9.1 |
| **T9.3** Result retrieval | RESOLVE-Q4 | Oracle CLOB result storage strategy; `result_logs` column already in schema |
| **T9.4** Callback retry strategy | RESOLVE-Q2 | Depends on T9.1; retry/dead-letter design |

---

## 7. Phase 4 — Frontend ✅ Complete

Frontend source at `frontend/`. Vue 3 + Vite + Pinia + Vue Router + Axios. Dev server proxies `/api` → Spring Boot on `:8080`.
**To run**: `cd frontend && npm install && npm run dev` (requires internet on first run).

| Task | Depends on | Status | File(s) |
|------|------------|--------|---------|
| T11.1 Workspace shell | — | ✅ Done | `views/WorkspaceLayout.vue` |
| T11.2 Release Flow summary view | T10.1 ✅ | ✅ Done | `views/ReleaseFlowSummaryView.vue` |
| T11.3 Release Flow detail view | T10.1 ✅ | ✅ Done | `views/ReleaseFlowDetailView.vue` |
| T11.4 Task detail view | T10.2 ✅, T8.1b ✅ | ✅ Done | `views/ReleaseFlowDetailView.vue` (combined) |
| T11.5 Upload dialog | T6.3 ✅ | ✅ Done | `components/UploadDialog.vue` |
| T11.5b Record Result dialog | T8.1b ✅ | ✅ Done | `components/RecordResultDialog.vue` |
| T11.6 Task edit dialog | T10.2 ✅ | ✅ Done | `components/TaskEditDialog.vue` |
| T11.7 Decision dialog | T7.3 ✅ | ✅ Done | `components/DecisionDialog.vue` |
| T11.8 Audit log view | T3.3 ✅ | ✅ Done | `views/AuditLogView.vue` |
| T12.1 Vue 3 state management (Pinia) | T10.x ✅ | ✅ Done | `stores/releaseFlow.ts`, `task.ts`, `config.ts`, `audit.ts`, `user.ts` |
| T12.2 REST client integration | T10.x ✅ | ✅ Done | `api/client.ts`, `releaseFlows.ts`, `tasks.ts`, `upload.ts`, `config.ts`, `audit.ts` |
| T2.3 Configuration admin view | T2.2 ✅ | ✅ Done | `views/ConfigAdminView.vue` |

**Key frontend design decisions**:
- Role switcher in topbar for dev convenience (simulates WWA auth headers X-User-Id/X-User-Role)
- Polling: release flow list refreshes every 10s via `startPolling()` / `stopPolling()`
- Action visibility: Edit (TL, Pending/Ready), Record Result (TL, MANUAL+Ready), Decision (TL, Awaiting_Review)
- Status badges: color-coded globally via `src/assets/main.css`
- MANUAL tasks marked with purple badge; AUTO with teal badge
- Result viewer: side-by-side result summary vs expected output

---

## 8. Phase 5 — Testing & Verification ⚠️ Partial

| Task | Coverage | Status |
|------|----------|--------|
| **T13.1** Domain unit tests | See §9 for full breakdown | ✅ Done |
| **T13.2** Integration workflow tests | End-to-end upload→decision→progression flows | ✅ Done (`workflow/ManualTaskWorkflowTest`, `ExcelImportWorkflowTest`) |
| **T13.3** API contract tests | HTTP-level controller tests with MockMvc | ✅ Done (`web/ReleaseFlowControllerTest`, `TaskControllerTest`, `ConfigurationControllerTest`) |
| **T13.4** Result persistence tests | CLOB storage; pagination correctness | ✅ Done (`web/ResultPersistenceTest`) |
| **T13.5** Authorization/security tests | Wrong-role → 403; missing header → 401 per endpoint | ✅ Done (`web/SecurityTest`) |
| **T13.6** Frontend component tests | Vue 3 component tests | ❌ Deferred (requires Vitest setup) |
| **T13.7** E2E workflow tests | Full upload-to-approval happy path | ✅ Done (`workflow/ManualTaskWorkflowTest`) |

---

## 9. Test Status (T13.1 Detail)

```
Build: mvn test  (requires internet on first run for Maven artifact download)
Test DB: H2 in-memory (application-test.properties, MODE=Oracle)

domain/task/
  TaskStateMachineTest.java                ~18 tests  (all transitions, valid + invalid)
  TaskServiceTest.java                     ~23 tests  (CRUD, state machine, input editing, audit)
  TaskExecutionHistoryServiceTest.java     ~14 tests  (creation, attempt numbering, completion)
  RecordResultServiceTest.java               6 tests  (success, AUTO guard, state guard, attempt#)

domain/decision/
  DecisionEngineTest.java                  ~12 tests  (approve/reject/rerun/skip, role/state guards)
  ReleaseFlowProgressionServiceTest.java    ~7 tests  (request completion, SIT→UAT→PROD, auto-ready)

domain/releaseflow/
  ReleaseFlowAggregationTest.java          ~29 tests  (bottom-up aggregation, summary status, edge cases)
  ReleaseFlowServiceTest.java               14 tests  (create, getById, hierarchy load, list filters,
                                                        advanceStage, recompute)

domain/audit/
  AuditLoggerServiceTest.java               ~5 tests  (append, swallow failure)

domain/configuration/
  ConfigurationServiceTest.java            ~10 tests  (get/upsert, validation, audit)

domain/fileimport/
  ExcelParserServiceTest.java               7 tests  (valid parse, required-field errors, invalid exec type,
                                                       AUTO without script, dup seq#, ignored columns,
                                                       wrong sheet name)
  ImportServiceTest.java                    4 tests  (new project, existing project new stage,
                                                       re-upload upsert, validation error rollback)
```

---

## 10. Critical Path

```
[✅] Phase 0 — Design Resolution
[✅] Phase 1 — Foundation (T1.x, T2.x, T3.x)
[⚠️] Phase 2 — Core Domain (T4.x–T7.x, T8.1b done; T8.1/T8.2/T8.3 BLOCKED)
[⚠️] Phase 3 — API Layer (T10.1–T10.3, T10.5 done; T9.x + T10.4 BLOCKED)
         │
         ├──[BLOCKED on RESOLVE-Q3]──► T8.1 AUTO orchestration → T8.2 adapter → T8.3 error handling
         │
         ├──[BLOCKED on RESOLVE-Q2]──► T9.1 callback service → T9.2 endpoint → T9.4 retry
         │
         ├──[BLOCKED on RESOLVE-Q4]──► T9.3 result retrieval
         │
         └──[BLOCKED on RESOLVE-Q5]──► T10.4 full WWA auth integration
         │
[✅] Phase 4 — Frontend (T11.x, T12.x, T2.3) — COMPLETE (frontend/ directory)
         │
[⚠️] Phase 5 — Testing (T13.1 done; T13.2–T13.7 pending)
```

---

## 11. Architecture Summary

### Stack
- **Runtime**: Java 21
- **Framework**: Spring Boot 3.2.4 (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`)
- **ORM**: Spring Data JPA / Hibernate 6
- **DB**: Oracle (`ojdbc11`, production); H2 in-memory with `MODE=Oracle` (tests)
- **Excel parsing**: Apache POI 5.2.5 (`poi-ooxml`)
- **Auth**: Header-based (`X-User-Id`, `X-User-Role`); `HeaderAuthFilter` → `UserContextAuthentication`; role checked per endpoint; full WWA SSO pending RESOLVE-Q5
- **Build**: Maven 3 / `spring-boot-maven-plugin`

### Architecture Boundaries (enforced)
- Controllers in `web/controller/` — no persistence logic, no domain logic
- Domain logic in `domain/` — no HTTP concerns
- Shared types in `contracts/` — entities never returned from controllers

### Key Patterns
| Pattern | Where |
|---------|-------|
| State machine (pure static) | `TaskStateMachine.isValid(from, to)` |
| Bottom-up status aggregation | `ReleaseFlowAggregation` (pure static methods) |
| Optimistic locking | `@Version Long version` on `Task`, `ReleaseFlow`, `Request` |
| Transaction boundary | `@Transactional` on service methods; `Propagation.REQUIRES_NEW` in `AuditLoggerService` |
| Audit-first | `AuditLoggerService.log()` in every state-changing op; failures swallowed |
| DTO separation | Entities never returned from controllers; Java records with `static from(Entity)` |
| JSON columns | `@Convert(converter = JsonAttributeConverter.class)` + `columnDefinition = "CLOB"` |
| UUID generation | `@PrePersist` sets `id = UUID.randomUUID().toString()` |

---

## 12. File Map (Current State)

```
src/main/java/com/wwa/deploymentagent/
├── contracts/
│   ├── UserContext.java
│   ├── enums/
│   │   ├── ExecutionType.java, TaskStatus.java, FlowStatus.java, RequestStatus.java
│   │   ├── Stage.java (with Stage.next()), ExecutionStatus.java, ReviewStatus.java
│   │   ├── AuditActionType.java, ConfigKey.java, Role.java, SummaryStatus.java
│   └── dto/
│       ├── TaskDto.java, ReleaseFlowListItemDto.java, ReleaseFlowDetailDto.java
│       ├── RequestDto.java, TaskExecutionHistoryDto.java, AuditLogEntryDto.java
│       ├── ConfigurationItemDto.java, DecisionRequestDto.java, PaginatedResponseDto.java
│       ├── ErrorResponseDto.java, UploadResponseDto.java, RecordResultRequestDto.java
├── domain/
│   ├── task/
│   │   ├── Task.java, TaskRepository.java, TaskStateMachine.java, CreateTaskInput.java
│   │   ├── TaskService.java
│   │   ├── TaskExecutionHistory.java, TaskExecutionHistoryRepository.java
│   │   ├── TaskExecutionHistoryService.java
│   │   └── RecordResultService.java
│   ├── releaseflow/
│   │   ├── ReleaseFlow.java, ReleaseFlowRepository.java, ReleaseFlowService.java
│   │   ├── ReleaseFlowAggregation.java
│   │   ├── Request.java, RequestRepository.java
│   ├── decision/
│   │   ├── DecisionEngine.java, DecisionType.java
│   │   └── ReleaseFlowProgressionService.java
│   ├── audit/
│   │   ├── AuditLogEntry.java, AuditLogRepository.java, AuditLoggerService.java
│   ├── configuration/
│   │   ├── ConfigurationItem.java, ConfigurationRepository.java, ConfigurationService.java
│   └── fileimport/
│       ├── ParsedTaskRow.java, ImportError.java, ParseResult.java, ImportResult.java
│       ├── ExcelParserService.java, ImportService.java
├── errors/
│   ├── AppException.java, NotFoundAppException.java, ConflictAppException.java
│   ├── ValidationAppException.java, ForbiddenAppException.java, UnauthorizedAppException.java
│   ├── InvalidStateTransitionException.java, OptimisticLockConflictException.java
│   └── ImportValidationException.java
├── web/
│   ├── controller/
│   │   ├── TaskController.java         GET/PUT /tasks, GET /executions, POST /record-result
│   │   ├── DecisionController.java     POST /tasks/{id}/decision
│   │   ├── ReleaseFlowController.java  GET /release-flows, /release-flows/{id}
│   │   ├── AuditLogController.java     GET /audit-logs
│   │   ├── ConfigurationController.java GET/POST /config
│   │   └── UploadController.java       POST /upload
│   ├── exception/GlobalExceptionHandler.java
│   └── security/
│       ├── HeaderAuthFilter.java, UserContextAuthentication.java
├── config/SecurityConfig.java
├── util/JsonAttributeConverter.java
└── DeploymentAgentApplication.java

src/test/java/com/wwa/deploymentagent/
├── helper/TestDataHelper.java
├── domain/
│   ├── task/  TaskStateMachineTest, TaskServiceTest, TaskExecutionHistoryServiceTest, RecordResultServiceTest
│   ├── decision/  DecisionEngineTest, ReleaseFlowProgressionServiceTest
│   ├── releaseflow/  ReleaseFlowAggregationTest, ReleaseFlowServiceTest
│   ├── audit/  AuditLoggerServiceTest
│   ├── configuration/  ConfigurationServiceTest
│   └── fileimport/  ExcelParserServiceTest, ImportServiceTest

frontend/                          ← Vue 3 app (npm run dev → :5173, proxies /api → :8080)
├── package.json                   (vue@3.4, pinia@2.1, vue-router@4.3, axios@1.6, vite@5.1)
├── vite.config.ts                 (proxy /api → http://localhost:8080)
├── src/
│   ├── types/index.ts             (all shared TS types + enums)
│   ├── api/
│   │   ├── client.ts              (axios instance + X-User-Id/Role headers)
│   │   ├── releaseFlows.ts, tasks.ts, upload.ts, config.ts, audit.ts
│   ├── stores/
│   │   ├── user.ts                (role/userId; isTL/isDeveloper/isDevOpsAdmin/isAuditMgmt)
│   │   ├── releaseFlow.ts         (list, detail, polling, filters)
│   │   ├── task.ts, config.ts, audit.ts
│   ├── router/index.ts            (release-flows, release-flows/:id, /config, /audit)
│   ├── views/
│   │   ├── WorkspaceLayout.vue    (T11.1 — sidebar nav + topbar + router-view)
│   │   ├── ReleaseFlowSummaryView.vue  (T11.2 — table, filters, pagination, Upload btn, polling)
│   │   ├── ReleaseFlowDetailView.vue   (T11.3+T11.4 — header, stage tabs, task table, actions)
│   │   ├── AuditLogView.vue       (T11.8 — paginated; AUDIT_MGMT only)
│   │   └── ConfigAdminView.vue    (T2.3 — inline edit; DEVOPS_ADMIN only)
│   ├── components/
│   │   ├── UploadDialog.vue       (T11.5 — stage select + file picker, shows release ID on success)
│   │   ├── RecordResultDialog.vue (T11.5b — ref panel + result textarea; MANUAL only)
│   │   ├── TaskEditDialog.vue     (T11.6 — edit script/params; TL only)
│   │   └── DecisionDialog.vue     (T11.7 — Approve/Reject/Rerun/Skip radio; TL only)
│   └── assets/main.css            (global styles, badges, buttons, table, modal, spinner)
```

---

## 13. REST API Reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/deployment-agent/release-flows` | any | List (paginated, filterable by project/status/stage) |
| `GET`  | `/api/deployment-agent/release-flows/{id}` | any | Detail with full request+task hierarchy |
| `GET`  | `/api/deployment-agent/tasks?requestId=X` | any | List tasks for a request |
| `GET`  | `/api/deployment-agent/tasks/{id}` | any | Single task detail |
| `PUT`  | `/api/deployment-agent/tasks/{id}/input` | TL | Edit task input parameters |
| `GET`  | `/api/deployment-agent/tasks/{id}/executions` | any | Execution history for a task |
| `POST` | `/api/deployment-agent/tasks/{id}/decision` | TL | Apply decision (approve/reject/rerun/skip) |
| `POST` | `/api/deployment-agent/tasks/{id}/record-result` | TL | Record MANUAL task result |
| `POST` | `/api/deployment-agent/upload` | DEV/TL | Upload XLSX (multipart: `file` + `stage`) |
| `GET`  | `/api/deployment-agent/audit-logs` | AUDIT/MGMT/DEVOPS | Paginated audit log |
| `GET`  | `/api/deployment-agent/config` | any | List configuration items |
| `POST` | `/api/deployment-agent/config` | DEVOPS_ADMIN | Upsert configuration item |

---

## 14. Verification Checklist

| Item | Status |
|------|--------|
| All entities finalized with correct columns | ✅ |
| Optimistic locking on Task, ReleaseFlow, Request | ✅ |
| State machine frozen and tested | ✅ |
| Decision engine: TL-only, transactional, audited | ✅ |
| Progression: request → stage → flow, auto-ready next task | ✅ |
| 12 REST endpoints wired | ✅ |
| Error handling: GlobalExceptionHandler, no stack leak | ✅ |
| DTO separation: no entity leaks to controller layer | ✅ |
| Excel import (T6.1–T6.3): parse + validate + upsert | ✅ |
| Record Result MANUAL path (T8.1b) | ✅ |
| Hierarchical query optimization (T4.3) | ✅ |
| Domain unit tests (T13.1) | ✅ |
| T1.4 caching baseline (Should priority) | ❌ Deferred |
| AUTO execution orchestration (T8.1–T8.3) | ❌ Blocked (RESOLVE-Q3) |
| Callback endpoint (T9.1–T9.4) | ❌ Blocked (RESOLVE-Q2/Q4) |
| Full WWA auth integration (T10.4) | ❌ Blocked (RESOLVE-Q5) |
| Frontend (T11.x, T12.x, T2.3) | ✅ Done (`frontend/` — Vue 3/Vite/Pinia/axios) |
| API contract tests (T13.3) | ❌ Not started |
| Integration/E2E tests (T13.2, T13.7) | ❌ Not started |
| Authorization/security tests (T13.5) | ❌ Not started |
