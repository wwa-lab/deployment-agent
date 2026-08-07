# Atlas CLI Platform Integration Specification

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Accepted implementation specification; generated via `user-story-to-spec`
**Requirements:** `docs/01-requirements/atlas-cli-platform-integration-requirement.md`
**Stories:** `docs/02-user-stories/atlas-cli-platform-integration-user-stories.md`

## 1. System Boundary

Atlas Integration is a Platform Core control plane. It persists and serves lifecycle facts but does not
execute source code, Skills, builds, IDE commands, LangGraph graphs, or LLM prompts. Existing Agent Modules
continue to own domain-specific workflow. The Integration surface may identify a registered Agent Module
and enforce its boundary but never imports a concrete module implementation.

## 2. Public State Model

### 2.1 Execution

```text
RUNNING ──submit──> SUCCEEDED
    ├────fail─────> FAILED
    └────cancel───> CANCELLED
```

Terminal Execution rows are immutable. Internal legacy values map as follows:

| Internal | Public |
|---|---|
| `Running` | `RUNNING` |
| `Completed` | `SUCCEEDED` |
| `Failed`, `Timed_Out` | `FAILED` |
| `Cancelled` | `CANCELLED` |

### 2.2 Task

The existing state machine remains authoritative. Integration commands use only these transitions:

| Command | From | To |
|---|---|---|
| start | `Ready_For_Execution` | `Executing` |
| submit | `Executing` | `Awaiting_Review` |
| fail | `Executing` | `Failed` |
| cancel | `Executing` | `Ready_For_Execution` |
| review approve | `Awaiting_Review` | `Approved` |
| review reject | `Awaiting_Review` | `Rejected` |
| review skip | `Awaiting_Review` | `Skipped` |
| rerun | `Rejected` or `Failed` | `Ready_For_Execution` |

No request DTO contains `taskStatus` or `executionStatus` as a desired target. Atlas derives state from
the command endpoint. Rerun creates no Execution.

Public Task status is a transport mapping, not a second state machine:

| Internal | Public |
|---|---|
| `Pending` | `PENDING` |
| `Ready_For_Execution` | `READY_FOR_EXECUTION` |
| `Executing` | `EXECUTING` |
| `Awaiting_Review` | `AWAITING_REVIEW` |
| `Approved` | `APPROVED` |
| `Rejected` | `REJECTED` |
| `Skipped` | `SKIPPED` |
| `Failed` | `FAILED` |

## 3. Integration Readiness

A legacy Task appears in the Integration API only when it has a complete server-owned binding:

- stable `assigneeUserId` or an authorized administrative/service execution policy;
- capability type/id/version;
- project ID from its Release Flow;
- Agent Module from its Request and a registered platform pipeline;
- repository ID/provider plus branch/commit for non-manual execution, or explicitly manual capability.

The server does not invent repository URLs, Skill identifiers, or identities for incomplete legacy rows.

## 4. Command Processing Invariants

Every mutation performs the following logical order:

1. Authenticate and derive principal, client application, client type, and allowed Agent Modules.
2. Parse/validate input and idempotency key.
3. Resolve target and authorize its project/team/Agent scope without disclosing forbidden existence.
4. Look up completed idempotency replay and reauthorize it before returning. Execution-bound replay lookup,
   current authorization, and latest-attempt validation occur in one transaction holding the same Task row lock
   used by start/rerun, so a newer attempt and an old replay have a linear order.
5. Lock Task, then Execution when present.
6. Fence active execution and validate the legal transition.
7. Persist mutation, append-only Execution Event, and completed idempotency record in one transaction.
8. Persist the existing allowlisted audit entry with the same correlation ID in that transaction.

### 4.1 Active-Execution Fence

All progress, artifact, submit, fail, and cancel commands require:

- `Task.activeExecutionId == execution.id`;
- `Task.latestExecutionId == execution.id`;
- Task is `Executing`;
- Execution is `Running`;
- principal/client owns the Execution or holds explicit delegation;
- Task Agent Module remains allowed.

Failure returns `STALE_EXECUTION` for an old attempt regardless of a later attempt's state.

### 4.2 Attempt Allocation

Start obtains a pessimistic Task lock before computing `max(attemptNumber) + 1`. The database unique
constraint on `(task_id, attempt_number)` and optimistic versions remain secondary protection. There is
no time lease in v1; the active Execution ID is the fencing token.

