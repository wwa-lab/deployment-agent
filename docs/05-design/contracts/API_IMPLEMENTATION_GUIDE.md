# Deployment Agent — API Implementation Guide

**Date:** 2026-03-28
**Version:** 1.4 (current MVP + owner-driven task controls + Phase 1 Access Management + template-based rundown creation)
**Base Path:** `/api/deployment-agent`
**Backend:** Java 21 / Spring Boot 3.2.0 / Spring MVC
**Auth:** Session-based login via authentication-provider abstraction with local Access Grant resolution and effective permissions

---

## Overview

This guide describes the backend API surface for Deployment Agent. It covers the current MVP workflow APIs plus the currently implemented auth/access-management surface used for deny-by-default product entry and scoped visibility.

**Interpretation rule**
- Implemented auth/access-management endpoints are described as current behavior.
- Follow-up items that are not yet implemented are called out inline where relevant.

---

## Authentication and Authorization

### Session Lifecycle

| Operation | Endpoint | Notes |
|-----------|----------|-------|
| Login | `POST /auth/login` | Authenticates the current login identity; Phase 1 also resolves product access |
| Check session | `GET /auth/me` | Returns current authenticated context |
| Logout | `POST /auth/logout` | Invalidates session |

### Authentication Chain

1. `AuthController` receives login request
2. `AuthService` validates credentials via the configured authentication provider (`TeamBookAuthenticationProvider` in code)
3. Access Grant resolution runs after identity authentication
4. Session stores authenticated user context
5. `SessionAuthFilter` reconstructs request security context
6. `HeaderAuthFilter` remains a controlled fallback for tests/local validation

**Current baseline**
- Local/dev/test environments use the stub provider.
- Deployment Agent owns product authorization through local Access Grants; Team Book remains a future production-provider option.

### Roles and Effective Permissions

| Role | Current / Intended Capability |
|------|-------------------------------|
| `DEVELOPER` | Upload and monitor Release Flows; may act on tasks or rundowns when assigned as owner |
| `TL` | Participates in release workflow like other delivery roles; task/rundown mutation is owner-driven rather than TL-only |
| `DEVOPS_ADMIN` | Workflow management, configuration, archive/restore/purge, and Access Management |
| `AUDIT` | Read-only audit visibility |
| `MANAGEMENT` | Read-only audit / management visibility |

**Current operational rule**
- Task-level mutation endpoints (`edit`, `start-manual`, `record-result`, `submit-auto`, `decision`) are authorized for the task owner or `DEVOPS_ADMIN`, not for TL as a standalone reviewer role.
- Request-level `start` / `fail` actions are authorized for the rundown owner or `DEVOPS_ADMIN`.

**Phase 1 direction**
- Product access is no longer “any authenticated user.”
- Authorization is resolved from one or more assigned roles on the Access Grant.
- Backend should prefer effective-permission evaluation over route-level role string assumptions.
- Visibility and administrative reach are additionally constrained by `Application + SNOW Group` scope grants.

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

