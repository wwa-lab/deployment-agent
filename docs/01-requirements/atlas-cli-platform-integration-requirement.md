# Atlas CLI Platform Integration Requirements

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Accepted for implementation by direct user request; generated via `req-to-user-story` chain grounding
**Owner:** Atlas Platform Core
**Language:** English-only (ADR-0009)

## 1. Slice Contract

| Field | Value |
|---|---|
| Goal | Make Atlas the authoritative, agent-neutral control plane for CLI task execution, artifacts, review, audit, and capability usage telemetry |
| In scope | Versioned Integration API; execution lifecycle; artifact metadata/upload/download; idempotency; correlation; execution events; task lease/fencing; review submission; usage aggregation; scoped authorization; Platform Execution Center UI; Oracle migration; tests and traceability |
| Out of scope | Running local skills, source code, builds, or LLM workflows on Atlas Server; LangGraph; arbitrary client-side task-state writes; unbounded repository uploads; Build Agent-specific orchestration; replacing existing Agent Modules |
| Primary external contract | Adjacent `atlas-cli` `docs/api-contract.md`, `docs/openapi/atlas-execution-api.yaml`, and `docs/architecture-decisions.md`, pinned in the execution manifest |
| Platform authority | Atlas owns Task and Execution state; clients submit commands and observations only |
| Acceptance | Requirements in sections 5-8 and the traceability matrix in the slice spec |

## 2. Context

Atlas CLI executes approved work locally through tools such as Copilot, OpenCode, and Kiro. The
server must coordinate that work without becoming a coding runtime. The current Atlas codebase already
contains a shared `TaskStateMachine`, execution history, audit, access controls, artifacts, and explicit
Agent Module boundaries. This slice extends those seams as Platform Core capabilities.

The adjacent OpenAPI contract defines the CLI protocol baseline but does not yet expose all control-plane
queries needed by the requested Web experience. The slice therefore keeps the existing `/api/v1/integration`
surface and adds compatible collection, review-write, download, and telemetry endpoints. The five required
client types supersede the older illustrative `ATLAS_CLI`, `WEB`, and `SERVICE` values.

## 3. Governing Principles

1. Platform Core may identify and authorize an Agent Module but must not import or branch on a concrete
   Build, Testing, or Deployment Agent implementation.
2. A Task is durable work intent; an Execution is one numbered attempt. One Task may have many attempts,
   but at most one active attempt.
3. Only Atlas performs Task and Execution transitions. A client may request a legal command but cannot
   set an arbitrary status.
4. Technical execution outcome is distinct from human review outcome.
5. Integration responses are least-disclosure projections. Tokens, prompts, environment dumps, complete
   source, repository archives, and raw execution logs are not returned by these APIs or rendered by the UI.

## 4. Actors And Trust Boundaries

| Actor | Trust and permitted behavior |
|---|---|
| CLI credential | Authenticated server-side; fixed principal, client type, and allowed Agent Module scope; may operate only its own authorized active executions |
| Web user | Existing HttpOnly session; may read within existing access scope and review only with server-authorized permission |
| Atlas Platform Core | Owns state transition, attempt allocation, fencing, idempotency, audit, artifact policy, and aggregation |
| Agent Module | Owns domain workflow and supplies task context; does not own Integration API lifecycle rules |
| Local execution tool | Runs outside Atlas Server and reports bounded progress/artifacts/results through Atlas CLI |

## 5. Functional Requirements

### 5.1 Task And Execution Lifecycle

| ID | Requirement |
|---|---|
| **ACI-REQ-001** | Expose an agent-neutral Integration API under `/api/v1/integration`; controllers belong to Platform Core and must not import a concrete Agent Module. |
| **ACI-REQ-002** | List and retrieve only Tasks visible to the authenticated principal, with stable project, team, Agent Module, capability, latest execution, and review-safe metadata. |
| **ACI-REQ-003** | Start an Execution only from a legally executable Task state, atomically allocate the next attempt, set the Task to `EXECUTING`, and record the active execution identifier. |
| **ACI-REQ-004** | Enforce at most one `RUNNING` Execution per Task through database locking, optimistic versioning, an active-execution fence, and a unique Task-attempt constraint. |
| **ACI-REQ-005** | Accept progress events only for the authorized active `RUNNING` Execution while its Task remains `EXECUTING`; reject stale or cross-task writes. |
| **ACI-REQ-006** | Submit technical success only through a legal transition. Non-manual executions require at least one accepted output/evidence artifact; manual executions may submit a bounded safe-prose summary without an artifact. Unsafe source, configuration, raw-log, or secret-bearing text is rejected rather than silently discarded. |
| **ACI-REQ-007** | Fail or cancel only the active execution. Failure records a stable safe code/message and moves the Task to `FAILED`; cancellation records a safe-prose reason and returns the Task to `READY_FOR_EXECUTION`. Unsafe client prose is rejected with no lifecycle mutation. |
| **ACI-REQ-008** | Map existing internal execution values to the public lifecycle `RUNNING`, `SUCCEEDED`, `FAILED`, and `CANCELLED` without breaking legacy execution-history consumers. |
| **ACI-REQ-009** | Rerun changes a rejected or failed Task back to `READY_FOR_EXECUTION` only. It must not create an Execution until a client successfully starts one. |
| **ACI-REQ-010** | Return ordered Execution history for a Task, including attempt, status, capability/client identity, safe failure details, timing, artifact count, and server-derived sync status. |

