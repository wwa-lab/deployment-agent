# Atlas CLI Platform Integration User Stories

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Generated via `req-to-user-story`
**Source:** `docs/01-requirements/atlas-cli-platform-integration-requirement.md`

## ACI-US-01 — Discover Authorized Work

**As** a delivery engineer using an Atlas-supported client,
**I want** Atlas to return only executable Tasks and approved inputs in my scope,
**so that** I can work locally without learning another Agent Module's data or secrets.

### Acceptance Criteria

1. Given an authenticated principal, when it lists or reads Tasks, then Atlas returns only project,
   team, and Agent Module-scoped Tasks it may access.
2. Given an out-of-scope Task identifier, when it is requested, then Atlas responds without disclosing
   whether that Task exists.
3. Given an authorized Task, then the response contains stable Task/capability/repository metadata but
   not tokens, raw prompts, environment values, full source, or raw logs.
4. Given approved input artifacts, then content is downloaded only through a separate authorized endpoint.

**Traces to:** ACI-REQ-001, 002, 021, 031-034, 040.

## ACI-US-02 — Start Exactly One Execution Attempt

**As** an Atlas CLI user,
**I want** starting a Task to atomically lease one numbered Execution,
**so that** retries and competing clients cannot perform the same attempt twice.

### Acceptance Criteria

1. Given a `READY_FOR_EXECUTION` Task, when an authorized start request succeeds, then exactly one
   `RUNNING` Execution is created, its attempt is the previous maximum plus one, and the Task becomes
   `EXECUTING` with that active execution identifier.
2. Given concurrent starts, then one succeeds and all others receive replay or conflict; no attempt
   number or active lease is duplicated.
3. Given the same valid idempotency key and body, then a retry receives the original response.
4. Given the same key and a different body, then Atlas returns 409 and does not create an attempt.
5. Given a rejected or failed Task that is reset for rerun, then no Execution exists until this start succeeds.

**Traces to:** ACI-REQ-003, 004, 009, 011-013.

## ACI-US-03 — Report Progress And Terminal Outcome Safely

**As** an execution client,
**I want** to report bounded progress, success, failure, or cancellation for my active attempt,
**so that** Atlas remains the authoritative lifecycle record.

### Acceptance Criteria

1. Progress is accepted only while the Execution is the Task's active `RUNNING` attempt and the caller
   owns it or has delegation permission.
2. A stale execution, a Task no longer in `EXECUTING`, or a cross-Agent credential is fenced before mutation.
3. Submit, fail, and cancel use the legal state machine and never accept a client-supplied target status.
4. Success moves the Task to `AWAITING_REVIEW`; failure moves it to `FAILED`; cancellation returns it to
   `READY_FOR_EXECUTION` without creating another attempt.
5. An active running attempt projects `pendingSync=true`; a terminal attempt projects false.

**Traces to:** ACI-REQ-005-008, 010, 033, 037, 041.

## ACI-US-04 — Register Bounded Evidence

**As** an execution client,
**I want** to attach immutable output/evidence metadata and bounded content,
**so that** reviewers can inspect proof without Atlas ingesting a repository or unsafe payload.

### Acceptance Criteria

1. Atlas accepts only allowlisted kinds/media types under the configured size limit and verifies SHA-256.
2. Unsafe filenames, paths, archives, scripts, executables, mismatched digests, and arbitrary URLs are rejected.
3. An artifact is bound to the active Task/Execution and receives an Atlas identifier and immutable provenance.
4. A reference request may point only to an existing authorized Atlas artifact.
5. Metadata listing never includes bytes; download reauthorizes and sets safe content headers.

**Traces to:** ACI-REQ-017-021.

## ACI-US-05 — Review The Exact Successful Attempt

**As** an authorized reviewer,
**I want** to approve, reject, or skip the latest successful Execution,
**so that** human governance stays separate from technical completion.

### Acceptance Criteria

1. Review submission names the exact Execution and is accepted only when it is the latest succeeded
   attempt and its Task is `AWAITING_REVIEW`.
