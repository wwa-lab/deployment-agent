# Atlas CLI Platform Integration API Implementation Guide

**Base path:** `/api/v1/integration`
**Version:** v1 additive implementation
**Source baseline:** adjacent Atlas CLI OpenAPI pinned by the execution manifest
**Behavior source:** `docs/03-spec/atlas-cli-platform-integration-spec.md`

## 1. Shared HTTP Contract

### Headers

| Header | Reads | Writes | Rule |
|---|---|---|---|
| `Authorization: Bearer …` | CLI | CLI | path-scoped digest credential; never logged |
| Session cookie | Web | Web review/read | existing HttpOnly session |
| `X-Correlation-Id` | optional | optional | 1-64 safe characters; server generates/echoes otherwise; an exact presented bearer match is replaced before any use |
| `Idempotency-Key` | no | required | 16-128 printable non-whitespace ASCII |

### Success

```json
{"success":true,"data":{}}
```

Paged list:

```json
{"success":true,"data":[],"meta":{"nextCursor":null,"hasMore":false}}
```

### Error

```json
{
  "success": false,
  "error": {
    "code": "STALE_EXECUTION",
    "message": "The execution is no longer active.",
    "retryable": false,
    "requestId": "correlation-id",
    "details": []
  }
}
```

No stack trace, SQL, path, token, repository URL, source, raw log, prompt, or environment value appears.

## 2. Safe Schemas

### Task

Required projection: `taskId`, `workItemId`, `title`, public `status`, `assignee`, project ID/name, team,
`agentModuleId`, capability type/id/version, repository ID/provider/canonical URL/branch/commit, `latestExecutionId`,
`activeExecutionId`, server timestamps, and authorized `actions` booleans.

Every emitted `UserReference.userId` and every `assigneeUserId` command value must satisfy the adjacent
`ResourceID` schema. Reject incompatible authenticated identities at the Integration boundary; do not emit an
invalid session or persisted identifier. Display names are non-authoritative and fall back to the valid ID when
blank, unsafe, or longer than 300 characters.

### Execution

Required projection: `executionId`, `taskId`, `attempt`, public status, actor display/stable ID, client
application/type/version, capability, project/team/Agent, adjacent-contract repository assertion, start/end/duration,
artifact count, bounded safe failure, `pendingSync`, and correlation reference. Exclude legacy input/result logs.

### Artifact

Required projection: `artifactId`, `executionId`, `taskId`, role, kind, sanitized name, media type, size,
lowercase SHA-256, content mode/reference ID, optional validated relative `sourcePath`, and created time. Exclude
bytes and storage locators. The Web view intentionally does not render URL or sourcePath fields.

## 3. Operations

### Task Reads

- `GET /tasks?status=&projectId=&team=&agentModuleId=&limit=&cursor=`
- `GET /tasks/{taskId}`
- `GET /tasks/{taskId}/approved-input-artifacts`
- `GET /tasks/{taskId}/approved-input-artifacts/{artifactId}/content`

List limit defaults to 50 and is capped at 200. Cursor is opaque. `status` is a public Task state filter.
Only integration-ready, authorized Tasks are returned.

### Start And History

- `POST /tasks/{taskId}/executions`

Request fields may include bounded client version and repository branch/commit assertions only where the
server-owned Task binding permits them. User, client type, attempt, time, duration, and status are derived.
Response is 201 on first execution, original status on replay.

- `GET /tasks/{taskId}/executions?limit=&cursor=`
- `GET /executions/{executionId}`

History sorts attempt descending for UI/CLI collection, with stable cursor tie-breaking by ID.

### Platform Provisioning And Rerun

- `PUT /admin/tasks/{taskId}/binding` — human platform admin binds assignee/capability/repository metadata;
  it does not change Request-owned Task readiness. Every rendered binding field is secret-checked before any
  Task mutation or Audit write.