### 5.2 Idempotency, Correlation, And Audit

| ID | Requirement |
|---|---|
| **ACI-REQ-011** | Every mutating Integration API operation requires `Idempotency-Key`: 16-128 printable ASCII characters with no whitespace. |
| **ACI-REQ-012** | Scope idempotency to authenticated principal, HTTP method, normalized path, and key. Replay the original status and response only when the canonical request fingerprint matches; otherwise return 409. |
| **ACI-REQ-013** | Reauthorize the principal and resource before replaying an idempotent response. For Execution-bound commands, replay reauthorization and latest-attempt fencing share the same Task row lock and transaction as replay lookup. An in-progress matching request returns a retryable conflict rather than executing twice. |
| **ACI-REQ-014** | Reuse `X-Correlation-Id`; validate or generate it at the HTTP boundary, echo it in the response, and persist it with execution events and audit entries. If it contains the exact presented bearer credential, replace it before response echo, MDC, event, or Audit use. |
| **ACI-REQ-015** | Append a platform execution event for start, progress, artifact registration, submit, fail, cancel, and review. The lifecycle mutation and event must commit atomically. |
| **ACI-REQ-016** | Persist the existing Audit record atomically with every accepted Integration lifecycle mutation and its Execution Event, using only allowlisted context and the same correlation ID. |

### 5.3 Artifact Handling

| ID | Requirement |
|---|---|
| **ACI-REQ-017** | Store immutable artifact metadata and bounded content for `OUTPUT` and `EVIDENCE`; preserve task, execution, actor, digest, size, kind, and creation provenance. |
| **ACI-REQ-018** | Support multipart upload and authorized reference to an existing Atlas artifact. Do not accept an arbitrary remote URL as artifact content. |
| **ACI-REQ-019** | Validate artifact name, kind, media type, declared size, SHA-256 digest, optional relative source path, configurable maximum size, malware/DLP readiness, and raw-source policy; reject traversal, control characters, unsafe archives, executables/scripts, credentials, prompts, raw logs, and full source. Apply format-specific positive checks to plain text, Markdown, and every JSON field name/textual value so JSON wrapping or secret-named non-text values cannot bypass controls while normal prose Markdown remains usable. Enforce smaller independent text/JSON byte limits and stream JSON under explicit token and nesting budgets rather than materializing an object tree. |
| **ACI-REQ-020** | List artifact metadata separately from content. Download content by Atlas identifiers with safe `Content-Disposition`, `nosniff`, private/no-store caching, and authorization revalidation. |
| **ACI-REQ-021** | Never return a repository archive or full source tree. Retain immutable metadata, expire unheld BLOB content after the configured period in configurable ID-ordered transaction batches, preserve approved inputs under legal hold, and accept only a documented allowlist of bounded evidence/output kinds and media types. Cleanup and submission recheck locked rows under one global Artifact-ID order. |

### 5.4 Review

| ID | Requirement |
|---|---|
| **ACI-REQ-022** | Submit a review decision against the exact latest `SUCCEEDED` Execution while the Task is `AWAITING_REVIEW`; store reviewer, timestamp, decision, and bounded safe-prose comment. Reject unsafe rationale atomically rather than accepting and dropping it. |
| **ACI-REQ-023** | Allow only `APPROVED`, `REJECTED`, or `SKIPPED` review outcomes and delegate Task transition/progression to existing state-machine and decision services. |
| **ACI-REQ-024** | Replaying a review with the same idempotency key returns the original result; conflicting, stale, unauthorized, or duplicate review submissions are rejected. |
| **ACI-REQ-025** | Expose the current review decision as a separate resource so technical success and review acceptance are never conflated. |