2. The server authorizes the reviewer and records reviewer identity, bounded comment, decision, and time.
3. The existing decision/state-machine services perform the legal Task transition and downstream progression.
4. A replay returns the original decision; stale, conflicting, duplicate, or unauthorized review is rejected.
5. Review retrieval returns the separate decision resource and does not rewrite technical outcome.

**Traces to:** ACI-REQ-022-025.

## ACI-US-06 — Audit Every Control-Plane Mutation

**As** an auditor,
**I want** lifecycle commands to have correlation-linked immutable events and audit records,
**so that** I can reconstruct who did what without exposing sensitive execution content.

### Acceptance Criteria

1. Start, progress, artifact, submit, fail, cancel, and review each append an event in the lifecycle transaction.
2. The same transaction persists the existing Audit record; audit failure rolls back the Integration mutation so no accepted command lacks its audit trail.
3. Both records carry the validated/generated correlation ID, principal, target identity, safe action metadata,
   and server receive time.
4. Audit/event details never include bearer tokens, artifact bytes, source, raw prompts, environment, or full logs.

**Traces to:** ACI-REQ-014-016, ACI-NFR-001, 002, 005, 009.

## ACI-US-07 — Compare Capability And Skill Usage

**As** an engineering manager or platform owner,
**I want** scoped usage metrics across capabilities, Skills, clients, teams, projects, agents, and dates,
**so that** I can see adoption and reliability without reading raw executions.

### Acceptance Criteria

1. Results group exact capability type/id/version and expose `skillId=capabilityId` for Skills.
2. Each group contains invocation count, succeeded/failed/cancelled/running counts, technical success/failure
   rates, average terminal duration, distinct user count, and version distribution.
3. Filters for team, project, Agent Module, UTC date range, and all five client types combine correctly.
4. Authorization is applied before aggregation; metrics do not leak out-of-scope counts.
5. Success/failure denominator excludes running and cancelled attempts.

**Traces to:** ACI-REQ-026-030, 032, ACI-NFR-004.

## ACI-US-08 — Operate From The Platform Execution Center

**As** an Atlas Web user,
**I want** one Platform page for Tasks, awaiting reviews, execution history, artifacts, and usage,
**so that** I can observe and govern local execution without entering a concrete Agent workspace.

### Acceptance Criteria

1. One Execution Center entry appears in Platform navigation and Home shared controls.
2. Task data polls at a bounded interval without overlap, pauses while hidden/unmounted, and is always
   replaced from Atlas responses.
3. The page exposes Tasks, Awaiting Review, selected Task history, safe artifact metadata/download,
   safe failure reason, and pending-sync state.
4. Review uses a unique idempotency key and re-fetches state after completion; the browser never assigns status.
5. The usage tab supplies every required filter and metric.
6. The page has no renderer for tokens, raw input, prompts, environment, complete logs, repository URLs,
   source paths, reference URLs, or complete source.

**Traces to:** ACI-REQ-036-041.

## ACI-US-09 — Preserve Existing Agent Modules

**As** an Atlas maintainer,
**I want** the implementation to reuse shared seams without crossing Agent Module boundaries,
**so that** Execution, Artifact, and Telemetry remain durable platform capabilities.

### Acceptance Criteria

1. Platform packages do not import `agents.*`; Integration controllers exist only under Platform Web.
2. Existing TaskStateMachine, execution history, decision, audit, access, and artifact patterns are reused
   or extended without duplicating Build-specific logic.
3. ArchUnit fails if a concrete Agent dependency, Agent-specific Integration controller, or shared status
   duplicate is introduced.
4. Existing backend/frontend behavior remains green after the migration and additions.
5. Dependency inspection confirms no LangGraph and code inspection confirms no Atlas Server Skill runner.

**Traces to:** ACI-REQ-001, 035, ACI-NFR-006-010.

## Story Dependency Order

`ACI-US-01` → `ACI-US-02` → (`ACI-US-03` + `ACI-US-04`) → `ACI-US-05`; `ACI-US-06` is
cross-cutting; `ACI-US-07` consumes execution facts; `ACI-US-08` consumes the read/write APIs; and
`ACI-US-09` is enforced continuously.
