# Atlas CLI Platform Integration Implementation Tasks

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Complete and verified
**Design:** `docs/05-design/atlas-cli-platform-integration-design.md`
**API:** `docs/05-design/contracts/atlas-cli-platform-integration-API_IMPLEMENTATION_GUIDE.md`

## Delivery Order

`ACI-T00` → `T10` → `T20` → `T30` → (`T40` + `T50`) → `T60` → `T70` → `T80` → `T90`.
Tests are written before implementation inside each vertical slice.

## Tasks

### ACI-T00 — Close The SDD Gate

- [x] Pin adjacent Atlas CLI contract files and repository revision.
- [x] Record accepted change proposal and freshness evidence.
- [x] Create requirements, stories, spec, architecture, data flow/model, design, API guide, tasks, ADR-0011,
  traceability, and execution manifest.
- [x] Validate execution manifest and update artifact version pins after generation.
- **Traces:** all requirements; ADR-0011.

### ACI-T10 — Add Failing Lifecycle And Boundary Tests

- [x] State-machine cancel and rerun-without-attempt tests.
- [x] Integration execution start/terminal/stale-fence/concurrency tests.
- [x] Integration error-envelope, correlation, and authorization-isolation tests.
- [x] ArchUnit Platform/Agent/runtime boundary rules.
- **Traces:** ACI-REQ-003-016, 031-035; ACI-NFR-002, 006, 008, 010.

### ACI-T20 — Migrate And Extend Task/Execution Persistence

- [x] Add `V21__add_atlas_integration_platform.sql` and synchronize Oracle current schema.
- [x] Add Task active fence, stable assignee, capability/repository binding, and created time.
- [x] Add Execution identity/client/capability/project/outcome/trace snapshots and optimistic version.
- [x] Add platform Event, Artifact, input approval, Review, and Idempotency entities/repositories.
- [x] Verify constraints/indexes and H2 contract; add an environment-gated Oracle test that requires a real Flyway V20 schema and proves exactly V21 executes.
- **Traces:** ACI-REQ-003, 004, 015, 017, 022, 026; ACI-NFR-002-004.

### ACI-T30 — Implement Identity, Authorization, Errors, And Idempotency

- [x] Add digest-only credential registry and path-scoped bearer filter; preserve session Web auth.
- [x] Add composite scope/Agent/ownership/review/telemetry authorizer.
- [x] Add safe Integration success/error envelopes/advice.
- [x] Add canonical fingerprint and persistent command replay/conflict handling with hashed keys/stale reclaim.
- [x] Prove replay reauthorization, Task-lock/latest-attempt atomic fencing, and absence of raw token/key storage/logging.
- [x] Isolate the synthetic Guest with a server-owned session marker; reject/fail closed all Access Grant `GUEST` roles.
- [x] Add trusted-remote invalid-bearer failure throttling with valid-credential bypass, exact-credential leak guards, and
  secret-safe correlation/binding rejection.
- **Traces:** ACI-REQ-011-014, 031-034; ACI-NFR-001, 005.

### ACI-T40 — Implement Execution Lifecycle And Events

- [x] Lock Task; allocate exactly one active monotonically numbered attempt.
- [x] Implement Task list/detail, Execution start/get/keyset history, and progress events.
- [x] Implement submit/fail/cancel with active fencing and server-derived duration/count.
- [x] Positively validate and preserve accepted progress/summary/failure/cancellation prose; reject unsafe text atomically.
- [x] Append Execution Events and allowlisted existing Audit rows atomically.
- [x] Reconcile manual/auto/monitor lifecycle paths so late attempts cannot transition Task.
- [x] Change rerun to Ready only and make it human-only.
- **Traces:** ACI-REQ-001-010, 014-016.

### ACI-T50 — Implement Artifact Platform Capability

- [x] Write upload/reference/download/retention policy tests.
- [x] Implement bounded BLOB upload and locked/authorized immutable references.
- [x] Implement approved input and produced artifact metadata/content routes.
- [x] Enforce digest, size, quotas, basename/path, role/kind/media/signature/scanner/DLP/source policy, pre-parse exact-client transfer admission, exact-Execution serialization, fence, and safe headers.
- [x] Prove no arbitrary URL/archive/source-tree/raw content in Web projections; implement expiry/renewal/legal hold.
- [x] Add format-specific plain/Markdown/recursive-JSON source gates and globally ordered Artifact/source locks.
- [x] Replace JSON object-tree materialization with one streaming pass and independent byte/token/depth budgets.
- [x] Bound retention cleanup to a configurable ID-ordered transaction batch with under-lock eligibility recheck.
- **Traces:** ACI-REQ-017-021; ACI-NFR-001, 004, 009.

### ACI-T60 — Implement Exact-Attempt Review

- [x] Add review read/write integration tests and duplicate/stale/permission matrix.
- [x] Persist one exact Execution Review Decision and apply existing legal progression.
- [x] Separate technical outcome from review outcome and rerun action.
- [x] Preserve accepted safe review rationale, reject unsafe text, and lock all Request Tasks in stable order before progression.
- **Traces:** ACI-REQ-022-025.

### ACI-T70 — Implement Capability Usage Aggregation

- [x] Seed integration tests covering every metric/client/filter/version case.
- [x] Implement scoped database aggregations over Execution snapshots.
- [x] Expose telemetry endpoint and safe response.
- [x] Verify team=`snowGroup`, project, Agent, UTC date, client, Skill ID/version semantics.
- **Traces:** ACI-REQ-026-030; ACI-NFR-004.

### ACI-T80 — Build Platform Execution Center

- [x] Add independent Integration TypeScript types, API, store, and visibility-aware polling.
- [x] Register one Platform route/navigation capability.
- [x] Implement Tasks, Awaiting Review, history, artifact metadata/download, review, failure/pending-sync views.
- [x] Implement usage cards/table/filter/version distribution.
- [x] Add frontend tests prohibiting sensitive/raw renderers and arbitrary artifact URLs.
- [x] Capture before/after screenshots and run responsive smoke.
- **Traces:** ACI-REQ-036-041; ACI-NFR-009.

### ACI-T90 — Review, Verify, And Close

- [x] Run code-against-design, docs-against-code, doc-quality, and security reviews; address findings.
- [x] Run `mvn test`.
- [x] Run architecture boundary test selection.
- [x] Run frontend tests and `npm run build`.
- [x] Review final diff for secrets, source/raw log exposure, and unexpected Agent dependencies.
- [x] Update CHANGELOG, traceability, task statuses, and change review/archive. Update `AGENT_HANDOFF.md` last.
- **Traces:** all ACI acceptance criteria.

## Definition Of Done

The slice is done only when every requirement maps to implemented code and a passing verification, Atlas is
the sole lifecycle authority, one active Execution is proven under concurrency, stale/idempotent behavior is
proven, Artifact/Review/Telemetry are platform-owned, Execution Center is safe and polling, architecture tests
pass, and no LangGraph/server Skill runtime exists.
