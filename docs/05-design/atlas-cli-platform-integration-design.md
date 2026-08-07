# Atlas CLI Platform Integration Detailed Design

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Generated via `architecture-to-design`; accepted for implementation
**Architecture:** `docs/04-architecture/atlas-cli-platform-integration-architecture.md`

## 1. Delivery Shape

The implementation is one Platform Core vertical slice. Existing aggregates remain under `domain.task`;
new application/persistence policy lives under `platform.domain.integration`; safe transport contracts live
under `contracts.integration`; routes live under `platform.web.shared.integration`.

```text
src/main/java/com/wwa/agenthub/
  contracts/dto/integration/             # exact adjacent CLI transport records
  domain/task/                         # reused Task + Execution attempt
  platform/domain/integration/
    auth/ artifact/ event/ idempotency/ lifecycle/ review/ telemetry/
  platform/web/security/               # bearer identity
  platform/web/shared/integration/     # v1 controllers + advice
```

## 2. Contract Types

### 2.1 Enums

- `CapabilityType`: `SKILL`, `SCRIPT`, `PIPELINE`, `MANUAL`
- `IntegrationClientType`: `COPILOT`, `OPENCODE`, `KIRO`, `MANUAL`, `PIPELINE`
- `IntegrationExecutionStatus`: `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`
- `ArtifactRole`: `OUTPUT`, `EVIDENCE`
- `ArtifactKind`: adjacent-contract string values (including report/log/patch forms), policy-validated
- `ArtifactStorageMode`: `UPLOAD`, `REFERENCE`
- `ExecutionEventType`: lifecycle event allowlist
- `IntegrationReviewDecisionType`: `APPROVED`, `REJECTED`, `SKIPPED`

### 2.2 Safe Response Records

- `IntegrationTaskResponse`: identity/title/status/assignee, WorkItem/project/team/Agent, capability,
  adjacent-contract repository assertion including canonical server-bound URL, latest/active IDs, timestamps, `actions`.
- `IntegrationExecutionResponse`: identity/attempt/public status, actor/client/capability/project snapshots,
  start/end/duration/artifact count, safe failure, `pendingSync`, correlation reference.
- `IntegrationArtifactResponse`: identity/name/role/kind/media/size/digest/content mode/reference ID,
  validated relative source label, and time; never bytes or a server storage locator.
- `IntegrationReviewResponse`: exact execution/task, decision/reviewer display/time/comment.
- `CapabilityUsageResponse`: applied filters, totals, grouped metrics, version distribution.

No Integration DTO reuses legacy `TaskDto` or `TaskExecutionHistoryDto`, because those expose raw inputs/logs.

### 2.3 Envelopes

`IntegrationSuccess<T>(boolean success, T data)` and cursor variant. `IntegrationErrorEnvelope` carries a
stable error object. An integration-scoped advice maps validation, auth, not-found, conflict, and unexpected
exceptions without changing legacy API behavior.

## 3. Task And Execution Entity Changes

`Task` receives `activeExecutionId`, `assigneeUserId`, capability and repository binding, and `createdAt`.
The setters remain entity-level persistence access; all Integration transitions occur through
`ExecutionLifecycleService`, which calls `TaskStateMachine.isValid` before writing status.

`TaskExecutionHistory` receives immutable integration snapshots, outcome/failure fields, correlation,
artifact count, last event time, and `@Version`. It remains the single attempt table. Helper methods map its
legacy status to the public enum and answer whether it is terminal.

`TaskRepository.findByIdForExecutionUpdate` and `TaskExecutionHistoryRepository.findByIdForUpdate` use
`PESSIMISTIC_WRITE`. Read projections use bounded/paged queries and fetch Task → Request → ReleaseFlow context
without artifact bytes.

## 4. Execution Lifecycle Service

### 4.1 Interfaces

```java
ExecutionView start(String taskId, StartExecutionCommand command, IntegrationActor actor);
ExecutionView appendProgress(String executionId, ProgressCommand command, IntegrationActor actor);
ExecutionView submit(String executionId, SubmitCommand command, IntegrationActor actor);
ExecutionView fail(String executionId, FailCommand command, IntegrationActor actor);
ExecutionView cancel(String executionId, CancelCommand command, IntegrationActor actor);
```