- `POST /admin/tasks/{taskId}/approved-input-artifacts/{artifactId}` — human platform admin approves an
  immutable Artifact from a completed, reviewed source Task; the BLOB receives legal hold.
- `POST /tasks/{taskId}/rerun` — authorized human requests the exact latest failed/rejected Execution ID;
  the Task returns to Ready and no Execution is created.

### Progress

- `POST /executions/{executionId}/progress-events`

```json
{"sequenceNumber":1,"percent":25,"message":"Tests started","clientTimestamp":"2026-08-07T01:00:00Z"}
```

Sequence must be positive; percent optional 0-100; message required, bounded, and limited to positively
validated safe prose. Unsafe secret/source/configuration/raw-log text returns 422 and records no event.
Progress never changes Task/Execution terminal state.

### Artifact Upload

- `POST /executions/{executionId}/artifacts` as multipart `metadata` JSON plus one `content` part.

Metadata contains role, kind, sanitized filename assertion, media type, declared byte length, lowercase SHA-256,
and optional relative source label. First response is 201 metadata. Digest, actual length, role/kind/media,
signature, active fence, ownership, per-Execution/Task quota, malware/DLP readiness, raw-source policy, and
maximum size are revalidated server-side. Uploads and downloads share the configured transfer budget.
Multipart upload admission is acquired in the security chain before Servlet parsing; it is keyed by exact client
identity and held through the response. Scan/persistence additionally uses an exact Execution-key permit.
Plain text, Markdown, and JSON use separate positive evidence policies. Raw-log/stack-trace forms are rejected;
every JSON field name and textual value is inspected, and canonical secret field names are rejected for every
value type. JSON is parsed once as a token stream rather than an object tree, under default 5 MiB, 100,000-token,
and 64-level budgets; text/Markdown use a separate 5 MiB default. The exact presented bearer credential is
rejected from metadata/content before external scanning.

### Artifact Reference

- `POST /executions/{executionId}/artifacts` with `Content-Type: application/json`

```json
{"metadata":{"role":"EVIDENCE","kind":"REPORT","name":"report.txt","mediaType":"text/plain","sizeBytes":12,"digest":{"algorithm":"SHA-256","value":"..."}},"referenceId":"existing-atlas-artifact-id"}
```

No caller-controlled URL field exists. Atlas locks/revalidates the source and renews its content-retention window.

### Artifact Reads

- `GET /executions/{executionId}/artifacts`
- `GET /executions/{executionId}/artifacts/{artifactId}/content`

Download verifies exact provenance and returns `Content-Type`, safe quoted `Content-Disposition`,
RFC 9530 `Content-Digest`, `Cache-Control: private, no-store`, and `X-Content-Type-Options: nosniff`.
Expired unheld BLOB content is unavailable while immutable metadata remains.

### Terminal Commands

- `POST /executions/{executionId}/submit`

Request: optional bounded safe-prose `summary` and declared artifact IDs that must equal authorized persisted artifacts.
Non-manual capability requires at least one output/evidence artifact. Atlas calculates count and duration.

- `POST /executions/{executionId}/fail`

Request: `{failureReason:{code,message,retryable}}`. Do not submit stack traces/logs/source; Atlas rejects
unsafe prose with 422 and persists an accepted message without silently replacing it.

- `POST /executions/{executionId}/cancel`

Request: bounded safe-prose `reason`. Cancellation returns Task to ready; it does not create the next attempt.

### Review

- `GET /executions/{executionId}/review-decision`
- `POST /executions/{executionId}/review-decision`

POST is an additive human-Web operation. Request contains `decision` (`APPROVED`, `REJECTED`, `SKIPPED`) and
optional bounded safe-prose comment. Unsafe rationale returns 422 without a decision. It requires an
authenticated human session and exact latest successful attempt.

### Telemetry

- `GET /telemetry/capability-usage?capabilityId=&skillId=&team=&projectId=&agent=&from=&to=&clientType=`

