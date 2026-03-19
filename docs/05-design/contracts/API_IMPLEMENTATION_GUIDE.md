# Deployment Agent — API Implementation Guide

**Date:** 2026-03-19
**Version:** 1.0 (MVP)
**Base Path:** `/api/deployment-agent`
**Backend:** Java 21 / Spring Boot 3.2.4 / Spring MVC
**Auth:** Session-based (Team Book login) with header fallback for tests

---

## Overview

Deployment Agent exposes a REST/JSON API that supports the full deployment workflow lifecycle: Excel upload and import, Release Flow monitoring, task management, human decision gates, AUTO execution submission, configuration administration, and audit logging. All endpoints except `/auth/login` require an authenticated session.

---

## Authentication

### Session Lifecycle

| Operation | Endpoint | Notes |
|-----------|----------|-------|
| Login | `POST /auth/login` | Creates HTTP session; stores UserContext |
| Check session | `GET /auth/me` | Returns current user or 401 |
| Logout | `POST /auth/logout` | Invalidates session |

### Authentication Chain

1. **SessionAuthFilter** — reads `UserContext` from HttpSession attribute `USER_CONTEXT`
2. **HeaderAuthFilter** — fallback; reads `X-User-Id` / `X-User-Role` headers (enabled via `app.auth.header-fallback-enabled=true`; defaults to `false` in production, `true` in test)

### Roles

| Role | Permissions |
|------|------------|
| `DEVELOPER` | Upload files, view Release Flows and tasks |
| `TL` | All Developer permissions + edit task input, record results, make decisions, submit AUTO execution |
| `DEVOPS_ADMIN` | All TL permissions + manage configuration, submit AUTO execution |
| `AUDIT` | Read-only access to audit logs |
| `MANAGEMENT` | Read-only access to audit logs |

### Stub Users (dev/test)

| Employee ID | Name | Role |
|-------------|------|------|
| `emp-001` | Alice Park | DEVELOPER |
| `emp-002` | Bob Kim | TL |
| `emp-003` | Carol Lee | DEVOPS_ADMIN |
| `emp-004` | David Cho | AUDIT |
| `emp-005` | Eve Yoon | MANAGEMENT |

Any non-blank password is accepted by the stub provider.

---

## Error Response Format

