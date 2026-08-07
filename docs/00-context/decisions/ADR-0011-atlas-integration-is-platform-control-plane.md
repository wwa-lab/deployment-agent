# ADR-0011: Atlas Integration Is A Platform Control Plane

**Status:** Accepted
**Date:** 2026-08-07
**Decision owners:** Atlas Platform Architecture
**Slice:** `atlas-cli-platform-integration`

## Context

Atlas CLI needs Task discovery, execution attempts, artifacts, review, event/audit history, and usage
telemetry across multiple local clients and Agent Modules. The existing Hub already owns the Task aggregate,
Task state machine, execution history, access model, audit, and Agent Module boundary. Implementing this
inside Build Agent would make shared concepts Build-specific; creating parallel Atlas Task/Execution tables
would create two authorities. Allowing the server to run local Skills would also collapse the intended
control-plane/execution-plane separation.

The adjacent Atlas CLI architecture permits a generic integration route only when cross-Agent authorization
and boundary fitness tests are enforced. The user also requires Atlas to be the only source of Task and
Execution state.

## Decision

1. Atlas Integration is Platform Core, exposed under `/api/v1/integration` and implemented under
   agent-neutral platform/domain, contract, and shared-web packages.
2. Existing `DA_TASK` remains the Task source of truth and existing `DA_TASK_EXECUTION_HISTORY` is extended
   as the Execution attempt source of truth. No competing Atlas Task or Execution aggregate is created.
3. `ReleaseFlow`/`Request` supply WorkItem, project, team, and Agent context. Only server-bound, integration-
   ready Tasks are visible; missing legacy metadata is not fabricated.
4. Execution ID is the v1 lease/fencing token. A Task row lock, `activeExecutionId`, optimistic versions,
   and the Task-attempt unique constraint enforce one active attempt.
5. Clients call command endpoints. Only server lifecycle services and the existing `TaskStateMachine`
   choose Task/Execution status.
6. Platform-owned append-only Execution Event, Artifact, Review Decision, and Idempotency records surround
   the reused aggregates. Integration mutations write their allowlisted existing Audit row atomically with
   lifecycle state and event; Audit remains evidence rather than a competing lifecycle authority.
7. Credential configuration derives principal, client type, permission, and allowed Agent Modules from a
   token digest. Client-supplied production identity or client type is never trusted.
8. Usage is aggregated from Execution snapshots after authorization filtering. For Skills, capability ID is
   the Skill ID.
9. Atlas Server never runs a local Skill, IDE command, repository scan, build, LangGraph graph, or LLM.
10. One Platform Execution Center consumes safe projections. It is not duplicated in Agent workspaces and
    does not render raw inputs/logs/source or tokens.

## Alternatives Considered

### Build Agent-owned integration

Rejected. Execution, Artifact, Review, and Telemetry are shared lifecycle concepts and would create reverse
dependencies or duplicated implementations for Testing and Deployment.

### New Atlas Task and Execution tables

Rejected. Parallel aggregates would allow the CLI-facing state to drift from the existing workflow and
violate the sole-source requirement.

### Time-based lease as the first fencing mechanism

Rejected for v1. Active Execution identity is sufficient and simpler. A future server-owned expiry can be
added only with explicit recovery semantics.

### Trust caller-provided client type and user

Rejected. It would make telemetry forgeable and permit cross-client execution writes.

### Run Skills on Atlas Server

Rejected. It increases attack surface, couples Atlas to local tool runtimes, and contradicts the Atlas CLI
architecture. The server coordinates and records; the client executes.

## Consequences

### Positive

- One authoritative lifecycle and monotonic attempt history.
- Shared platform behavior across all present/future Agent Modules.
- Server-derived, auditable identity and reliable telemetry.
- Explicit least-disclosure artifact and Web boundary.
- Existing task, review, audit, access, and module seams remain reusable.

### Negative

- Existing manual/auto/monitor paths must converge on the fenced lifecycle rather than assigning status.
- Legacy Tasks without a complete integration binding remain invisible until provisioned.
- New Oracle tables/indexes and credential provisioning are required.
- Offline local completion cannot be shown until a client reports it; pending sync is necessarily Atlas's
  last-known state.

## Enforcement

- `AtlasPlatformArchitectureTest` prohibits Platform Integration dependencies on `agents..`, `AgentId`,
  server execution adapters, LangGraph, and local Skill runtimes.
- Integration tests cover credential derivation, Access Grant + Agent scope, 404 isolation, ownership,
  idempotency reauthorization, and stale execution fencing.
- Web tests prohibit raw-log/input/source/token rendering and require download-by-ID.
- SDD traceability maps every implementation/test to ACI requirements.

## Reversal Triggers

Reconsider only if Atlas adopts a separately governed multi-control-plane architecture, if execution must
move server-side by an explicit security/operating decision, or if a durable distributed lease with recovery
becomes necessary. Any reversal requires a superseding ADR and migration plan; it cannot occur as an Agent-
specific implementation shortcut.
