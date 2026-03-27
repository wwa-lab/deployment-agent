# Data Model: Release Agent

**Date:** 2026-03-24
**Status:** Implemented (current MVP + partial Phase 1 access governance)
**Source:** spec.md (primary), JPA entity classes (validation)
**ORM:** Spring Data JPA · Hibernate
**Production DB:** Oracle · **Test DB:** H2 (create-drop)

---

## Overview

The Release Agent currently persists seven implemented entity types in Oracle. The current workspace already includes an `Access Grant` entity with scope grants so the system can enforce deny-by-default product entry and scoped visibility, alongside the existing hierarchical workflow model (Release Flow → Request → Task → Execution History), append-only audit logging, and runtime configuration management. Existing structured attributes use converter-backed `CLOB` storage; `assigned_roles` and `scope_grants` on `DA_ACCESS_GRANT` are stored as JSON arrays via attribute converters.

---

## Entity Relationship Diagram

```
┌─────────────────────┐
│  DA_RELEASE_FLOW    │
│  (PK: id)           │
│  version (optimistic│
│  locking)           │
└────────┬────────────┘
         │ 1:N
         ▼
┌─────────────────────┐
│  DA_REQUEST         │
│  (PK: id)           │
│  (FK: release_flow) │
│  scope + owner      │
│  version            │
└────────┬────────────┘
         │ 1:N
         ▼
┌─────────────────────┐       1:N       ┌──────────────────────────────┐
│  DA_TASK            │ ──────────────► │  DA_TASK_EXECUTION_HISTORY   │
│  (PK: id)           │                 │  (PK: id)                    │
│  (FK: request)      │                 │  (FK: task)                  │
│  version             │                 │  (UQ: task_id + attempt_num) │
└─────────────────────┘                 └──────────────────────────────┘


┌─────────────────────┐                 ┌──────────────────────────────┐
│  DA_CONFIGURATION   │                 │  DA_AUDIT_LOG_ENTRY          │
│  _ITEM              │                 │  (PK: id)                    │
│  (PK: config_key)   │                 │  (soft refs to RF/Req/Task)  │
│  no version         │                 │  scope fields + append-only  │
└─────────────────────┘                 └──────────────────────────────┘

┌─────────────────────┐
│  DA_ACCESS_GRANT    │
│  (PK: employee_id)  │
│  product entry      │
│  roles + scopes     │
└─────────────────────┘
```

---

## Entity Definitions

### DA_RELEASE_FLOW

Top-level deployment journey across stages. One Release Flow per project, identified by `project_id`.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR(36) | No (PK) | UUID, generated at persist |
| `project_id` | VARCHAR(255) | No | From Excel `Project ID`; grouping key |
| `project_name` | VARCHAR(255) | No | From Excel `Project Name`; display |
| `release_id` | VARCHAR(255) | Yes | System-generated: `{stage}-{normalized}-{seq}` |
| `normalized_release_id` | VARCHAR(255) | No | Lowercase trimmed release_id for uniqueness |
| `current_stage` | VARCHAR(10) | No | Enum: `SIT`, `UAT`, `PROD` |
| `flow_status` | VARCHAR(30) | No | Enum: `Pending`, `Running`, `Completed`, `Failed`, `Rejected` |
| `review_status` | VARCHAR(30) | No | Enum: `Pending_Review`, `Approved`, `Rejected` |
| `review_owner` | VARCHAR(255) | Yes | User ID of review owner |
| `created_at` | TIMESTAMP | No | Auto-populated, immutable |
| `updated_at` | TIMESTAMP | No | Auto-updated |
| `version` | BIGINT | No | Optimistic locking counter |

**Indexes:**
- `IDX_RF_PROJECT_RELEASE` on (`project_id`, `normalized_release_id`) — UNIQUE

---

### DA_REQUEST

