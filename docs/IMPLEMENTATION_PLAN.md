# Deployment Agent MVP — Implementation Plan

**Last Updated**: 2026-03-19
**Branch**: `develop-leo`
**Stack**: Java 21 / Spring Boot 3.2.4 / Spring Data JPA / H2 (test) / Oracle (prod) + Vue 3 / Vite / Pinia / Vue Router / Axios (frontend)
**Build**: `mvn test` (167 tests) | `cd frontend && npm install && npm run dev` | `cd frontend && npx vue-tsc --noEmit`
**Primary source of truth for task scope**: `docs/06-tasks/tasks.md`

---

## Phase Status Summary

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| Phase 0 | Design Resolution & Environment Readiness | ✅ Complete | All RESOLVE blockers resolved or eliminated |
| Phase 1 | Foundation & Persistence | ✅ Complete | — |
| Phase 2 | Core Domain Services | ✅ Complete | AUTO execution (T8.1-T8.3) now implemented |
| Phase 3 | API & Integration Layer | ✅ Complete | Session auth replaces WWA headers; callbacks deferred |
| Phase 4 | Frontend | ✅ Complete | Login, role alignment, AUTO submit added |
| Phase 5 | Testing & Verification | ✅ Complete | 167 tests; T13.6 (frontend component tests) deferred |

> **All phases are now complete.** The MANUAL workflow, AUTO execution (fire-and-forget), session-based authentication, and role alignment are all implemented and tested. The only remaining work is connecting to external systems (Team Book API, Jenkins/Ansible credentials) which require no code changes — only configuration and one provider implementation.

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
| `input_parameters` | CLOB (JSON) | `{script, parameters, system}` |
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

### TaskExecutionHistory Entity (`DA_TASK_EXECUTION_HISTORY`)
| Column | Type | Notes |
|--------|------|-------|
| `id` | VARCHAR(36) PK | `@PrePersist` UUID |
| `task_id` | VARCHAR(36) FK | Parent Task |
| `attempt_number` | INTEGER | Incremented per rerun |
| `execution_status` | VARCHAR(30) | `Running` \| `Completed` \| `Failed` \| `Timed_Out` |
| `input_snapshot` | CLOB (JSON) | Snapshot of task input at execution time |
| `result_summary` | CLOB (JSON) | Callback result summary |
| `result_logs` | CLOB | Raw execution logs (MANUAL tasks) |
| `start_time` | TIMESTAMP | Execution start |
| `end_time` | TIMESTAMP | Execution end |
| `external_system_type` | VARCHAR(30) | `JENKINS` \| `ANSIBLE` (nullable, AUTO tasks only) |
| `external_execution_id` | VARCHAR(255) | External build/job ID (nullable) |
| `external_job_url` | VARCHAR(2000) | Clickable URL to external job UI (nullable) |
| `submitted_at` | TIMESTAMP | When submission was sent (nullable) |
| `submission_status` | VARCHAR(30) | `SUBMITTED` \| `FAILED` (nullable) |
| `submission_message` | VARCHAR(2000) | Success or error message (nullable) |

> External execution columns added via `V2__add_external_execution_columns.sql` Oracle DDL migration.

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
- **AuditLogEntry** (`DA_AUDIT_LOG_ENTRY`): append-only; `id`, `operator_id`, `operator_role`, `action_type`, `timestamp`, `release_flow_id`, `request_id`, `task_id`, `context_payload` (CLOB/JSON)
- **ConfigurationItem** (`DA_CONFIGURATION_ITEM`): `config_key` (PK), `config_value`, `description`, `updated_by`, `updated_at`

---

## 2. Locked Design Rules (Must Not Change)