All errors should return a consistent JSON body:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description",
  "details": null
}
```

### Error Codes

| Code | HTTP Status | Trigger |
|------|-------------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid credentials/session |
| `FORBIDDEN` | 403 | Authenticated but not permitted for the action |
| `ACCESS_NOT_GRANTED` | 403 | Enterprise-authenticated user has no Access Grant |
| `ACCESS_SUSPENDED` | 403 | Enterprise-authenticated user has a suspended Access Grant |
| `NOT_FOUND` | 404 | Requested entity does not exist |
| `VALIDATION_ERROR` | 400 | Request payload or query fails validation |
| `IMPORT_VALIDATION_ERROR` | 422 | Excel content fails import validation |
| `INVALID_STATE_TRANSITION` | 409 | Requested action is invalid for current workflow state |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | Concurrent update detected |
| `CONFLICT` | 409 | Business rule conflict outside generic state transition handling |
| `INTERNAL_ERROR` | 500 | Unexpected server-side failure |

---

## API Endpoints Summary

### Authentication

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Login | POST | `/auth/login` | Public |
| Get current user | GET | `/auth/me` | Session |
| Logout | POST | `/auth/logout` | Session |

### Access Management

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List Access Grants | GET | `/access-grants` | DEVOPS_ADMIN |
| Search enterprise directory | GET | `/access-grants/directory` | DEVOPS_ADMIN |
| Create Access Grant | POST | `/access-grants` | DEVOPS_ADMIN |
| Update Access Grant | PATCH | `/access-grants/{employeeId}` | DEVOPS_ADMIN |
| Suspend Access Grant | POST | `/access-grants/{employeeId}/suspend` | DEVOPS_ADMIN |
| Reactivate Access Grant | POST | `/access-grants/{employeeId}/reactivate` | DEVOPS_ADMIN |

### Upload and Import

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Upload Excel file | POST | `/upload` | DEVELOPER, TL, DEVOPS_ADMIN |

### Release Flow

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List Release Flows | GET | `/release-flows` | Any authenticated within scoped visibility |
| Get Release Flow detail | GET | `/release-flows/{id}` | Any authenticated within scoped visibility |
| Create rundown from template | POST | `/release-flows/from-template` | DEVELOPER, TL, DEVOPS_ADMIN |
| Update stage rundown | PATCH | `/release-flows/{flowId}/requests/{requestId}/rundown` | DEVELOPER, TL, DEVOPS_ADMIN |
| Archive stage rundown | POST | `/release-flows/{flowId}/requests/{requestId}/archive` | DEVELOPER, TL, DEVOPS_ADMIN |
| Restore archived stage rundown | POST | `/release-flows/{flowId}/requests/{requestId}/restore` | DEVOPS_ADMIN |
| Purge archived stage rundown | DELETE | `/release-flows/{flowId}/requests/{requestId}/purge` | DEVOPS_ADMIN |
| Start stage deployment | POST | `/release-flows/{flowId}/requests/{requestId}/start` | Rundown owner or DEVOPS_ADMIN |
| Mark stage as failed | POST | `/release-flows/{flowId}/requests/{requestId}/fail` | Rundown owner or DEVOPS_ADMIN |

### Task Management

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List tasks by request | GET | `/tasks?requestId={id}` | Any authenticated |
| Get task detail | GET | `/tasks/{id}` | Any authenticated |
| Edit task input | PUT | `/tasks/{id}/input` | Task owner or DEVOPS_ADMIN |
| Get execution history | GET | `/tasks/{id}/executions` | Any authenticated |
| Start MANUAL execution | POST | `/tasks/{id}/start-manual` | Task owner or DEVOPS_ADMIN |
| Record MANUAL result | POST | `/tasks/{id}/record-result` | Task owner or DEVOPS_ADMIN |
| Submit AUTO execution | POST | `/tasks/{id}/submit-auto` | Task owner or DEVOPS_ADMIN |

### Decision

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Apply decision | POST | `/tasks/{id}/decision` | Task owner or DEVOPS_ADMIN |

### Configuration

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List config items | GET | `/config` | Any authenticated |
| Upsert config item | POST | `/config` | DEVOPS_ADMIN |

### Audit

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| List audit log entries | GET | `/audit-logs` | Any authenticated within scoped visibility |

---

## Endpoint Reference

## Authentication

### POST /auth/login

Authenticates the current login identity and creates an HTTP session.

**Request Body**

```json
{
  "employeeId": "emp-003",
  "password": "any-non-blank-value"
}
```

**Current Response** `200 OK`

```json
{
  "userId": "emp-003",
  "role": "DEVOPS_ADMIN",
  "roles": ["DEVOPS_ADMIN"],
  "permissions": ["release.view", "release.upload", "release.rundown.edit", "release.rundown.archive", "release.rundown.start", "release.rundown.fail", "task.edit", "task.run", "task.review", "config.manage", "audit.view", "access.manage", "release.view_archived", "release.rundown.restore", "release.rundown.purge"],
  "displayName": "Carol Lee",
  "scopes": []
}
```

**Errors**

| Status | Code | When |
|--------|------|------|
| 401 | `UNAUTHORIZED` | Invalid login credentials |
| 403 | `ACCESS_NOT_GRANTED` | Valid authenticated identity but no Access Grant |
| 403 | `ACCESS_SUSPENDED` | Valid authenticated identity but suspended Access Grant |

**Side effects**
- Creates HTTP session
- Updates `last_login_at` for active Access Grants

### GET /auth/me

Returns the currently authenticated user context.

**Current Response**

```json
{
  "userId": "emp-003",
  "role": "DEVOPS_ADMIN",
  "roles": ["DEVOPS_ADMIN"],
  "permissions": ["release.view", "release.upload", "release.rundown.edit", "release.rundown.archive", "release.rundown.start", "release.rundown.fail", "task.edit", "task.run", "task.review", "config.manage", "audit.view", "access.manage", "release.view_archived", "release.rundown.restore", "release.rundown.purge"],
  "displayName": "Carol Lee",
  "scopes": []
}
```

**Errors**

| Status | Code | When |
|--------|------|------|
| 401 | `UNAUTHORIZED` | No active session |

### POST /auth/logout

Invalidates the current session.

**Response:** `200 OK`

---

## Access Management

### GET /access-grants

Lists product Access Grants for Deployment Agent administration.

**Query Parameters**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | String | No | Employee ID or display name search term |
| `status` | String | No | `ACTIVE` or `SUSPENDED` |
| `page` | int | No | Zero-based page index |
| `size` | int | No | Page size |

**Response** `200 OK`

```json
{
  "data": [
    {
      "employeeId": "emp-003",
      "displayName": "Carol Lee",
      "grantStatus": "ACTIVE",
      "assignedRoles": ["DEVOPS_ADMIN"],
      "scopeGrants": [],
      "lastLoginAt": "2026-03-24T09:30:00Z",
      "updatedBy": "emp-003",
      "updatedAt": "2026-03-24T09:00:00Z"
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20
}
```

### GET /access-grants/directory

Searches the authentication-provider directory for enterprise users, including users who do not yet have an Access Grant.

**Query Parameters**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | String | Yes | Employee ID or display-name search term |
| `limit` | int | No | Result cap; defaults to a bounded backend value |

**Response** `200 OK`

```json
[
  {
    "employeeId": "emp-006",
    "displayName": "Frank Han (Developer)",
    "hasAccessGrant": false,
    "grantStatus": null
  }
]
```

**Notes**
- Used by the Add User flow in Access Management.
- Directory search is provider-backed; local/dev behavior comes from the stub Team Book provider.

### POST /access-grants

Creates a new Access Grant.

**Request Body**

```json
{
  "employeeId": "emp-006",
  "assignedRoles": ["DEVELOPER"],
  "scopeGrants": [
    { "application": "AMH HCC", "snowGroup": "HTSA-CSI-HCC-AMH-PRJ" }
  ],
  "grantStatus": "ACTIVE",
  "note": "Initial product onboarding"
}
```

**Validation**
- `employeeId` required
- `grantStatus` required
- `assignedRoles` required when `grantStatus = ACTIVE`
- `scopeGrants` must contain valid `application` and `snowGroup` values when provided

**Side effects**
- Audit log entry for access-grant creation

### PATCH /access-grants/{employeeId}

Updates mutable grant fields such as roles, display-name snapshot, or note.

### POST /access-grants/{employeeId}/suspend

Suspends product entry for an existing Access Grant without deleting the record.

### POST /access-grants/{employeeId}/reactivate

Reactivates a suspended Access Grant.

**Errors for Access Management endpoints**

| Status | Code | When |
|--------|------|------|
| 403 | `FORBIDDEN` | Caller is not DEVOPS_ADMIN |
| 404 | `NOT_FOUND` | Grant does not exist |
| 409 | `CONFLICT` | Invalid lifecycle operation for current grant state |

---

## Upload and Import

### POST /upload

Parses an Excel file (`AMH_HCC_task`) and creates or updates a Release Flow for the selected stage.

**Content-Type:** `multipart/form-data`

**Form Fields**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | `.xlsx` file conforming to the template |
| `stage` | String | Yes | `SIT`, `UAT`, or `PROD` |
| `snowGroup` | String | No | Runtime support-group scope for the uploaded rundown |
| `application` | String | No | Runtime application scope for the uploaded rundown |
| `agent` | String | No | Runtime agent label for the uploaded rundown |

**Response** `200 OK`

```json
{
  "releaseFlowId": "uuid-string",
  "releaseId": "sit-my-project-001",
  "stage": "SIT",
  "taskCount": 12,
  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
  "application": "AMH HCC",
  "agent": "Deployment Agent"
}
```

**Validation**
- Stage required and valid
- File required and `.xlsx`
- Fixed worksheet name and required columns must exist
- AUTO rows require executable script value

**Errors**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Missing file or invalid stage |
| 403 | `FORBIDDEN` | Caller lacks upload permission |
| 422 | `IMPORT_VALIDATION_ERROR` | Spreadsheet content fails validation |

**Side effects**
- Creates or updates Release Flow / Request / Task records
- Promotes first eligible task
- Records upload audit entry

---

## Release Flow

### GET /release-flows

Returns a paginated list of Release Flows.

**Query Parameters**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | String | — | Filter by project ID or name |
| `status` | String | — | Flow status filter |
| `stage` | String | — | Current stage filter |
| `application` | String | — | Scope filter by application |
| `snowGroup` | String | — | Scope filter by SNOW group |
| `agent` | String | — | Scope filter by agent |
| `includeArchived` | Boolean | `false` | Admin-only archived visibility |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `10` | Page size |

**Response** `200 OK`

```json
{
  "data": [
    {
      "id": "uuid",
      "projectId": "PRJ-001",
      "projectName": "My Project",
      "releaseId": "sit-my-project-001",
      "currentStage": "SIT",
      "flowStatus": "Running",
      "reviewStatus": "Pending_Review",
      "application": "AMH HCC",
      "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
      "agent": "Deployment Agent",
      "owner": "alice"
    }
  ],
  "total": 42,
  "page": 0,
  "size": 10
}
```

### GET /release-flows/{id}

Returns Release Flow detail, nested stage requests, and tasks.

**Query Parameters**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `includeArchived` | Boolean | `false` | Admin-only archived visibility |

### POST /release-flows/from-template

Creates a new Release Flow request (rundown) from a saved template record, bypassing Excel upload.

**Auth:** DEVELOPER, TL, or DEVOPS_ADMIN

**Request Body**

```json
{
  "templateId": "uuid",
  "templateName": "AMH HCC Standard Deploy",
  "projectId": "AMH-HCC",
  "projectName": "AMH HCC",
  "stage": "SIT",
  "releaseId": "amh-hcc-sit-01",
  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
  "application": "AMH HCC",
  "agent": "Deployment Agent",
  "site": "HK",
  "owner": "alice",
  "estimatedRemainingMinutes": 120,
  "tasks": [
    {
      "category": "Infrastructure",
      "taskName": "Deploy DB Schema",
      "step": 1,
      "stepName": "Run Flyway migration",
      "type": "MANUAL",
      "critical": true,
      "owner": "alice",
      "estDurationMinutes": 30,
      "dependencies": null
    }
  ]
}
```

**Field Notes**

| Field | Required | Notes |
|-------|----------|-------|
| `templateId` | No | Informational reference; not used for lookup |
| `templateName` | No | Captured in audit context |
| `projectId` | No | Derived from `projectName` if omitted |
| `projectName` | Yes | Used as the Release Flow display name |
| `stage` | Yes | `SIT`, `UAT`, or `PROD` |
| `releaseId` | Yes | Must match pattern `xxx-{stage}-NN` (e.g. `amh-hcc-sit-01`) and the stage segment must match the `stage` field |
| `tasks[].step` | Yes | Must be ≥ 1; tasks are sorted by step before creation |
| `tasks[].taskName` | Yes | Maps to `task_group_name` |
| `tasks[].stepName` | Yes | Maps to `task_name` |
| `tasks[].type` | Yes | `MANUAL` or `AUTO` |
| `estimatedRemainingMinutes` | No | Calculated from task durations if omitted |

**Response** `200 OK`

```json
{
  "releaseFlowId": "uuid-string",
  "releaseId": "amh-hcc-sit-01",
  "stage": "SIT",
  "taskCount": 8,
  "snowGroup": "HTSA-CSI-HCC-AMH-PRJ",
  "application": "AMH HCC",
  "agent": "Deployment Agent"
}
```

**Validation**
- `projectName`, `stage`, `releaseId`, and at least one task are required
- Release identifier must conform to `{prefix}-{stage}-{sequence}` pattern
- Stage segment of release identifier must match the `stage` field
- Task `step` must be ≥ 1; `taskName` and `stepName` must be non-blank; `type` must be valid
- Archived release identifiers cannot be reused

**Errors**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Missing or invalid fields |
| 403 | `FORBIDDEN` | Caller lacks rundown edit permission |

**Side effects**
- Creates or reuses a Release Flow for the derived project + release identifier
- Creates a new Request and Tasks for the selected stage
- Records upload audit entry with `source: "template"`

---

### PATCH /release-flows/{flowId}/requests/{requestId}/rundown

Updates stage-level rundown fields such as application, SNOW group, agent, site, estimated remaining time, and rundown owner.

**Validation**
- Runtime scope changes require rundown edit permission and scoped visibility
- `owner` updates are restricted to `DEVOPS_ADMIN`

### POST /release-flows/{flowId}/requests/{requestId}/archive

Archives the selected stage rundown and hides it from default workflow views.

**Response** `200 OK`

```json
{
  "releaseFlowId": "uuid",
  "requestId": "uuid",
  "stage": "SIT",
  "requestArchived": true,
  "releaseFlowArchived": false,
  "activeRequestCount": 1
}
```

### POST /release-flows/{flowId}/requests/{requestId}/restore

Restores an archived stage rundown. If no other active requests existed, restoring also reactivates the parent Release Flow.

### DELETE /release-flows/{flowId}/requests/{requestId}/purge

Permanently deletes an already archived stage rundown.

**Validation**
- Target request must already be archived
- Caller must be DEVOPS_ADMIN

### POST /release-flows/{flowId}/requests/{requestId}/start

Promotes the first pending task in the stage into an executable state.

**Validation**
- Caller must have scoped visibility to the request
- Caller must be the rundown owner or `DEVOPS_ADMIN`

### POST /release-flows/{flowId}/requests/{requestId}/fail

Marks the stage request as failed and recomputes parent aggregate status.

**Validation**
- Caller must have scoped visibility to the request
- Caller must be the rundown owner or `DEVOPS_ADMIN`

---

## Task Management

### GET /tasks

Returns tasks for a specific request.

**Query Parameters**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `requestId` | String | Yes | Request UUID |

### GET /tasks/{id}

Returns the current task record, including execution, owner, and display metadata.

### PUT /tasks/{id}/input

Updates task input parameters while the task is still editable.

**Auth:** Task owner or `DEVOPS_ADMIN`

**Validation**
- Task must be in an editable state
- Parent rundown must not be archived

**Side effects**
- Audit log entry for task edit

### GET /tasks/{id}/executions

Returns execution history in attempt order.

**AUTO execution history fields**
- `externalSystemType`
- `externalExecutionId`
- `externalJobUrl`
- `submittedAt`
- `submissionStatus`
- `submissionMessage`

These fields are `null` for MANUAL attempts.

### POST /tasks/{id}/record-result

Records a MANUAL task result and transitions the task into `Awaiting_Review`.

**Auth:** Task owner or `DEVOPS_ADMIN`

**Request Body**

```json
{
  "resultSummary": { "output": "Migration completed" },
  "resultLogs": "optional free-text log"
}
```

**Validation**
- Task must be `MANUAL`
- Task must be runnable / editable according to workflow rules

**Side effects**
- Creates execution history
- Moves task through execution bookkeeping into review state
- Records audit entry

### POST /tasks/{id}/start-manual

Transitions a MANUAL task from `Ready_For_Execution` to `Executing`.

**Auth:** Task owner or `DEVOPS_ADMIN`

**Request Body:** None

**Validation**
- Task must be `MANUAL`
- Task must be in `Ready_For_Execution`

**Behavior**
- Marks the task as `Executing`
- Does not create a result record yet
- Allows operators to begin a manual step without editing input first

### POST /tasks/{id}/submit-auto

Submits an AUTO task to Jenkins or Ansible.

**Auth:** Task owner or `DEVOPS_ADMIN`

**Request Body:** None

**Behavior**
- Creates a new execution history attempt
- Reads integration config
- Submits to external system
- Stores submission outcome and job URL
- Leaves successful submission in `Executing`
- Marks failed submission as `Failed`

**Errors**

| Status | Code | When |
|--------|------|------|
| 403 | `FORBIDDEN` | Caller lacks permission |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `CONFLICT` | Task is not AUTO or not executable |

---

## Decision

### POST /tasks/{id}/decision

Applies a human decision to a task and triggers progression logic.

**Auth:** Task owner or `DEVOPS_ADMIN`

**Request Body**

```json
{
  "decision": "approve",
  "comment": "optional comment"
}
```

**Supported decisions**

| Decision | Valid Starting State | Effect |
|----------|----------------------|--------|
| `approve` | `Awaiting_Review` | Marks task approved and promotes next eligible task |
| `reject` | `Awaiting_Review` | Marks task rejected and propagates rejected state |
| `rerun` | `Rejected` or `Failed` | Returns task to `Ready_For_Execution`; a later explicit start creates the next attempt |
| `skip` | `Pending` or `Ready_For_Execution` | Skips task and promotes next eligible task |

**Errors**

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Missing or malformed decision |
| 403 | `FORBIDDEN` | Caller lacks permission |
| 404 | `NOT_FOUND` | Task not found |
| 409 | `INVALID_STATE_TRANSITION` | Task not in a valid state for the requested decision |

---

## Configuration

### GET /config

Returns configuration items for Deployment Agent integrations.

### POST /config

Creates or updates a configuration item.

**Auth:** `DEVOPS_ADMIN`

**Request Body**

```json
{
  "key": "jenkins_url",
  "value": "https://jenkins.example.com",
  "description": "Jenkins server base URL"
}
```

**Supported keys**

| Key | Validation |
|-----|------------|
| `jenkins_url` | Must match `^https?://.+` |
| `jenkins_user` | Must not be blank |
| `jenkins_api_token` | Must not be blank |
| `ansible_url` | Must match `^https?://.+` |
| `ansible_user` | Must not be blank |
| `ansible_api_token` | Must not be blank |

**Design note**
- `execution_callback_endpoint` is not part of the current design baseline

---

## Audit

### GET /audit-logs

Returns audit log entries for signed-in users.

**Auth:** Any authenticated session, filtered by scoped visibility unless the user is a global `DEVOPS_ADMIN`

**Query Parameters**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `operatorId` | String | — | Filter by operator |
| `actionType` | String | — | Filter by audit action |
| `releaseFlowId` | String | — | Filter by flow |
| `taskId` | String | — | Filter by task |
| `application` | String | — | Filter by application scope |
| `snowGroup` | String | — | Filter by SNOW group scope |
| `agent` | String | — | Filter by agent |
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |

**Audit action coverage**
- workflow actions such as upload, edit, manual result, auto submit, approve/reject/rerun/skip
- rundown lifecycle actions such as archive, restore, purge
- config updates
- access-grant actions

---

## State Reference

### Task Status

```text
Pending -> Ready_For_Execution -> Executing -> Awaiting_Review -> Approved
Pending -> Skipped
Ready_For_Execution -> Skipped
Executing -> Failed
Awaiting_Review -> Rejected
Rejected -> Ready_For_Execution
Failed -> Ready_For_Execution
```

### Request Status

```text
Pending -> Running -> Completed | Failed | Skipped | Rejected
```

### Flow Status

```text
Pending -> Running -> Completed | Failed | Rejected
```

### Access Grant Status

```text
ACTIVE <-> SUSPENDED
```

---

## Concurrency

Task, Request, and Release Flow entities use optimistic locking. Concurrent mutation should return `409 OPTIMISTIC_LOCK_CONFLICT`, and the client should reload before retrying.

Access Grant mutation follows the same optimistic-update discipline through its `version` column.

---

## Integration Dependencies

| Dependency | Required Config Keys | Protocol | Timeout |
|------------|----------------------|----------|---------|
| Jenkins | `jenkins_url`, `jenkins_user`, `jenkins_api_token` | REST + Basic Auth | 10s connect / 30s read |
| Ansible Tower | `ansible_url`, `ansible_user`, `ansible_api_token` | REST + Bearer Token | 10s connect / 30s read |
| Authentication provider (`TeamBookAuthenticationProvider`) | — (stub in current baseline; Team Book adapter optional later) | Provider interface | — |

---

## Design Notes for Implementation Planning

- Access Management APIs should be implemented together with session contract changes, route guards, and audit action expansion.
- Existing workflow APIs should not be widened to “superuser bypass” semantics without an explicit admin-override design.
- Archive/restore/purge behavior should remain separate from Access Management; they solve different product problems.