`skillId` is an alias that applies `capabilityType=SKILL` and `capabilityId=skillId`; conflicting filters are
400. Dates are ISO `yyyy-MM-dd` UTC and inclusive. Results contain invocation/success/failure/cancelled/running
counts, rates, average duration, distinct users, and version distribution.

## 4. Status Codes

| Situation | Status |
|---|---|
| successful read/replay | 200 (or original stored status) |
| resource created | 201 |
| validation/policy/idempotency key syntax | 400 |
| missing/invalid authentication | 401 |
| malformed/invalid bearer attempt budget exhausted | 429 with `Retry-After` |
| same-scope action forbidden | 403 |
| missing/cross-scope/cross-Agent resource | 404 |
| stale/state/active/idempotency/duplicate conflict | 409 |
| artifact too large | 413 |
| unsupported safe media type | 415 |
| semantic artifact/content policy failure | 422 |
| request/transfer capacity exhausted | 429 with `Retry-After` |
| unexpected server failure | 500 with safe request ID |

## 5. Idempotency Checklist Per POST

1. Header parsed before body mutation.
2. Resource and actor reauthorized before replay; Execution-bound replay lookup holds the Task row lock while
   validating `latestExecutionId`, so it is atomic with a concurrent new attempt.
3. Canonical path includes concrete resource ID.
4. Fingerprint contains every behavior-affecting field and actual content digest.
5. Same replay returns byte-equivalent safe JSON and no duplicate event/audit/artifact/review.
6. Different fingerprint returns `IDEMPOTENCY_KEY_REUSED`.
7. In-progress returns retryable `IDEMPOTENCY_REQUEST_IN_PROGRESS`.
8. Raw keys are SHA-256 hashed before persistence; completed replay rows follow resource lifetime.

## 6. Authorization Checklist Per Resource

1. Resolve Task → Request → Release Flow and registered Agent Module.
2. Apply Application/SNOW Group scope.
3. Apply allowed Agent Modules from client credential/session policy.
4. Apply operation permission/role.
5. For writes, match assignee/Execution user and client application or explicit delegation.
6. For reviews, require human session.
7. For artifact reads, match exact Task/Execution/artifact provenance.
8. Use 404 for scope/Agent concealment.
9. Rebuild employee-session roles, permissions, and scopes from the current Access Grant on every request;
   invalidate missing, suspended, historical/mixed `GUEST`, or invalid grants immediately. Only the dedicated
   server-set synthetic Guest marker and an exact single-role Guest identity bypass this lookup.
10. Reject `GUEST` in Integration bearer roles, rotate an existing anonymous/Guest Session ID after employee
    authentication, and reject the exact presented bearer value from client-authored persisted fields and
    correlation. Throttle invalid bearer responses by servlet `remoteAddr`; never trust `X-Forwarded-For` here,
    and never block a valid credential on an exhausted shared-address failure bucket.

## 7. Contract Test Checklist

- all endpoints and envelopes above;
- all five client types, server-derived;
- cursor/limit validation;
- legal transition and stale fence matrix;
- replay/mismatch/in-progress/stale reclaim/replay-after-terminal and old-attempt replay fencing;
- correlation echo/error request ID and exact-presented-credential replacement;
- artifact digest/size/name/path/media/signature/scanner/DLP/source/reference/retention/download headers plus
  text/JSON byte and JSON token/depth budgets;
- repeated invalid bearer throttling with spoofed forwarding headers ignored and a same-address valid credential
  still accepted after the failure bucket is exhausted;
- exact review and rerun without attempt;
- session scope update/suspension using an already-established session;
- multipart rejection before filter-chain body handling and exact-client hash-collision independence;
- Web cache purge on 401/403/404 and no rendering of client-controlled failure/cancellation text;
- aggregation rates, users, average duration, filters, version distribution;
- responses and UI contain no sensitive/raw fields.