## 5. Authentication And Authorization

### 5.1 CLI Bearer

Production CLI access uses a bearer credential resolved against a server configuration registry. Each
record contains only a SHA-256 token digest plus stable client application ID, principal user ID/display
name, one of the five client types, permissions, allowed Agent Modules, expiry, and enabled flag. The
presented raw token exists only for request authentication and is never logged or stored.

After fixed-cost credential verification, malformed or invalid bearer responses consume a bounded token keyed
only by the servlet container's `remoteAddr`; caller-controlled forwarding headers do not affect the key.
Repeated failures eventually receive 429. Valid credentials never consume or consult this shared-address
failure bucket and are governed by the authenticated per-client limiter, so proxy/NAT peers cannot deny them
authentication. The exact presented credential is request-thread scoped solely for leak detection. A correlation
value containing it is replaced before echo, MDC, Event, or Audit use.

Session-authenticated Web requests keep the existing HttpOnly session and are derived as `MANUAL`.
Development/test header identity may supply a client type only where the existing header fallback is
explicitly enabled; production never trusts such a header.

Every employee Web request reloads the current Access Grant and rebuilds roles, permissions, and scopes.
Missing, suspended, invalid, historical `GUEST`, or mixed `GUEST` employee grants invalidate the session
immediately; changed scopes are applied to the same request rather than waiting for a new login. Guest bypass
is reserved for the synthetic read-only Guest login and requires a dedicated server-owned session marker plus
an exact single-role `GUEST` identity; an employee Access Grant can never create that marker. A successful
employee login rotates an existing anonymous or synthetic-Guest Session ID before installing employee context.

All assignee, Execution-user, and reviewer identifiers exposed as `UserReference.userId` satisfy the
adjacent `ResourceID` grammar (`1..128`, leading alphanumeric, then alphanumeric/`.`/`_`/`:`/`-`). Client
registry startup rejects incompatible configured identities, task binding rejects incompatible assignees,
and the Integration boundary rejects an incompatible authenticated session identity before it can create an
Execution or Review. An unsafe or overlong display name falls back to the validated user ID.

### 5.2 Authorization Matrix

| Operation | Required authority |
|---|---|
| Task/artifact/execution read | Existing scoped access plus allowed Agent Module; owner or read supervisor |
| Start | Task assignee/owner, `DEVOPS_ADMIN`, or explicit run delegation |
| Progress/artifact/terminal | Same recorded user + client application, or explicit execution delegation/admin |
| Review submit | Human session plus Task owner/admin/review permission; bearer applications cannot self-review |
| Telemetry | Management, audit, admin, or telemetry permission, with existing scope still applied |

Cross-Agent and out-of-scope identifiers yield the same resource-specific 404 projection. An authenticated
same-scope user lacking an allowed action receives 403.

## 6. Idempotency

### 6.1 Header And Key Space

- All Integration `POST` endpoints require `Idempotency-Key`.
- Length: 16-128 characters.
- Character set: printable ASCII, excluding whitespace and control characters.
- Unique scope: `principalId + method + canonicalPath + key`.
- Store only `SHA-256(key)`; never persist the caller-provided raw key.
- Replay remains bound to the client application that created the record.

### 6.2 Fingerprint And Outcomes

The fingerprint is SHA-256 of canonical JSON with recursively sorted object keys. Multipart requests use
canonical metadata plus the digest of actual bytes, not the boundary encoding.