1. **Stage source**: User selects SIT / UAT / PROD at upload time — not from Excel
2. **Release ID generation**: `{stage}-{normalized_project_id}-{seq}` (e.g., `sit-paymenthub-0001`); system-generated; seq = count of existing flows for projectId + 1
3. **Release Flow grouping**: by `project_id` from Excel; same project re-uploads attach new stage to same flow
4. **Execution Type**: `MANUAL` executes externally (TL records result); `AUTO` submits to Jenkins/Ansible (fire-and-forget, stores external job URL)
5. **Rerun model**: same `task_id`, new `TaskExecutionHistory` row with incremented `attempt_number`
6. **Summary display**: only `Done` / `Running` / `Pending` (no raw enum exposure)
7. **RBAC**: Developer = upload + view; TL = view + edit input + decide + record result + submit auto; DevOps Admin = config + operational view + submit auto; Audit = audit log view; Management = audit log view
8. **Config update scope**: applies to future executions only; does not affect in-flight tasks
9. **Auth**: Session-based Team Book login (prod); header fallback for tests only (`app.auth.header-fallback-enabled`)
10. **AUTO execution**: Fire-and-forget; no callbacks in MVP; full logs stay in Jenkins/Ansible

---

## 3. Phase 0 — Design Resolution Status

| Blocker | Status | Resolution |
|---------|--------|------------|
| RESOLVE-Q1 (Excel schema) | ✅ Resolved | All field mappings finalized and implemented |
| RESOLVE-Q6 (Stage/Release ID source) | ✅ Resolved | Stage from upload param; Release ID system-generated |
| RESOLVE-Q2 (Callback auth) | ⏸️ Deferred | No callbacks in MVP; fire-and-forget submission |
| RESOLVE-Q3 (Secret store) | ✅ Eliminated | Credentials stored in DA_CONFIGURATION_ITEM via Config admin page |
| RESOLVE-Q4 (Oracle result storage) | ✅ Eliminated | Full logs stay in Jenkins/Ansible; DA stores external job URL only |
| RESOLVE-Q5 (WWA auth context) | ✅ Replaced | Team Book session login; StubTeamBookAuthenticationProvider for dev/test |

---

## 4. Phase 1 — Foundation & Persistence ✅ Complete

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T1.1** Entities | `Task`, `ReleaseFlow`, `Request`, `TaskExecutionHistory`, `AuditLogEntry`, `ConfigurationItem` | ✅ |
| **T1.2** Repositories | Spring Data JPA: `TaskRepository`, `TaskExecutionHistoryRepository`, `ReleaseFlowRepository`, `RequestRepository`, `AuditLogRepository`, `ConfigurationRepository` | ✅ |
| **T1.3** Transactions & Locking | `@Version Long version` on Task/ReleaseFlow/Request; `@Transactional`; `ObjectOptimisticLockingFailureException` → 409 | ✅ |
| **T1.4** Query Performance | N+1 resolved via `LEFT JOIN FETCH` in `findByReleaseFlowIdWithTasks` and `findByIdWithFullHierarchy` (T4.3); caching deferred | ⚠️ Partial |
| **T1.5** Test DB Setup | `TestDataHelper` `@Component`; H2 in-memory (`application-test.properties`, `MODE=Oracle`); `@SpringBootTest @ActiveProfiles("test") @Transactional` pattern | ✅ |
| **T2.1** Configuration Service | `ConfigurationService`: get/list/upsert, key validation (7 keys incl. Jenkins/Ansible credentials), audit on update | ✅ |
| **T2.2** Configuration Controller | `ConfigurationController`: `GET/POST /api/deployment-agent/config`; DEVOPS_ADMIN auth on write | ✅ |
| **T3.1** Audit Log Entity | `AuditLogEntry`; append-only `AuditLogRepository` | ✅ |
| **T3.2** Audit Logger Service | `AuditLoggerService.log()`: `Propagation.REQUIRES_NEW`; failures swallowed | ✅ |
| **T3.3** Audit Log Endpoint | `AuditLogController`: `GET /api/deployment-agent/audit-logs`; paginated + filtered | ✅ |

---