The actor contains only server-derived principal/client/permission/allowed-agent data. Commands never contain
attempt, status, user, client type, server times, duration, or artifact count.

### 4.2 Start

1. Authorize Task visibility/run.
2. Lock Task; require integration-ready, state Ready, and no active ID.
3. Calculate attempt under lock.
4. Attach only explicitly human-approved Atlas input Artifact IDs; do not copy legacy raw input snapshots.
5. Create `Running` Execution with identity/client/capability/project/repository snapshots.
6. Set Task active/latest IDs and start time; transition through state machine to Executing.
7. Append `STARTED` event with correlation.

### 4.3 Active Mutation Guard

One private method locks Task then Execution and checks provenance, active/latest IDs, Task/Execution state,
Agent/scope, and execution ownership. Every progress/artifact/terminal command calls it.

### 4.4 Terminal

- Submit positively validates optional safe-prose summary, counts persisted artifacts and enforces non-manual
  evidence, maps to `Completed`, sets server end/duration/result summary, transitions Task to Awaiting Review,
  clears active ID, and appends `SUBMITTED`.
- Fail stores positively validated bounded code/message/retryability, maps to `Failed`, transitions Task to Failed, clears active,
  and appends `FAILED`.
- Cancel stores positively validated bounded safe-prose reason, maps to `Cancelled`, transitions Task to Ready, clears active, and appends
  `CANCELLED`.

Late and repeated writes are immutable conflicts unless an authorized idempotent replay resolves first.

## 5. Idempotent Command Service

`IdempotentCommandService.execute(scope, fingerprintSource, responseType, replayGuard, command)` validates the key and
canonical fingerprint. A small deterministic JSON canonicalizer sorts maps/record fields; multipart callers
pass metadata plus computed byte digest.

The service stores only safe JSON command responses and `SHA-256(raw key)`. The unique namespace is principal,
method, canonical path, and key hash; `clientApplicationId` is stored as replay ownership rather than namespace.
A completed matching replay deserializes the stored response and marks the HTTP response with
`Idempotency-Replayed`; another client is forbidden. A mismatch or live in-progress record conflicts, while a
stale in-progress reservation is reclaimed. Completed records remain for their retained resource lifetime.

Initial authorization is invoked by the controller/facade before calling this service. A completed replay runs its
operation-specific `replayGuard` inside the replay lookup transaction. Execution guards acquire the Task
`PESSIMISTIC_WRITE` lock used by start/rerun, reauthorize the actor, and compare `latestExecutionId`, closing the
preauthorization-to-replay race. The mutation and idempotency completion share one transaction. A unique-key race
is translated into a reload and deterministic replay/conflict result.

## 6. Execution Event And Audit

`ExecutionEventService.append(...)` is package-scoped and called only inside lifecycle/artifact/review
transactions. Lifecycle validates client prose with the positive safe-text policy before append; the service
validates progress sequence/percentage/message bounds and persists a server-owned safe details map.

The same transaction calls `AuditLoggerService.logAtomic` with target identifiers and allowlisted metadata.
An audit write failure rolls back the Integration state/event rather than accepting an unaudited mutation.
The payload never includes request bodies, source paths, bytes, raw logs, or credential data. Legacy APIs keep
their separate best-effort audit entry point.

## 7. Artifact Service

### 7.1 Upload

`registerUpload(executionId, metadata, MultipartFile, actor)`:

1. active-mutation guard;
2. enforce one `content` part, request/Execution/Task quotas, configured maximum, actual/declared size;
3. normalize safe basename and optional relative source label;
4. enforce OUTPUT/EVIDENCE and kind/media allowlist;
5. compute and constant-compare SHA-256 before expensive parsing/scanning;
6. reject an exact presented credential before external scanning; enforce media-specific text/JSON byte limits;
   sniff PDF/PNG/JPEG magic or validate UTF-8 text/JSON; use format-specific plain/Markdown and one-pass
   streaming JSON key/value positive evidence gates with token/depth budgets; reject archives/executables/raw
   source/logs/secrets/prompts;