All errors return a consistent JSON body:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description",
  "details": null
}
```

### Error Codes

| Code | HTTP Status | Trigger |
|------|------------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid session |
| `FORBIDDEN` | 403 | Insufficient role for the requested action |
| `NOT_FOUND` | 404 | Entity does not exist |
| `VALIDATION_ERROR` | 400 | Request body fails validation |
| `IMPORT_VALIDATION_ERROR` | 422 | Excel data fails schema/business rule validation |
| `INVALID_STATE_TRANSITION` | 409 | Task is not in a valid state for the requested operation |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | Concurrent update detected; reload and retry |
| `CONFLICT` | 409 | Business rule conflict (e.g., wrong execution type) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## API Endpoints Summary

### Authentication

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Login | POST | `/auth/login` | Public |
| Get current user | GET | `/auth/me` | Session |
| Logout | POST | `/auth/logout` | Session |

### Upload & Import

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Upload Excel file | POST | `/upload` | DEVELOPER, TL |

### Release Flow

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List Release Flows | GET | `/release-flows` | Any authenticated |
| Get Release Flow detail | GET | `/release-flows/{id}` | Any authenticated |

### Task Management

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List tasks by request | GET | `/tasks?requestId={id}` | Any authenticated |
| Get task detail | GET | `/tasks/{id}` | Any authenticated |
| Edit task input | PUT | `/tasks/{id}/input` | TL |
| Get execution history | GET | `/tasks/{id}/executions` | Any authenticated |
| Record manual result | POST | `/tasks/{id}/record-result` | TL |
| Submit AUTO execution | POST | `/tasks/{id}/submit-auto` | TL, DEVOPS_ADMIN |

### Decision

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Apply decision | POST | `/tasks/{id}/decision` | TL |

### Configuration

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List all config items | GET | `/config` | Any authenticated |
| Upsert config item | POST | `/config` | DEVOPS_ADMIN |

### Audit

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List audit log entries | GET | `/audit-logs` | AUDIT, MANAGEMENT, DEVOPS_ADMIN |

---

## Endpoint Reference

---

### Authentication

#### POST /auth/login

Authenticates against Team Book and creates an HTTP session.

**Request Body:**

```json
{
  "employeeId": "emp-002",
  "password": "any-non-blank-value"
}
```

**Response** `200 OK`:

```json
{
  "userId": "emp-002",
  "role": "TL",
  "displayName": "Bob Kim"
}
```

**Errors:**

| Status | When |
|--------|------|
| 401 | Invalid credentials |

**Side effects:** Creates HTTP session with `USER_CONTEXT` attribute.

---

#### GET /auth/me

Returns the currently authenticated user from the session.

**Response** `200 OK`:

```json
{
  "userId": "emp-002",
  "role": "TL",
  "displayName": "Bob Kim"
}
```

**Errors:**

| Status | When |
|--------|------|
| 401 | No active session |

---

#### POST /auth/logout

Invalidates the current session.

**Response:** `200 OK` (empty body)

**Side effects:** Session invalidated, SecurityContext cleared.

---

### Upload & Import

#### POST /upload

Parses an Excel file (AMH_HCC_task sheet) and creates or updates a Release Flow with its Request and Tasks.

**Content-Type:** `multipart/form-data`

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | `.xlsx` file conforming to AMH_HCC_task template |
| `stage` | String | Yes | `SIT`, `UAT`, or `PROD` |

**Response** `200 OK`:

```json
{
  "releaseFlowId": "uuid-string",
  "releaseId": "SIT-my-project-001",
  "stage": "SIT",
  "taskCount": 12
}
```

**Validation:**
- Stage is required and must be a valid enum value
- File is required and must be `.xlsx` format
- Excel must contain sheet named `AMH_HCC_task`
- Required columns: Project ID, Project Name, Task ID, Task Name, Step, Execution Type, Step seq#
- `execution_type` must be `MANUAL` or `AUTO` (case-insensitive)
- `step_seq` must be a positive integer, unique within each `task_group_id`
- AUTO tasks require non-blank `Script to be executed`

**Errors:**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Missing file or invalid stage |
| 403 | `FORBIDDEN` | Role is not DEVELOPER or TL |
| 422 | `IMPORT_VALIDATION_ERROR` | Excel data fails validation (details includes row/field errors) |

**Side effects:**
- Creates or updates one Release Flow (grouped by `Project ID`)
- Creates one Request for the selected stage
- Creates one Task per data row (all start in `Pending`)
- First task promoted to `Ready_For_Execution`
- Audit log entry (`upload`)
- Import is atomic: validation failure creates no records

---

### Release Flow

#### GET /release-flows

Returns a paginated list of Release Flows.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | String | — | Filter by project ID (partial match) |
| `status` | FlowStatus | — | `Pending`, `Running`, `Completed`, `Failed`, `Rejected` |
| `stage` | Stage | — | `SIT`, `UAT`, `PROD` |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `10` | Page size (max 100) |

**Response** `200 OK`:

```json
{
  "data": [
    {
      "id": "uuid",
      "projectId": "PRJ-001",
      "projectName": "My Project",
      "releaseId": "SIT-my-project-001",
      "normalizedReleaseId": "sit-my-project-001",
      "currentStage": "SIT",
      "flowStatus": "Running",
      "reviewStatus": "Pending_Review"
    }
  ],
  "total": 42,
  "page": 0,
  "size": 10
}
```

---

#### GET /release-flows/{id}

Returns full Release Flow detail including nested Requests and Tasks.

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | String | Release Flow UUID |

**Response** `200 OK`:

```json
{
  "id": "uuid",
  "projectId": "PRJ-001",
  "projectName": "My Project",
  "releaseId": "SIT-my-project-001",
  "normalizedReleaseId": "sit-my-project-001",
  "currentStage": "SIT",
  "flowStatus": "Running",
  "reviewStatus": "Pending_Review",
  "requests": [
    {
      "id": "uuid",
      "releaseFlowId": "uuid",
      "stage": "SIT",
      "requestStatus": "Running",
      "tasks": [
        {
          "id": "uuid",
          "requestId": "uuid",
          "taskGroupId": "T1",
          "taskGroupName": "Deploy DB",
          "stepSeq": 1,
          "taskName": "Run migration",
          "executionType": "AUTO",
          "taskStatus": "Awaiting_Review",
          "inputParameters": { "script": "migrate.sh", "parameters": "--env sit" },
          "expectedOutput": "Migration complete",
          "owner": "ops-team",
          "plannedStartTime": null,
          "plannedEndTime": null,
          "currentResultSummary": { "output": "3 tables migrated" },
          "latestExecutionId": "uuid",
          "version": 2
        }
      ]
    }
  ]
}
```

**Errors:** `404` if Release Flow not found.

---

### Task Management

#### GET /tasks

Returns tasks belonging to a specific Request.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `requestId` | String | Yes | Request UUID |

**Response** `200 OK`: `TaskDto[]`

---

#### GET /tasks/{id}

Returns a single task.

**Response** `200 OK`: `TaskDto`

**Errors:** `404` if task not found.

---

#### PUT /tasks/{id}/input

Updates a task's input parameters. Only allowed when the task is in `Pending` or `Ready_For_Execution` status.

**Auth:** TL only

**Request Body:**

```json
{
  "script": "deploy.sh",
  "parameters": "--env sit --version 1.2.3"
}
```

The body is a `Map<String, Object>` representing the new `inputParameters`.

**Response** `200 OK`: Updated `TaskDto`

**Errors:**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Null input or task not in editable state |
| 403 | `FORBIDDEN` | Role is not TL |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | Concurrent update detected |

**Side effects:** Audit log entry (`edit`).

---

#### GET /tasks/{id}/executions

Returns execution history for a task, ordered by attempt number.

**Response** `200 OK`:

```json
[
  {
    "id": "uuid",
    "taskId": "uuid",
    "attemptNumber": 1,
    "executionStatus": "Completed",
    "inputSnapshot": { "script": "deploy.sh", "parameters": "--env sit" },
    "resultSummary": { "output": "Deployed successfully" },
    "resultLogs": "full log output...",
    "startTime": "2026-03-19T10:00:00Z",
    "endTime": "2026-03-19T10:05:00Z",
    "externalSystemType": "JENKINS",
    "externalExecutionId": "42",
    "externalJobUrl": "https://jenkins.example.com/job/deploy/42/console",
    "submittedAt": "2026-03-19T10:00:00Z",
    "submissionStatus": "SUBMITTED",
    "submissionMessage": "Build queued successfully"
  }
]
```

The six `external*` / `submission*` fields are populated only for AUTO tasks submitted via `/submit-auto`. They are `null` for MANUAL tasks.

---

#### POST /tasks/{id}/record-result

Records the result of a MANUAL task. Transitions the task from `Ready_For_Execution` → `Executing` → `Awaiting_Review`.

**Auth:** TL only

**Request Body:**

```json
{
  "resultSummary": { "output": "Migration completed, 3 tables updated" },
  "resultLogs": "optional raw log text"
}
```

**Response** `200 OK`: Updated `TaskDto`

**Errors:**

| Status | Code | When |
|--------|------|------|
| 403 | `FORBIDDEN` | Role is not TL |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `CONFLICT` | Task is not MANUAL or not in `Ready_For_Execution` |

**Side effects:**
- Creates `TaskExecutionHistory` record
- Transitions task through `Executing` to `Awaiting_Review`
- Audit log entry (`view_result`)

---

#### POST /tasks/{id}/submit-auto

Submits an AUTO task to Jenkins or Ansible. Fire-and-forget — the system stores the external job URL but does not wait for callbacks.

**Auth:** TL or DEVOPS_ADMIN

**Request Body:** None

**Response** `200 OK`: Updated `TaskDto`

**Errors:**

| Status | Code | When |
|--------|------|------|
| 403 | `FORBIDDEN` | Role is not TL or DEVOPS_ADMIN |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `CONFLICT` | Task is not AUTO or not in `Ready_For_Execution` |

**Side effects:**
- Creates `TaskExecutionHistory` record
- Transitions task to `Executing`
- Reads credentials from configuration items
- POSTs to Jenkins or Ansible (selected by `inputParameters.system`; defaults to `JENKINS`)
- Stores `externalExecutionId`, `externalJobUrl`, `submissionStatus`
- On external call failure: marks task `Failed`, records error in `submissionMessage`
- Audit log entry (`auto_submit`)

**External call details:**

| System | Auth | Timeout | URL |
|--------|------|---------|-----|
| Jenkins | Basic Auth | 10s / 30s | `{jenkins_url}/job/{script}/buildWithParameters` |
| Ansible | Bearer token | 10s / 30s | `{ansible_url}/api/v2/job_templates/{script}/launch/` |

---

### Decision

#### POST /tasks/{id}/decision

Applies a human decision to a task. Triggers Release Flow progression.

**Auth:** TL only (enforced in DecisionEngine)

**Request Body:**

```json
{
  "decision": "approve",
  "comment": "optional comment"
}
```

| Field | Type | Required | Values |
|-------|------|----------|--------|
| `decision` | DecisionType | Yes (`@NotNull`) | `approve`, `reject`, `rerun`, `skip` |
| `comment` | String | No | Free text |

**Response** `200 OK`: Updated `TaskDto`

**Decision Effects:**

| Decision | Required Task Status | Task Transition | Flow Effect |
|----------|---------------------|----------------|-------------|
| `approve` | `Awaiting_Review` | → `Approved` | Next task promoted; if last → Request/Flow completed |
| `reject` | `Awaiting_Review` | → `Rejected` | Request → Rejected, Flow → Rejected |
| `rerun` | `Rejected` or `Failed` | → `Ready_For_Execution` | New execution history created |
| `skip` | `Pending` or `Ready_For_Execution` | → `Skipped` | Next task promoted; if last → completed |

**Errors:**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Missing `decision` field |
| 403 | `FORBIDDEN` | Role is not TL |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `INVALID_STATE_TRANSITION` | Task not in valid state for decision |

**Side effects:**
- Updates task, request, and release flow statuses
- May promote next task to `Ready_For_Execution`
- Audit log entry (action type matches decision)

---

### Configuration

#### GET /config

Returns all configuration items.

**Response** `200 OK`:

```json
[
  {
    "configKey": "jenkins_url",
    "configValue": "https://jenkins.example.com",
    "description": "Jenkins server base URL",
    "updatedBy": "emp-003",
    "updatedAt": "2026-03-19T10:00:00Z"
  }
]
```

---

#### POST /config

Creates or updates a configuration item.

**Auth:** DEVOPS_ADMIN only

**Request Body:**

```json
{
  "key": "jenkins_url",
  "value": "https://jenkins.example.com",
  "description": "Jenkins server base URL"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `key` | ConfigKey | Yes (`@NotNull`) | Must be a valid ConfigKey enum value |
| `value` | String | Yes (`@NotBlank`) | Per-key validation (see below) |
| `description` | String | No | — |

**Per-key validation:**

| Key | Rule |
|-----|------|
| `jenkins_url`, `ansible_url` | Must match `^https?://.+` |
| `jenkins_user`, `jenkins_api_token`, `ansible_user`, `ansible_api_token` | Must not be blank |
| `execution_callback_endpoint` | Must match `^https://.+` (HTTPS required) |

**Response** `200 OK`: `ConfigurationItemDto`

**Errors:**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Unknown key, blank value, or per-key validation failure |
| 403 | `FORBIDDEN` | Role is not DEVOPS_ADMIN |

**Side effects:** Audit log entry (`config_update`) with `oldValue` and `newValue`.

---

### Audit

#### GET /audit-logs

Returns a paginated list of audit log entries.

**Auth:** AUDIT, MANAGEMENT, or DEVOPS_ADMIN

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `operatorId` | String | — | Filter by operator |
| `actionType` | AuditActionType | — | See Audit Action Types in data model |
| `releaseFlowId` | String | — | Filter by Release Flow |
| `taskId` | String | — | Filter by Task |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

**Response** `200 OK`:

```json
{
  "data": [
    {
      "id": "uuid",
      "timestamp": "2026-03-19T10:00:00Z",
      "operatorId": "emp-002",
      "operatorRole": "TL",
      "actionType": "approve",
      "releaseFlowId": "uuid",
      "requestId": "uuid",
      "taskId": "uuid",
      "contextPayload": { "decisionType": "approve", "previousStatus": "Awaiting_Review", "comment": "Looks good" }
    }
  ],
  "total": 156,
  "page": 0,
  "size": 20
}
```

**Errors:** `403` if role is not AUDIT, MANAGEMENT, or DEVOPS_ADMIN.

---

## State Reference

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

### Flow Status

`Pending` → `Running` → `Completed` | `Failed` | `Rejected`

### Request Status

`Pending` → `Running` → `Completed` | `Failed` | `Skipped` | `Rejected`

---

## Concurrency

Task, Request, and Release Flow entities use optimistic locking via a `version` field. If a concurrent modification has occurred, the server returns `409 OPTIMISTIC_LOCK_CONFLICT`. The client should reload and retry.

---

## Integration Dependencies

| Dependency | Required Config Keys | Protocol | Timeout |
|------------|---------------------|----------|---------|
| Jenkins | `jenkins_url`, `jenkins_user`, `jenkins_api_token` | REST + Basic Auth | 10s connect / 30s read |
| Ansible Tower | `ansible_url`, `ansible_user`, `ansible_api_token` | REST + Bearer Token | 10s connect / 30s read |
| Team Book | — (stubbed for MVP) | Interface-based | — |