Stage-scoped unit within a Release Flow. One Request per (Release Flow, Stage) pair.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR(36) | No (PK) | UUID |
| `release_flow_id` | VARCHAR(36) | No (FK) | References DA_RELEASE_FLOW |
| `stage` | VARCHAR(10) | No | Enum: `SIT`, `UAT`, `PROD` |
| `request_status` | VARCHAR(30) | No | Enum: `Pending`, `Running`, `Completed`, `Failed`, `Skipped`, `Rejected` |
| `snow_group` | VARCHAR(255) | Yes | Runtime scope / owning support group |
| `application` | VARCHAR(255) | Yes | Runtime application scope |
| `agent` | VARCHAR(255) | Yes | Runtime agent label |
| `owner` | VARCHAR(255) | Yes | Rundown owner for request-level control actions |
| `site` | VARCHAR(100) | Yes | Display / operational context |
| `created_by` | VARCHAR(255) | Yes | Uploading or creating user |
| `estimated_remaining_minutes` | NUMBER(10) | Yes | Operator-maintained ETA |
| `archived_at` | TIMESTAMP | Yes | Soft-archive marker |
| `archived_by` | VARCHAR(255) | Yes | User who archived the request |
| `created_at` | TIMESTAMP | No | Auto-populated, immutable |
| `updated_at` | TIMESTAMP | No | Auto-updated |
| `version` | BIGINT | No | Optimistic locking counter |

**Indexes:**
- `IDX_REQ_FLOW_STAGE` on (`release_flow_id`, `stage`)

**Cascade:** Owned by ReleaseFlow (`cascade = ALL`, `orphanRemoval = true`)

---

### DA_TASK

Atomic executable step. One Task per Excel data row. One row in AMH_HCC_task template = one Task.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR(36) | No (PK) | UUID |
| `request_id` | VARCHAR(36) | No (FK) | References DA_REQUEST |
| `task_group_id` | VARCHAR(255) | No | From Excel `Task ID`; display grouping |
| `task_group_name` | VARCHAR(255) | No | From Excel `Task Name`; display label |
| `step_seq` | INTEGER | No | From Excel `Step seq#`; execution ordering |
| `task_name` | VARCHAR(255) | No | From Excel `Step`; atomic step identity |
| `execution_type` | VARCHAR(10) | No | Enum: `MANUAL`, `AUTO` |
| `task_status` | VARCHAR(30) | No | See Task State Model below |
| `input_parameters` | CLOB | Yes | JSON: `{"script": "...", "parameters": "..."}` |
| `expected_output` | CLOB | Yes | From Excel `Parameter (Expected Output)` |
| `owner` | VARCHAR(255) | Yes | From Excel `Owner`; display only |
| `planned_start_time` | TIMESTAMP | Yes | From Excel; display only, does NOT gate execution |
| `planned_end_time` | TIMESTAMP | Yes | From Excel; display only |
| `import_metadata` | CLOB | Yes | JSON blob: `{activity_category, common, dependencies, validation}` |
| `current_result_summary` | CLOB | Yes | JSON; latest execution result |
| `latest_execution_id` | VARCHAR(36) | Yes | FK to latest TaskExecutionHistory |
| `start_time` | TIMESTAMP | Yes | Actual start; set by execution service |
| `end_time` | TIMESTAMP | Yes | Actual end; set on completion |
| `last_updated_at` | TIMESTAMP | No | Auto-updated |
| `version` | BIGINT | No | Optimistic locking counter |

**Indexes:**
- `IDX_TASK_REQUEST` on (`request_id`)
- `IDX_TASK_STATUS` on (`task_status`)
- `IDX_TASK_GROUP_SEQ` on (`task_group_id`, `step_seq`)
- `IDX_TASK_EXECUTION_TYPE` on (`execution_type`)

**Cascade:** Owned by Request (`cascade = ALL`, `orphanRemoval = true`)

---

### DA_TASK_EXECUTION_HISTORY

Per-attempt execution record. Same `task_id` across reruns; `attempt_number` increments.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR(36) | No (PK) | UUID |
| `task_id` | VARCHAR(36) | No (FK) | References DA_TASK |
| `attempt_number` | INTEGER | No | 1-based attempt counter |
| `execution_status` | VARCHAR(30) | No | Enum: `Running`, `Completed`, `Failed`, `Timed_Out` |
| `input_snapshot` | CLOB | Yes | JSON; copy of input_parameters at execution time |
| `result_summary` | CLOB | Yes | JSON; execution result |
| `result_logs` | CLOB | Yes | Raw execution output text |
| `start_time` | TIMESTAMP | No | Execution start |
| `end_time` | TIMESTAMP | Yes | Execution end (null while running) |
| `external_system_type` | VARCHAR(30) | Yes | `JENKINS` or `ANSIBLE`; null for MANUAL |
| `external_execution_id` | VARCHAR(255) | Yes | Build/job ID in external system |
| `external_job_url` | VARCHAR(2000) | Yes | Clickable URL to external job |
| `submitted_at` | TIMESTAMP | Yes | When submission was sent |
| `submission_status` | VARCHAR(30) | Yes | `SUBMITTED` or `FAILED` |
| `submission_message` | VARCHAR(2000) | Yes | Success/error message |