7. require a production-ready malware/DLP scanner outside local/test;
8. persist immutable metadata+BLOB+expiry, update Execution artifact count, append event/audit.

### 7.2 Reference

`registerReference` pessimistically locks an Atlas artifact, verifies read scope/content/provenance, renews
its retention window, and persists a new provenance row pointing to that ID. It never accepts or fetches a URL.

### 7.3 List And Download

Metadata queries never select/serialize content intentionally. Download resolves exact Task/Execution/artifact
relationship, reauthorizes, then returns a dedicated content value object used to set safe headers. Reference
content resolves through the same service and authorization path with cycle rejection.

Upload/download hold one shared node/exact-client transfer permit through persistence or response serialization.
`ArtifactUploadAdmissionFilter` obtains it before Servlet multipart parsing, so rejected bursts do not first spool
the configured body limit to temporary storage. Upload persistence then serializes by exact Execution key.
Reference-counted keyed limiters remove idle keys without hash-stripe collisions. Scheduled retention drains a
configurable maximum number of ID-ordered batches; a separate `REQUIRES_NEW` worker locks/rechecks each batch in
the shared order, clears eligible expired unheld BLOBs, and keeps metadata/purge time. Approved input provisioning is
human/admin-only, locks source and target, and sets legal hold. Terminal submit merges every declared Artifact
and referenced source ID, locks the globally sorted union, rejects unavailable content, and renews its retention window.

## 8. Review Service

`submit(executionId, decision, comment, humanActor)` positively validates optional safe-prose rationale, locks
every Task in the Request by ID and then the exact Execution, requires latest succeeded attempt and awaiting
review, rejects bearer-only review, persists the accepted unique Review Decision, invokes the existing
`DecisionEngine` to apply its legal decision/progression in the same outer transaction, appends `REVIEWED`,
and emits audit. `DecisionEngine` does not delegate back to this service; its rerun branch is changed to
state-only, avoiding a dependency cycle.

Existing agent review routes can remain adapters to the same service. Pre-execution skip is still a Task
decision and creates no exact-attempt Review Decision.

## 9. Authorization And Bearer Design

`IntegrationClientProperties` binds credential descriptors from environment/configuration. Startup
validation rejects raw token fields, invalid hex digests, duplicate application IDs/digests, invalid client
types, unknown Agent Module IDs, missing principal, or expired defaults. No production credential is supplied.

`IntegrationBearerAuthFilter` handles only Integration paths and only when an Authorization bearer header is
present. It builds `IntegrationClientAuthentication`/`IntegrationActor`. Existing session auth remains valid
for Web. Tests/local development may derive MANUAL/explicit test client through the existing safe fallback.
If an Authorization bearer header is present but invalid, it is rejected even when a valid Web session also
exists; callers cannot downgrade a failed bearer attempt into session authority. After digest verification, a
separate bounded token bucket keys only failed responses by `HttpServletRequest.remoteAddr` and ignores
forwarding headers. Valid credentials bypass that bucket and then use the regular authenticated limiter, which
prevents a proxy/NAT peer from blocking their authentication. The request-thread credential guard prevents the
exact bearer value from becoming correlation, lifecycle, or Artifact data; binding fields independently use the
secret-like-value gate.

`SessionAuthFilter` rebuilds every employee Web session identity from the current Access Grant per request.
Role/permission/scope edits apply immediately, while a missing, suspended, historical `GUEST`, mixed `GUEST`,
or invalid grant invalidates the session and continues unauthenticated. Only a dedicated server-set marker plus
an exact single-role synthetic Guest bypasses lookup; Access Grant mutations/bootstrap and Integration bearer
registry reject `GUEST`. Successful employee authentication rotates an existing anonymous/Guest Session ID.
For bearer requests, an exact presented credential is bound only to a request-thread leak guard, checked against
client-authored persisted fields, and cleared after the filter chain; the raw value is never added to an actor.

`IntegrationAuthorizationService` resolves Task relationships and returns a safe authorized aggregate. It
combines `UserContext.hasScopedAccess`, roles/permissions, allowed Agent Modules, stable assignee, and recorded
Execution owner/client. It uses non-disclosing errors for scope/Agent mismatches.