## 5. Phase 2 — Core Domain Services ✅ Complete

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T4.1** ReleaseFlow Service | `ReleaseFlowService`: create, getById, `getByIdWithFullHierarchy`, list, recomputeAndPersistStatus, advanceStage | ✅ |
| **T4.2** Request handling | `RequestRepository`: `findByReleaseFlowIdWithTasks`, `findByReleaseFlowId`, `findByReleaseFlowIdAndStage` | ✅ |
| **T4.3** Hierarchical Query | `findByIdWithFullHierarchy` in `ReleaseFlowRepository`; single-query path | ✅ |
| **T5.1** Task Service CRUD | `TaskService`: `create(CreateTaskInput)`, `getById`, `listByRequestId`, `updateStatus`, `updateResultMetadata` | ✅ |
| **T5.2** Execution History Service | `TaskExecutionHistoryService`: `createExecution`, `findByTaskId`, `findLatest`, `completeExecution` | ✅ |
| **T5.3** Task Input Editing | `TaskService.editInput()`: state guard (Pending/Ready only), null check, audit | ✅ |
| **T5.4** Result Metadata Update | `TaskService.updateResultMetadata()`: sets `currentResultSummary` + `latestExecutionId` | ✅ |
| **T6.1** Excel Parsing | `ExcelParserService`: parses `AMH_HCC_task` sheet; row-level validation; `Step seq#` uniqueness | ✅ |
| **T6.2** Import Service | `ImportService`: groups by `project_id`; finds/creates `ReleaseFlow`; finds/creates `Request` per stage; upserts tasks | ✅ |
| **T6.3** Upload Controller | `UploadController`: `POST /upload` (multipart); stage validated before file read; DEVELOPER/TL only; 422 on parse errors | ✅ |
| **T7.1** Decision Engine | `DecisionEngine.applyDecision()`: approve/reject/rerun/skip; TL-only; `@Transactional`; audit | ✅ |
| **T7.2** Progression Service | `ReleaseFlowProgressionService.progressAfterDecision()`: request completion, stage advancement, auto-ready next task, bottom-up recompute | ✅ |
| **T7.3** Decision Controller | `DecisionController`: `POST /tasks/{id}/decision`; TL auth | ✅ |
| **T8.1b** Record Result (MANUAL) | `RecordResultService`: MANUAL + `Ready_For_Execution` guard; creates history; transitions to `Awaiting_Review`; triggers progression | ✅ |
| **T8.1** AUTO execution orchestration | `AutoExecutionService`: AUTO + `Ready_For_Execution` guard; creates history; calls adapter; transitions to `Executing` or `Failed` | ✅ |
| **T8.2** Execution adapters | `JenkinsExecutionAdapter` (Basic auth, named params via MultiValueMap); `AnsibleExecutionAdapter` (Bearer auth, ObjectMapper JSON serialization) | ✅ |
| **T8.3** Submission error handling | Adapter failure → task `Failed`, error in `submissionMessage`; triggers progression | ✅ |

---

## 6. Phase 3 — API & Integration Layer ✅ Complete

| Task | Java class / file | Status |
|------|-------------------|--------|
| **T10.1** Release Flow Controllers | `ReleaseFlowController`: list (paginated + filtered), detail (single-query via T4.3) | ✅ |
| **T10.2** Task Controllers | `TaskController`: list, get, edit input (TL), executions, record-result (TL), submit-auto (TL/DEVOPS_ADMIN) | ✅ |
| **T10.3** Error Handling | `GlobalExceptionHandler`: `AppException` → HTTP; optimistic lock → 409; import validation → 422; no stack leak | ✅ |
| **T10.4** Authorization | Session-based login via `AuthController` (login/me/logout); `SessionAuthFilter` reads HttpSession; `HeaderAuthFilter` fallback for tests (gated by property); per-endpoint role checks enforced | ✅ |
| **T10.5** DTOs | Java records: `TaskDto`, `ReleaseFlowListItemDto/DetailDto`, `RequestDto`, `TaskExecutionHistoryDto` (with 6 external fields), `AuditLogEntryDto`, `ConfigurationItemDto`, `PaginatedResponseDto<T>`, `DecisionRequestDto`, `UploadResponseDto`, `RecordResultRequestDto`, `ErrorResponseDto`, `LoginRequestDto`, `AuthResponseDto` | ✅ |
| **Auth** Auth domain | `TeamBookAuthenticationProvider` interface; `StubTeamBookAuthenticationProvider` (5 users, dev/test/default profiles); `AuthService`; `TeamBookEmployee` record | ✅ |
| **Config** RestTemplate | `RestClientConfig`: 10s connect timeout, 30s read timeout | ✅ |
| **DDL** Oracle migration | `V2__add_external_execution_columns.sql`: 6 nullable columns on `DA_TASK_EXECUTION_HISTORY` | ✅ |

### Deferred

| Task | Reason |
|------|--------|
| **T9.1–T9.4** Callback handler/retry | No callbacks in MVP; fire-and-forget submission; deferred to future phase |