| Existing record | Fingerprint | Result |
|---|---|---|
| none | n/a | create `IN_PROGRESS`, execute once, store safe response and mark `COMPLETED` |
| `COMPLETED` | same | return stored status/body/location and `Idempotency-Replayed: true` |
| any | different | 409 `IDEMPOTENCY_KEY_REUSED` |
| `IN_PROGRESS` | same | 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`, retryable |

Binary download responses are never idempotency records. Failed commands roll back their in-progress
record so a corrected retry can execute.

## 7. Artifact Policy

### 7.1 Accepted Content

V1 supports one bounded `content` part per multipart request or an immutable reference to an existing authorized
Atlas artifact. Default maximum is 50 MiB per Artifact, with 100 Artifacts and 500 MiB of logical
Artifact content per Execution; text and JSON evidence each have a smaller 5 MiB default. All limits are
configurable by operations.

Allowed roles are `OUTPUT` and `EVIDENCE`. Allowed kinds/media types are a documented safe allowlist
covering plain text, Markdown, JSON, PDF, PNG, and JPEG evidence. Archives, executable binaries, shell
scripts, and directory/repository uploads are rejected.

Validation requires:

- basename-only safe file name;
- declared and actual byte lengths match;
- exact lowercase SHA-256 digest;
- source path, if supplied, is relative, traversal-free, and metadata-only;
- media type is allowlisted and agrees with lightweight content signature/text validation;
- deployed profiles have a production-ready malware/DLP scanner; local/test use only the explicit built-in gate;
- artifact name and source label reject raw-source extensions; plain text uses a narrow prose gate that also
  rejects raw-log/stack-trace line forms, Markdown uses a formatting-aware prose gate with no fenced source,
  and every JSON object key plus textual value is checked in one streaming pass; secret field names are rejected
  for every JSON value type, with defaults of 100,000 parser tokens and 64 nesting levels and no `JsonNode` tree;
- active Execution fence and authorization.

Unheld uploaded BLOB content expires after 30 days by default while immutable metadata remains. A reference
locks and revalidates its source and renews the content window; submission merges local and referenced source
IDs, globally sorts them, locks each row in that order, and renews from terminal time. Human-approved input sets
legal hold. A scheduled cleanup drains a configurable maximum number of ID-ordered batches, gives each batch
its own transaction, locks in the same order, and rechecks expiry/legal hold/content before clearing only
eligible BLOBs.
Multipart upload obtains that shared permit in the security filter chain before Servlet multipart parsing, then
uses a separate exact per-Execution permit for scan/persistence serialization.

### 7.2 Retrieval

Task/Execution resources contain only artifact count. Artifact list contains safe name, role, kind,
media type, size, digest, and timestamps. Content download is separate and sends:

- a sanitized quoted `Content-Disposition` filename;
- `Cache-Control: private, no-store`;
- `X-Content-Type-Options: nosniff`.

The API may return the validated relative `sourcePath` required by the adjacent CLI contract, but it never
returns absolute paths, signed URLs, arbitrary reference URLs, tokens, or source trees. The Web allowlist does
not render repository URLs or source paths.

## 8. Review Semantics

Review is an immutable one-to-one decision for an Execution. Submission requires the exact latest
`SUCCEEDED` attempt and Task `Awaiting_Review`. The server derives reviewer and time, positively validates the
optional bounded safe-prose comment, stores accepted rationale in the lifecycle transaction, calls the existing `DecisionEngine` for its legal
decision/progression behavior, and
appends event/audit records. A Task rerun is a separate command and does not alter the historical decision.

## 9. Event And Audit Semantics

`ExecutionEvent` is an append-only control-plane ledger, not a log sink. Types are `STARTED`, `PROGRESS`,
`ARTIFACT_REGISTERED`, `SUBMITTED`, `FAILED`, `CANCELLED`, and `REVIEWED`. A client progress sequence is
positive and unique per Execution. Percent is 0-100. Client progress messages and terminal/review prose must
pass the safe-prose policy or the whole command is rejected; accepted values are stored without silent loss.
Event details remain a server-owned allowlist.

Integration mutations use `AuditLoggerService.logAtomic`: lifecycle state, Execution Event, and the matching
allowlisted existing Audit row commit or roll back together. This Integration-specific strict path does not
change the legacy best-effort audit behavior used by other APIs and does not make Audit a state authority.

## 10. Capability Usage Semantics

Facts come from immutable Execution snapshots, not audit rows or artifact content.

| Metric | Definition |
|---|---|
| Invocation count | All started attempts |
| Success count | Public `SUCCEEDED` |
| Failure count | Public `FAILED` |
| Cancelled/running | Separate counts |
| Success rate | `succeeded / (succeeded + failed)`; zero when denominator is zero |
| Failure rate | `failed / (succeeded + failed)`; zero when denominator is zero |
| Average duration | Average server-derived duration for succeeded/failed terminal attempts |
| User count | Distinct stable `userId` |
| Skill ID | `capabilityId` where type is `SKILL` |
| Version distribution | count and percentage per exact capability version within the group |

Team maps to `Request.snowGroup`; project maps to `ReleaseFlow.projectId`; Agent maps to `Request.agent`.
Date filtering uses Execution start time and inclusive UTC calendar dates. Scope filtering occurs before
grouping. Supported client types are `COPILOT`, `OPENCODE`, `KIRO`, `MANUAL`, `PIPELINE`.

## 11. API Surface

The exact request/response schema is in the API implementation guide. Operations are:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/integration/tasks` | scoped executable/reviewable Task list |
| GET | `/api/v1/integration/tasks/{taskId}` | safe Task detail |
| GET | `/api/v1/integration/tasks/{taskId}/approved-input-artifacts` | approved input metadata |
| GET | `/api/v1/integration/tasks/{taskId}/approved-input-artifacts/{artifactId}/content` | approved input content |
| POST | `/api/v1/integration/tasks/{taskId}/executions` | start attempt |
| GET | `/api/v1/integration/tasks/{taskId}/executions` | ordered history (additive) |
| GET | `/api/v1/integration/executions/{executionId}` | execution detail |
| POST | `/api/v1/integration/executions/{executionId}/progress-events` | append progress |
| POST | `/api/v1/integration/executions/{executionId}/artifacts` | upload artifact |
| POST | `/api/v1/integration/executions/{executionId}/artifacts` (`application/json`) | reference Atlas artifact |
| GET | `/api/v1/integration/executions/{executionId}/artifacts` | artifact metadata (additive) |
| GET | `/api/v1/integration/executions/{executionId}/artifacts/{artifactId}/content` | artifact download (additive) |
| POST | `/api/v1/integration/executions/{executionId}/submit` | technical success |
| POST | `/api/v1/integration/executions/{executionId}/fail` | technical failure |
| POST | `/api/v1/integration/executions/{executionId}/cancel` | cancel active attempt |
| GET | `/api/v1/integration/executions/{executionId}/review-decision` | review lookup |
| POST | `/api/v1/integration/executions/{executionId}/review-decision` | human review (additive) |
| GET | `/api/v1/integration/telemetry/capability-usage` | scoped usage aggregation (additive) |