### 5.5 Capability And Skill Telemetry

| ID | Requirement |
|---|---|
| **ACI-REQ-026** | Persist exact capability identity per Execution: `capabilityType`, `capabilityId`, and `capabilityVersion`. For `SKILL`, `skillId` is the same stable identifier as `capabilityId`, not a second identity. |
| **ACI-REQ-027** | Server-derive and persist one client type from `COPILOT`, `OPENCODE`, `KIRO`, `MANUAL`, or `PIPELINE`; never trust a production caller to self-assert a different type. |
| **ACI-REQ-028** | Aggregate invocation count, technical success count/rate, failure count/rate, average terminal duration, distinct user count, cancellation/running counts, and Skill version distribution. |
| **ACI-REQ-029** | Filter aggregation by team, project, Agent Module, inclusive UTC date range, and client type, applying authorization scope before aggregation. |
| **ACI-REQ-030** | Compute success and failure rates over technically decided attempts only (`SUCCEEDED` plus `FAILED`); expose cancelled and running separately. |

### 5.6 Authorization And Agent Boundary

| ID | Requirement |
|---|---|
| **ACI-REQ-031** | Bind CLI bearer credentials to a stable principal, client type, permissions, and allowed Agent Modules using server configuration that stores only token digests. Raw tokens must not be logged or persisted. Reject `GUEST` bearer roles, bind the exact presented value only to a request-thread leak guard, and throttle malformed/invalid bearer responses by the trusted servlet remote address without trusting forwarding headers. Valid credentials must bypass an exhausted shared-address failure bucket. |
| **ACI-REQ-032** | Reuse existing session identity, roles, permission grants, project/team scope, and Agent boundary checks for Web users; rebuild employee session authority from the current Access Grant on every request so scope/role/permission changes and suspension take effect immediately. Only a dedicated server-set session marker may identify the synthetic read-only Guest. `GUEST` is not a valid Access Grant role, historical mixed/Guest employee grants fail closed, and successful employee login rotates any pre-authentication Session ID before storing authority. UI visibility is never an authorization decision. |
| **ACI-REQ-032a** | Every user identifier emitted through an Atlas `UserReference` or accepted as an assignee must satisfy the adjacent API `ResourceID` contract. Reject incompatible authenticated actor IDs before command execution and fall back to the validated ID when a display name is unsafe or too long. Reject secret-like values in every rendered Task binding field before mutating Task state or emitting Audit. |
| **ACI-REQ-033** | A client may mutate only an execution it started unless it has an explicit delegation permission, and every mutation must remain within the credential's allowed Agent Module scope. |
| **ACI-REQ-034** | Return a non-disclosing not-found response for cross-Agent or out-of-scope resource identifiers where revealing existence would leak information. |
| **ACI-REQ-035** | Add architecture tests proving Platform Core has no dependency on Agent Module packages and Integration DTOs/controllers are not duplicated under individual agents. |

### 5.7 Web Execution Center