**Indexes:**
- `IDX_TEH_TASK_ATTEMPT` on (`task_id`, `attempt_number`) — UNIQUE
- `IDX_TEH_TASK` on (`task_id`)

**Note:** The six `external_*` / `submission_*` columns were added in migration `V2__add_external_execution_columns.sql` for AUTO task support. They are null for MANUAL tasks.

---

### DA_CONFIGURATION_ITEM

Runtime configuration managed by DevOps Admin. Enum primary key — no surrogate ID.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `config_key` | VARCHAR(100) | No (PK) | Enum: see Configuration Keys below |
| `config_value` | VARCHAR(2000) | No | Configuration value |
| `description` | VARCHAR(500) | Yes | Human-readable description |
| `updated_by` | VARCHAR(255) | No | User ID of last updater |
| `updated_at` | TIMESTAMP | No | Auto-updated |

**No version column** — config updates are authoritative overwrites.

---

### DA_AUDIT_LOG_ENTRY

Immutable, append-only record of operator actions. Uses soft references (nullable, no FK constraints) so audit entries survive entity deletion.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR(36) | No (PK) | UUID |
| `operator_id` | VARCHAR(255) | No | Authenticated user ID |
| `operator_role` | VARCHAR(50) | No | User role at action time |
| `action_type` | VARCHAR(50) | No | Enum: see Audit Action Types below |
| `timestamp` | TIMESTAMP | No | Auto-populated, immutable |
| `release_flow_id` | VARCHAR(36) | Yes | Soft reference |
| `request_id` | VARCHAR(36) | Yes | Soft reference |
| `task_id` | VARCHAR(36) | Yes | Soft reference |
| `application` | VARCHAR(255) | Yes | Scope field for filtering / traceability |
| `snow_group` | VARCHAR(255) | Yes | Scope field for filtering / traceability |
| `agent` | VARCHAR(255) | Yes | Scope field for filtering / traceability |
| `context_payload` | CLOB | Yes | JSON; action-specific detail |

**Indexes:**
- `IDX_ALE_TIMESTAMP` on (`timestamp`)
- `IDX_ALE_OPERATOR` on (`operator_id`)
- `IDX_ALE_ACTION_TYPE` on (`action_type`)
- `IDX_ALE_RELEASE_FLOW` on (`release_flow_id`)

**Immutability:** No UPDATE or DELETE operations. AuditLoggerService uses `Propagation.REQUIRES_NEW` so audit writes persist even if the enclosing business transaction rolls back.

---

### DA_ACCESS_GRANT

Product-level authorization record for one enterprise employee within Release Agent. This entity controls whether an authenticated employee may enter the product, what roles are assigned, and which `Application + SNOW Group` records are visible/manageable.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `employee_id` | VARCHAR(255) | No (PK) | Enterprise employee identifier |
| `display_name_snapshot` | VARCHAR(255) | No | Last captured display name from identity source |
| `grant_status` | VARCHAR(30) | No | Enum: `ACTIVE`, `SUSPENDED` |
| `assigned_roles` | CLOB / JSON array | No | One or more Release Agent roles |
| `scope_grants` | CLOB / JSON array | Yes | Zero or more `Application + SNOW Group` visibility grants |
| `note` | VARCHAR(1000) | Yes | Admin note / rationale |
| `last_login_at` | TIMESTAMP | Yes | Last successful product entry time |
| `created_by` | VARCHAR(255) | No | Admin who created the grant |
| `created_at` | TIMESTAMP | No | Creation timestamp |
| `updated_by` | VARCHAR(255) | No | Admin who last changed the grant |
| `updated_at` | TIMESTAMP | No | Last update timestamp |
| `version` | NUMBER(19) | No | Optimistic-lock version |