`IntegrationResourceIds` centralizes the adjacent OpenAPI `ResourceID` grammar. Binding DTO validation and
client-registry startup use it directly; `IntegrationActorResolver` rejects incompatible session principals and
normalizes unsafe display names to the validated ID. Projection applies the same check as a final no-invalid-wire
guard for assignee, Execution-user, and reviewer references.

## 10. Telemetry Query Design

`CapabilityUsageService` applies immutable snapshot authorization and capability/Skill/team/project/Agent/
client/UTC-date filters, then uses database aggregate and version-distribution projections. Filters are
validated, `from <= to`, and the range is capped. Artifact BLOBs and legacy input/result-log content are not
selected for this path.

## 11. Web Design

Files:

- `frontend/src/platform/integration/types.ts`
- `frontend/src/api/atlasIntegration.ts`
- `frontend/src/stores/atlasIntegration.ts`
- `frontend/src/platform/composables/useVisiblePolling.ts`
- `frontend/src/views/AtlasExecutionCenterView.vue` and CSS
- focused task/history/artifact/review/usage components as size requires
- `frontend/tests/atlasExecutionCenter.test.mjs`

The view has task operations/Awaiting Review and Usage panels. It renders stable failure code/retryability and
artifact metadata while intentionally omitting client-authored failure/cancellation prose, repository URL/source path,
downloads blobs by resource ID, displays stable error/message/request ID, and never reuses raw-log activity
components. Polling is visibility-aware and non-overlapping. Review and artifact actions have fresh
idempotency keys and always refresh from Atlas.

## 12. Legacy Reconciliation

1. Add Task cancel transition and test it.
2. Change rerun to Ready only and update its regression test.
3. Manual start must create the actual running attempt; manual result finalizes it.
4. Auto submission/monitor paths must eventually call the same active fence and terminal service rather than
   assigning status. The slice is not complete while a late old attempt can complete the current Task.
5. Legacy response DTOs may retain raw fields for existing screens, but the new Integration/Web surface never
   consumes them; sensitive legacy renderers are explicitly excluded from Execution Center.

## 13. Test Design

Write failing tests before each behavior:

- pure state/validator/canonicalizer/metric tests;
- MockMvc Integration API envelope, auth, idempotency, correlation, and artifact tests;
- transaction/concurrency tests with independent threads for start fencing and archive/start exclusion;
- exact-attempt review and rerun regression tests;
- scoped aggregation integration tests for every client type/filter;
- V21 migration/greenfield schema contract test plus environment-gated Oracle Flyway execution that first asserts
  the disposable schema is genuinely at V20, then asserts exactly V21 executes;
- ArchUnit Platform/Agent/runtime rules;
- frontend static behavior/redaction/polling/navigation test plus build.

## 14. Operational Defaults

| Setting | Default |
|---|---|
| Artifact maximum | 50 MiB each; 100/500 MiB per Execution; 500/1 GiB per Task |
| Structured Artifact limits | 5 MiB text; 5 MiB JSON; 100,000 JSON tokens; 64 nesting levels |
| Artifact transfer | 2 combined upload/download transfers per node, 1 per client |
| Artifact retention | 30 days unheld; renewed by reference/submit; approved input legal hold; 200-row ordered cleanup batch × at most 20 batches/run (both configurable; caps 1,000 × 100) |
| Progress events/message | 1,000 events per Execution; 2,000 characters each |
| Attempts | 10 per Task |
| Failure/review comment | 2,000 characters |
| Poll interval | 10 seconds while visible |
| Telemetry date range | 90 days when omitted; explicit range capped by configuration |
| Idempotency | 30-minute in-progress timeout; completed replay retained for resource lifetime |
| Request rate | token bucket capacity 120, refill 2 requests/second per client/identity |
| Invalid bearer responses | trusted-remote bucket capacity 30, refill 0.5/second; valid credentials bypass it |
| Production credentials | none; fail closed until digest descriptors are configured |
| Production Artifact scanner | required; application startup fails without a production-ready scanner bean |