All JSON success responses use `{ "success": true, "data": ... }`; paged responses also carry a cursor
`meta` object. Integration errors use `{ "success": false, "error": { code, message, retryable, requestId,
details } }`. Legacy APIs retain their current envelopes.

## 12. Web Behavior

Execution Center is one Platform route with task operations/`Awaiting Review` and `Usage` views. Polling defaults
to 10 seconds, has an in-flight guard, pauses while `document.hidden`, stops on unmount, preserves the last
successful projection after a transient error, and exposes manual refresh. A 401/403/404 clears cached Task,
workspace, and telemetry projections. Writes generate an idempotency key and then re-fetch; no optimistic state
mutation is permitted.

Only safe DTO fields are rendered. Failure rows show the server-validated stable failure code and retryable flag;
cancellation shows a server-fixed label. Client-controlled failure messages and cancellation reasons are never
rendered by the Web. Artifact content is downloaded by ID as a blob and is never used as a direct external URL.
The Web bundle never stores CLI bearer tokens.

## 13. Error Codes

At minimum: `UNAUTHENTICATED`, `FORBIDDEN`, `TASK_NOT_FOUND`, `EXECUTION_NOT_FOUND`,
`ARTIFACT_NOT_FOUND`, `TASK_NOT_EXECUTABLE`, `EXECUTION_ALREADY_ACTIVE`, `STALE_EXECUTION`,
`INVALID_STATE_TRANSITION`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_INVALID`,
`IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, `ARTIFACT_POLICY_VIOLATION`,
`DIGEST_MISMATCH`, `REVIEW_NOT_AVAILABLE`, `VALIDATION_ERROR`, and `INTERNAL_ERROR`.

## 14. Verification Matrix

| Behavior | Primary verification |
|---|---|
| State transitions/fencing/attempt allocation | unit + concurrent integration tests |
| Idempotency replay/conflict | controller integration tests |
| Authorization/Agent isolation | security integration + ArchUnit |
| Artifact policy/download headers | multipart integration tests |
| Review exact-attempt behavior | integration tests and rerun regression test |
| Telemetry calculations/filters | repository/service integration tests |
| Correlation/event/audit | integration tests |
| UI polling/redaction/navigation | frontend Node tests + build + screenshot smoke |
| Oracle/H2 schema | migration text/compatibility test + greenfield DDL sync + environment-gated V20-to-V21 Oracle Flyway execution |
| No LangGraph/server Skill runtime | dependency and architecture tests |