**Indexes:**
- Primary key on `employee_id`
- Secondary index on `grant_status`

**Lifecycle Notes:**
- Access grants are suspended/reactivated, not physically deleted, so access history remains traceable
- Multi-role assignment is stored as a JSON array in a `CLOB` column for parity with other structured attributes
- Empty `scope_grants` on a `DEVOPS_ADMIN` grant represent global-admin visibility

---

## State Models

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

| From | To | Trigger |
|------|----|---------|
| Pending | Ready_For_Execution | Import promotion or progression |
| Pending | Skipped | Reviewer skip decision |
| Ready_For_Execution | Executing | Run / record result (MANUAL) or Run (AUTO) |
| Ready_For_Execution | Skipped | Reviewer skip decision |
| Executing | Awaiting_Review | Result recorded |
| Executing | Failed | Execution failure or submission failure |
| Awaiting_Review | Approved | Reviewer approve decision |
| Awaiting_Review | Rejected | Reviewer reject decision |
| Rejected | Ready_For_Execution | Reviewer rerun decision (creates new execution history) |
| Failed | Ready_For_Execution | Reviewer rerun decision (creates new execution history) |

### Flow Status

`Pending` → `Running` → `Completed` | `Failed` | `Rejected`

### Request Status

`Pending` → `Running` → `Completed` | `Failed` | `Skipped` | `Rejected`

### Review Status

`Pending_Review` → `Approved` | `Rejected`

### Execution Status

`Running` → `Completed` | `Failed` | `Timed_Out`

### Access Grant Status

`ACTIVE` ↔ `SUSPENDED`

| From | To | Trigger |
|------|----|---------|
| ACTIVE | SUSPENDED | DevOps Admin suspends product access |
| SUSPENDED | ACTIVE | DevOps Admin reactivates product access |

---

## Configuration Keys

| Key | Validation | Consumer |
|-----|-----------|----------|
| `jenkins_url` | Must match `^https?://.+` | JenkinsExecutionAdapter |
| `jenkins_user` | Must not be blank | JenkinsExecutionAdapter |
| `jenkins_api_token` | Must not be blank | JenkinsExecutionAdapter |
| `ansible_url` | Must match `^https?://.+` | AnsibleExecutionAdapter |
| `ansible_user` | Must not be blank | AnsibleExecutionAdapter |
| `ansible_api_token` | Must not be blank | AnsibleExecutionAdapter |
| `execution_callback_endpoint` | Must match `^https://.+` | Reserved for future use |

---

## Audit Action Types

| Action Type | Triggered By | Context Payload |
|------------|-------------|-----------------|
| `upload` | Excel file import | stage, taskCount |
| `edit` | Task input edit | field changes |
| `view_result` | Record manual result | task result |
| `approve` | Reviewer approve decision | decisionType, previousStatus, comment |
| `reject` | Reviewer reject decision | decisionType, previousStatus, comment |
| `rerun` | Reviewer rerun decision | decisionType, previousStatus, comment |
| `skip` | Reviewer skip decision | decisionType, previousStatus, comment |
| `auto_submit` | AUTO task submission | systemType, attemptNumber, submissionStatus, externalJobUrl |
| `config_update` | Config upsert | configKey, oldValue, newValue |
| `access_grant_create` | Access grant creation | target employee, assigned roles, status |
| `access_grant_update` | Access grant role or metadata update | target employee, before/after changes |
| `access_grant_suspend` | Access suspension | target employee, previous status |
| `access_grant_reactivate` | Access reactivation | target employee, previous status |

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| ID generation | UUID (String 36) via `@PrePersist` | Avoids sequence contention across services |
| JSON storage | `@Convert` + `CLOB` | Oracle-compatible; no native JSON column type needed |
| Enum storage | `@Enumerated(STRING)` | Enum constant names match DB values for schema compatibility |
| Optimistic locking | `@Version Long` on RF/Request/Task | Concurrent update protection without pessimistic locks |
| Audit FK strategy | Soft references (nullable, no constraints) | Audit entries survive entity deletion |
| Timestamps | `@CreationTimestamp` / `@UpdateTimestamp` | Hibernate-managed; immutable created_at |
| Access Grant delete model | Suspend / reactivate rather than physical delete | Preserves authorization history and supports deny-by-default governance |