---

## 7. Phase 4 — Frontend ✅ Complete

Frontend source at `frontend/`. Vue 3 + Vite + Pinia + Vue Router + Axios. Dev server proxies `/api` → Spring Boot on `:8080`.
**To run**: `cd frontend && npm install && npm run dev` (requires internet on first run).

| Task | Status | File(s) |
|------|--------|---------|
| T11.1 Workspace shell | ✅ Done | `views/WorkspaceLayout.vue` (logout button, user identity display, role-gated nav) |
| T11.2 Release Flow summary view | ✅ Done | `views/ReleaseFlowSummaryView.vue` |
| T11.3 Release Flow detail view | ✅ Done | `views/ReleaseFlowDetailView.vue` (stage tabs, task table, Submit Auto button, external job URL in result modal) |
| T11.4 Task detail view | ✅ Done | `views/ReleaseFlowDetailView.vue` (combined) |
| T11.5 Upload dialog | ✅ Done | `components/UploadDialog.vue` |
| T11.5b Record Result dialog | ✅ Done | `components/RecordResultDialog.vue` |
| T11.6 Task edit dialog | ✅ Done | `components/TaskEditDialog.vue` |
| T11.7 Decision dialog | ✅ Done | `components/DecisionDialog.vue` |
| T11.8 Audit log view | ✅ Done | `views/AuditLogView.vue` (AUDIT or MANAGEMENT role required) |
| Login page | ✅ Done | `views/LoginView.vue` (employee ID + password, stub hint) |
| T12.1 Pinia state management | ✅ Done | `stores/user.ts` (session auth), `releaseFlow.ts`, `task.ts`, `config.ts`, `audit.ts` |
| T12.2 REST client | ✅ Done | `api/client.ts` (withCredentials, 401 redirect), `auth.ts`, `releaseFlows.ts`, `tasks.ts` (incl. submitAutoExecution), `upload.ts`, `config.ts`, `audit.ts` |
| T2.3 Configuration admin view | ✅ Done | `views/ConfigAdminView.vue` |
| Router auth guard | ✅ Done | `router/index.ts` (session check, /login redirect, role-based route protection) |
| Role alignment | ✅ Done | `types/index.ts` (5 separate roles), `stores/user.ts` (isAudit, isManagement, canViewAudit) |

**Key frontend design decisions**:
- Session-based login (replaces role-switcher dropdown); stub users for dev/test
- Router guard: redirects to `/login` if not authenticated; calls `GET /auth/me` to restore session on page reload
- Axios: `withCredentials: true` for session cookies; 401 interceptor redirects to `/login`
- Submit Auto button: visible for AUTO + Ready_For_Execution tasks, TL or DEVOPS_ADMIN role
- External job URL: clickable link in View Result modal with system type badge and submission status
- Audit nav: visible to AUDIT or MANAGEMENT roles (via `canViewAudit` computed)
- Polling: release flow list refreshes every 10s via `startPolling()` / `stopPolling()`
- Status badges: color-coded globally via `src/assets/main.css`

---

## 8. Phase 5 — Testing & Verification ✅ Complete

| Task | Coverage | Status |
|------|----------|--------|
| **T13.1** Domain unit tests | See §9 for full breakdown | ✅ Done |
| **T13.2** Integration workflow tests | End-to-end upload→decision→progression flows | ✅ Done |
| **T13.3** API contract tests | HTTP-level controller tests with MockMvc | ✅ Done |
| **T13.4** Result persistence tests | CLOB storage; pagination correctness | ✅ Done |
| **T13.5** Authorization/security tests | Wrong-role → 403; missing header → 401; session auth | ✅ Done |
| **T13.6** Frontend component tests | Vue 3 component tests | ❌ Deferred (requires Vitest setup) |
| **T13.7** E2E workflow tests | Full upload-to-approval happy path | ✅ Done |

---

## 9. Test Status (Detail)