| ID | Requirement |
|---|---|
| **ACI-REQ-036** | Add one Platform capability and route named Execution Center; do not add a Build, Testing, or Deployment Agent-specific copy. |
| **ACI-REQ-037** | Poll Atlas Task data at a bounded interval, prevent overlapping requests, pause polling when the page is hidden/unmounted, and replace local cache from server responses. |
| **ACI-REQ-038** | Provide Task and Awaiting Review views, selected Task execution history, artifact metadata/download, review submission, structured failure code/retryability, a fixed cancellation label, and server-derived pending-sync status. The Web must not render client-controlled failure/cancellation text. |
| **ACI-REQ-039** | Provide a capability/skill usage dashboard with the metrics and filters in ACI-REQ-026 through ACI-REQ-030. |
| **ACI-REQ-040** | Render only allowlisted projections. The page must not show tokens, raw inputs, prompts, environment variables, complete result logs, repository URLs, source paths, arbitrary external URLs, or full source. |
| **ACI-REQ-041** | After any write, re-fetch Task/Execution from Atlas; the Web client must not optimistically assign Task or Execution state. |

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| **ACI-NFR-001** | Security | Validate every request at the boundary, enforce least privilege, use constant-time digest comparison for credentials, and never include secrets in exceptions or telemetry. |
| **ACI-NFR-002** | Consistency | Lifecycle state, active-execution fence, attempt allocation, counters, and append-only event persist in one transaction. |
| **ACI-NFR-003** | Portability | Schema and JPA mappings work on Oracle production and H2 tests; update Flyway and the greenfield Oracle schema together. |
| **ACI-NFR-004** | Performance | Task/history queries use stable opaque keyset cursors; artifact reads are bounded; aggregation runs in indexed database projections without fetching bytes; per-request, failed-authentication response, per-Task, per-Execution, progress, attempt, structured-data, and shared transfer limits prevent resource exhaustion. Multipart uploads acquire exact client/global admission in the security chain before Servlet body parsing; JSON evidence is validated with a streaming parser and bounded bytes/tokens/depth. |
| **ACI-NFR-005** | Observability | Stable error envelope includes code, safe message, retryability, and request/correlation identifier. |
| **ACI-NFR-006** | Compatibility | Existing Release Flow, external execution monitor, review, and Agent Module APIs continue to pass their tests. |
| **ACI-NFR-007** | Maintainability | Keep lifecycle, artifact, telemetry, auth, and projection responsibilities in focused Platform Core services/files. |
| **ACI-NFR-008** | Testing | Add unit, integration, migration/contract, and ArchUnit coverage; final `mvn test`, frontend tests/build, and architecture boundary tests must pass. |
| **ACI-NFR-009** | Privacy | Store only bounded, positively validated safe prose needed for control-plane evidence; reject secret/source/configuration/raw-log text without mutating state. Source code and arbitrary logs remain outside Atlas. |
| **ACI-NFR-010** | Runtime boundary | Atlas Server must not invoke a local Skill, coding tool, build command, LangGraph, or an LLM as part of this API. |

## 7. Success Criteria

1. Two simultaneous start requests cannot create two active executions or duplicate attempts.
2. Every stale execution write is fenced, every repeated write is safely replayed or conflicted, and every accepted mutation has a correlation-linked event/audit trail.
3. Artifact upload/download honors the allowlist, size/digest rules, scoped authorization, and least-disclosure headers.
4. Review is bound to the exact latest successful attempt and causes only a legal Task transition.
5. Usage results are correct for all five client types and every requested filter/dimension.
6. Execution Center refreshes from Atlas and exposes history, artifacts, review, failures, pending sync, and metrics without sensitive content.
7. ArchUnit proves the new implementation is Platform Core and agent-neutral.
8. Backend tests, frontend tests/build, and architecture boundary tests pass with no LangGraph dependency or server-side Skill runtime.

## 8. Committed Clarifications

| Topic | Decision |
|---|---|
| Web-only endpoint gaps | Extend the Integration API with execution/artifact collections, review submission, and telemetry while preserving existing v1 paths |
| Client type mismatch | Use the five values explicitly required by this slice; record the supersession in the API guide |
| Pending sync | A server-derived projection: active `RUNNING` execution is waiting for the owning client to report a terminal result. Atlas never claims knowledge of unsent local completion |
| Skill identity | `skillId = capabilityId` when `capabilityType = SKILL`; version is separate |
| Technical rate denominator | `SUCCEEDED + FAILED`; cancelled/running are reported separately |
| Credential provisioning | Environment/configuration-backed digest registry in v1; no token administration UI and no raw token persistence |
| Idempotency namespace/storage | Namespace is principal + method + canonical path + key; store only the SHA-256 key digest, bind replay to the owning client, and retain completed rows for the associated resource lifetime |
| Artifact retention/scanning | Unheld content expires after 30 days by default; references renew that window; approved inputs set legal hold; deployed profiles fail startup unless a production malware/DLP scanner is installed |

## 9. Traceability

| Artifact | Path |
|---|---|
| User stories | `docs/02-user-stories/atlas-cli-platform-integration-user-stories.md` |
| Specification | `docs/03-spec/atlas-cli-platform-integration-spec.md` |
| Architecture | `docs/04-architecture/atlas-cli-platform-integration-architecture.md` |
| Data flow | `docs/04-architecture/atlas-cli-platform-integration-data-flow.md` |
| Data model | `docs/04-architecture/atlas-cli-platform-integration-data-model.md` |
| Design | `docs/05-design/atlas-cli-platform-integration-design.md` |
| API guide | `docs/05-design/contracts/atlas-cli-platform-integration-API_IMPLEMENTATION_GUIDE.md` |
| Tasks | `docs/06-tasks/atlas-cli-platform-integration-tasks.md` |
| Traceability matrix | `docs/00-context/atlas-cli-platform-integration-traceability.md` |
| Boundary ADR | `docs/00-context/decisions/ADR-0011-atlas-integration-is-platform-control-plane.md` |