```
Build: mvn test  (167 tests, 0 failures)
Test DB: H2 in-memory (application-test.properties, MODE=Oracle)
Auth: Header fallback enabled in test profile (app.auth.header-fallback-enabled=true)

domain/task/
  TaskStateMachineTest.java                18 tests  (all transitions, valid + invalid)
  TaskServiceTest.java                     10 tests  (CRUD, state machine, input editing, audit)
  TaskExecutionHistoryServiceTest.java      6 tests  (creation, attempt numbering, completion)
  RecordResultServiceTest.java              6 tests  (success, AUTO guard, state guard, attempt#)

domain/decision/
  DecisionEngineTest.java                  11 tests  (approve/reject/rerun/skip, role/state guards)
  ReleaseFlowProgressionServiceTest.java    3 tests  (request completion, stage advancement, auto-ready)

domain/releaseflow/
  ReleaseFlowAggregationTest.java          18 tests  (bottom-up aggregation, summary status, edge cases)
  ReleaseFlowServiceTest.java              16 tests  (create, getById, hierarchy load, list filters,
                                                       advanceStage, recompute)

domain/audit/
  AuditLoggerServiceTest.java               4 tests  (append, swallow failure)

domain/configuration/
  ConfigurationServiceTest.java             8 tests  (get/upsert, validation, audit)

domain/fileimport/
  ExcelParserServiceTest.java               9 tests  (valid parse, required-field errors, invalid exec type,
                                                       AUTO without script, dup seq#, ignored columns)
  ImportServiceTest.java                    4 tests  (new project, existing project new stage,
                                                       re-upload upsert, validation error rollback)

domain/execution/
  AutoExecutionServiceTest.java             6 tests  (success, external metadata, adapter failure,
                                                       MANUAL rejection, wrong state, not found)

domain/auth/
  AuthServiceTest.java                      8 tests  (valid employees x5, unknown, blank password, null password)

web/
  TaskControllerTest.java                   6 tests  (list, get, edit, executions, record-result, submit-auto)
  ReleaseFlowControllerTest.java            7 tests  (list, detail, filters)
  ConfigurationControllerTest.java          3 tests  (list, upsert, auth)
  SecurityTest.java                         7 tests  (role enforcement, missing auth)
  ResultPersistenceTest.java                4 tests  (CLOB storage, pagination)
  AuthControllerTest.java                   6 tests  (login success, login failure, me with/without session,
                                                       logout, login accessible without auth)

workflow/
  ExcelImportWorkflowTest.java              3 tests  (import, re-import, validation)
  ManualTaskWorkflowTest.java               4 tests  (full manual happy path)
```

---

## 10. Critical Path

```
[✅] Phase 0 — Design Resolution (all blockers resolved/deferred)
[✅] Phase 1 — Foundation (T1.x, T2.x, T3.x)
[✅] Phase 2 — Core Domain (T4.x–T8.x all done, incl. AUTO execution)
[✅] Phase 3 — API Layer (T10.x all done; T9.x callbacks deferred; auth complete)
[✅] Phase 4 — Frontend (T11.x, T12.x, T2.3 + login + role alignment + AUTO submit)
[✅] Phase 5 — Testing (167 tests; T13.6 frontend component tests deferred)
```

### Remaining External Dependencies (no code changes needed)

```
[⏳] Team Book API contract ──► build RealTeamBookAuthenticationProvider (@Profile("prod"))
[⏳] Jenkins credentials    ──► enter via Config admin page (jenkins_url, jenkins_user, jenkins_api_token)
[⏳] Ansible credentials    ──► enter via Config admin page (ansible_url, ansible_user, ansible_api_token)
```

---

## 11. Architecture Summary

### Stack
- **Runtime**: Java 21
- **Framework**: Spring Boot 3.2.4 (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`)
- **ORM**: Spring Data JPA / Hibernate 6
- **DB**: Oracle (`ojdbc11`, production); H2 in-memory with `MODE=Oracle` (tests)
- **Excel parsing**: Apache POI 5.2.5 (`poi-ooxml`)
- **Auth**: Session-based Team Book login (`SessionAuthFilter` → `UserContextAuthentication`); header fallback for tests (`HeaderAuthFilter`, gated by `app.auth.header-fallback-enabled`); stub provider for dev/test
- **HTTP client**: `RestTemplate` with 10s connect / 30s read timeouts (for Jenkins/Ansible calls)
- **Build**: Maven 3 / `spring-boot-maven-plugin`

### Architecture Boundaries (enforced)
- Controllers in `web/controller/` — no persistence logic, no domain logic
- Domain logic in `domain/` — no HTTP concerns
- Shared types in `contracts/` — entities never returned from controllers
- Security filters in `web/security/` — auth chain: SessionAuthFilter → HeaderAuthFilter

### Key Patterns
| Pattern | Where |
|---------|-------|
| State machine (pure static) | `TaskStateMachine.isValid(from, to)` |
| Bottom-up status aggregation | `ReleaseFlowAggregation` (pure static methods) |
| Optimistic locking | `@Version Long version` on `Task`, `ReleaseFlow`, `Request` |
| Transaction boundary | `@Transactional` on service methods; `Propagation.REQUIRES_NEW` in `AuditLoggerService` |
| Audit-first | `AuditLoggerService.log()` in every state-changing op; failures swallowed; uses authenticated session identity |
| DTO separation | Entities never returned from controllers; Java records with `static from(Entity)` |
| JSON columns | `@Convert(converter = JsonAttributeConverter.class)` + `columnDefinition = "CLOB"` |
| UUID generation | `@PrePersist` sets `id = UUID.randomUUID().toString()` |
| Adapter pattern | `AutoExecutionAdapter` interface; `JenkinsExecutionAdapter`, `AnsibleExecutionAdapter` selected by `inputParameters.system` |
| Provider pattern | `TeamBookAuthenticationProvider` interface; stub for dev/test, real impl for prod |

---

## 12. File Map (Current State)

```
src/main/java/com/wwa/deploymentagent/
├── DeploymentAgentApplication.java
├── config/
│   ├── RestClientConfig.java              RestTemplate with timeouts
│   └── SecurityConfig.java               Session auth + filter chain
├── contracts/
│   ├── UserContext.java
│   ├── enums/
│   │   ├── AuditActionType.java           9 values (incl. auto_submit)
│   │   ├── ConfigKey.java                 7 values (incl. jenkins/ansible user/token)
│   │   ├── ExecutionStatus.java, ExecutionType.java, FlowStatus.java
│   │   ├── RequestStatus.java, ReviewStatus.java, Role.java (5 roles)
│   │   ├── Stage.java (with Stage.next()), SummaryStatus.java, TaskStatus.java
│   └── dto/
│       ├── AuthResponseDto.java, LoginRequestDto.java
│       ├── TaskDto.java, TaskExecutionHistoryDto.java (with 6 external fields)
│       ├── ReleaseFlowListItemDto.java, ReleaseFlowDetailDto.java, RequestDto.java
│       ├── AuditLogEntryDto.java, ConfigurationItemDto.java
│       ├── DecisionRequestDto.java, PaginatedResponseDto.java
│       ├── ErrorResponseDto.java, UploadResponseDto.java, RecordResultRequestDto.java
├── domain/
│   ├── auth/
│   │   ├── TeamBookAuthenticationProvider.java   Interface
│   │   ├── StubTeamBookAuthenticationProvider.java   @Profile("dev","test","default")
│   │   ├── TeamBookEmployee.java                Record
│   │   └── AuthService.java                     Delegates to provider
│   ├── execution/
│   │   ├── AutoExecutionAdapter.java            Interface
│   │   ├── AutoSubmissionResult.java            Record (success/failure)
│   │   ├── JenkinsExecutionAdapter.java         Basic auth, MultiValueMap params
│   │   ├── AnsibleExecutionAdapter.java         Bearer auth, ObjectMapper JSON
│   │   └── AutoExecutionService.java            Guards + submit + audit
│   ├── task/
│   │   ├── Task.java, TaskRepository.java, TaskStateMachine.java, CreateTaskInput.java
│   │   ├── TaskService.java
│   │   ├── TaskExecutionHistory.java            Incl. 6 external execution columns
│   │   ├── TaskExecutionHistoryRepository.java, TaskExecutionHistoryService.java
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
│   ├── ValidationAppException.java, ForbiddenAppException.java
│   ├── UnauthorizedAppException.java (with message constructor)
│   ├── InvalidStateTransitionException.java, OptimisticLockConflictException.java
│   └── ImportValidationException.java
├── web/
│   ├── controller/
│   │   ├── AuthController.java            POST /auth/login, GET /auth/me, POST /auth/logout
│   │   ├── TaskController.java            + POST /submit-auto
│   │   ├── DecisionController.java        POST /tasks/{id}/decision
│   │   ├── ReleaseFlowController.java     GET /release-flows, /{id}
│   │   ├── AuditLogController.java        GET /audit-logs
│   │   ├── ConfigurationController.java   GET/POST /config
│   │   └── UploadController.java          POST /upload
│   ├── exception/GlobalExceptionHandler.java
│   └── security/
│       ├── SessionAuthFilter.java         Reads UserContext from HttpSession
│       ├── HeaderAuthFilter.java          Fallback; gated by app.auth.header-fallback-enabled
│       └── UserContextAuthentication.java
├── util/JsonAttributeConverter.java
└── resources/
    ├── application.properties             app.auth.header-fallback-enabled=false
    └── db/migration/
        └── V2__add_external_execution_columns.sql

src/test/java/com/wwa/deploymentagent/
├── helper/TestDataHelper.java
├── domain/
│   ├── task/       TaskStateMachineTest, TaskServiceTest, TaskExecutionHistoryServiceTest, RecordResultServiceTest
│   ├── decision/   DecisionEngineTest, ReleaseFlowProgressionServiceTest
│   ├── releaseflow/ ReleaseFlowAggregationTest, ReleaseFlowServiceTest
│   ├── audit/       AuditLoggerServiceTest
│   ├── configuration/ ConfigurationServiceTest
│   ├── fileimport/  ExcelParserServiceTest, ImportServiceTest
│   ├── execution/   AutoExecutionServiceTest
│   └── auth/        AuthServiceTest
├── web/            TaskControllerTest, ReleaseFlowControllerTest, ConfigurationControllerTest,
│                   SecurityTest, ResultPersistenceTest, AuthControllerTest
└── workflow/       ExcelImportWorkflowTest, ManualTaskWorkflowTest

src/test/resources/
└── application-test.properties            app.auth.header-fallback-enabled=true

frontend/                          ← Vue 3 app (npm run dev → :5173, proxies /api → :8080)
├── package.json                   (vue@3.4, pinia@2.1, vue-router@4.3, axios@1.6, vite@5.1)
├── vite.config.ts                 (proxy /api → http://localhost:8080)
├── src/
│   ├── main.ts, App.vue
│   ├── types/index.ts             (all shared TS types + enums; 5 UserRole values)
│   ├── api/
│   │   ├── client.ts              (axios + withCredentials + 401 redirect)
│   │   ├── auth.ts                (login, logout, checkSession)
│   │   ├── releaseFlows.ts, tasks.ts (incl. submitAutoExecution), upload.ts, config.ts, audit.ts
│   ├── stores/
│   │   ├── user.ts                (session auth: login/logout/initSession, isAudit/isManagement/canViewAudit)
│   │   ├── releaseFlow.ts         (list, detail, polling, filters)
│   │   ├── task.ts, config.ts, audit.ts
│   ├── router/index.ts            (/login, /release-flows, /:id, /config, /audit; auth guard)
│   ├── views/
│   │   ├── LoginView.vue          (employee ID + password form)
│   │   ├── WorkspaceLayout.vue    (sidebar nav, user identity, logout button)
│   │   ├── ReleaseFlowSummaryView.vue   (table, filters, pagination, Upload btn, polling)
│   │   ├── ReleaseFlowDetailView.vue    (stage tabs, task table, Submit Auto, external job URL)
│   │   ├── AuditLogView.vue       (paginated; AUDIT or MANAGEMENT only)
│   │   └── ConfigAdminView.vue    (inline edit; DEVOPS_ADMIN only)
│   ├── components/
│   │   ├── UploadDialog.vue, RecordResultDialog.vue, TaskEditDialog.vue, DecisionDialog.vue
│   └── assets/main.css            (global styles, badges, buttons, table, modal, spinner)
```

---

## 13. REST API Reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/deployment-agent/auth/login` | none | Login with employee ID + password |
| `GET`  | `/api/deployment-agent/auth/me` | session | Get current user from session |
| `POST` | `/api/deployment-agent/auth/logout` | session | Invalidate session |
| `GET`  | `/api/deployment-agent/release-flows` | any | List (paginated, filterable by project/status/stage) |
| `GET`  | `/api/deployment-agent/release-flows/{id}` | any | Detail with full request+task hierarchy |
| `GET`  | `/api/deployment-agent/tasks?requestId=X` | any | List tasks for a request |
| `GET`  | `/api/deployment-agent/tasks/{id}` | any | Single task detail |
| `PUT`  | `/api/deployment-agent/tasks/{id}/input` | TL | Edit task input parameters |
| `GET`  | `/api/deployment-agent/tasks/{id}/executions` | any | Execution history for a task |
| `POST` | `/api/deployment-agent/tasks/{id}/decision` | TL | Apply decision (approve/reject/rerun/skip) |
| `POST` | `/api/deployment-agent/tasks/{id}/record-result` | TL | Record MANUAL task result |
| `POST` | `/api/deployment-agent/tasks/{id}/submit-auto` | TL/DEVOPS_ADMIN | Submit AUTO task to Jenkins/Ansible |
| `POST` | `/api/deployment-agent/upload` | DEV/TL | Upload XLSX (multipart: `file` + `stage`) |
| `GET`  | `/api/deployment-agent/audit-logs` | AUDIT/MGMT/DEVOPS | Paginated audit log |
| `GET`  | `/api/deployment-agent/config` | any | List configuration items |
| `POST` | `/api/deployment-agent/config` | DEVOPS_ADMIN | Upsert configuration item |

**Total**: 16 endpoints across 7 controllers

---

## 14. Verification Checklist

| Item | Status |
|------|--------|
| All entities finalized with correct columns | ✅ |
| TaskExecutionHistory has 6 external execution columns | ✅ |
| Oracle DDL migration script (V2) provided | ✅ |
| Optimistic locking on Task, ReleaseFlow, Request | ✅ |
| State machine frozen and tested | ✅ |
| Decision engine: TL-only, transactional, audited | ✅ |
| Progression: request → stage → flow, auto-ready next task | ✅ |
| 16 REST endpoints wired across 7 controllers | ✅ |
| Error handling: GlobalExceptionHandler, no stack leak | ✅ |
| DTO separation: no entity leaks to controller layer | ✅ |
| Excel import (T6.1–T6.3): parse + validate + upsert | ✅ |
| Record Result MANUAL path (T8.1b) | ✅ |
| AUTO execution: submit to Jenkins/Ansible, failure handling | ✅ |
| Jenkins adapter: Basic auth, MultiValueMap named params, queue URL extraction | ✅ |
| Ansible adapter: Bearer auth, ObjectMapper JSON, UI URL | ✅ |
| RestTemplate: 10s connect / 30s read timeouts | ✅ |
| Session-based auth: login/me/logout + SessionAuthFilter | ✅ |
| Header auth fallback: disabled in prod, enabled in test | ✅ |
| Frontend login page + router auth guard | ✅ |
| Frontend role alignment: 5 separate roles, canViewAudit | ✅ |
| Frontend Submit Auto button + external job URL display | ✅ |
| Audit attribution: uses authenticated session identity | ✅ |
| Hierarchical query optimization (T4.3) | ✅ |
| Domain unit tests | ✅ 167 tests |
| Integration workflow tests | ✅ |
| API contract tests | ✅ |
| Authorization/security tests | ✅ |
| Frontend TypeScript compiles clean | ✅ |
| T1.4 caching baseline (Should priority) | ❌ Deferred |
| T9.x Callback handler (deferred from MVP) | ⏸️ Deferred |
| T13.6 Frontend component tests | ❌ Deferred |

---

## 15. UAT Readiness

| Area | Status | Blocker |
|------|--------|---------|
| MANUAL workflow | **Ready now** | None |
| AUTO execution | **Ready now** (tested with mocks) | None |
| AUTO against real Jenkins | **Ready once credentials provided** | Config page entry |
| AUTO against real Ansible | **Ready once credentials provided** | Config page entry |
| Login/session auth | **Ready now** (stub users) | None |
| Login against real Team Book | **Ready once API contract provided** | `RealTeamBookAuthenticationProvider` |
| Role alignment | **Ready now** | None |
| Audit with real identity | **Ready now** | Uses session user's employeeId |
| Oracle DDL | **Ready now** | V2 migration script provided |
