# Implementation Task Breakdown

## Overview

This document breaks the proposed multi-tool external execution design into implementation-ready work. The goal is to evolve Deployment Agent from submission-only AUTO execution into a routed, synchronized execution flow that can show external job/log/approval links and update task state when Jenkins or Ansible jobs finish.

**Delivery objective**

- Route each AUTO task to the correct external tool.
- Preserve the current Deployment Agent task-review model.
- Surface external job, log, and approval context in Task Result and Task Activity.
- Synchronize remote completion back into Deployment Agent so tasks do not remain indefinitely in `Executing`.

**Planning assumptions**

- Jenkins and Ansible remain the only supported external tools in the first implementation pass.
- Polling is the required synchronization path for MVP of this feature; callback ingestion is optional follow-up work.
- Existing task state, release-flow progression, and owner/admin permission rules must remain intact.
- Legacy plain-script AUTO tasks remain backward-compatible through Jenkins fallback unless explicitly migrated.

## Shared Definition of Done

- A task is done when its scoped code and document changes are merge-ready, reviewed, and aligned with the design decisions in `docs/05-design/multi-tool-execution-design.md`.
- Relevant automated coverage is added or updated for the changed behavior.
- Relevant backend, frontend, and type or contract checks pass for the changed area.
- Operational or rollout notes are captured for any task that introduces scheduling, configuration, or migration behavior.
- Must-priority tasks are not done until acceptance-critical review comments are resolved.

## Source Design

**System name:** Deployment Agent

**Design scope summary**

- The source design extends AUTO execution from fire-and-forget submission into multi-tool routing with normalized status synchronization.
- The feature keeps the existing task and review lifecycle, but adds external execution visibility and terminal-state reconciliation.
- Execution-history records remain the primary execution source of truth, with additional normalized external metadata added for UI and monitor behavior.

## Workstreams

### Major Implementation Streams

1. Execution metadata model and API contract
2. Tool target resolution and adapter refactor
3. External status monitor and task-state reconciliation
4. Jenkins and Ansible polling implementations
5. Task edit, result, and activity UX enhancements
6. Audit, observability, and rollout safety
7. Backend and frontend verification

### Recommended Sequencing

1. Extend the execution metadata model and DTO contract.
2. Implement tool resolution and adapter contract changes.
3. Build the monitor service and terminal-state reconciliation.
4. Complete Jenkins and Ansible polling support.
5. Update frontend edit/result/activity flows to use normalized metadata.
6. Add observability and rollout safeguards.
7. Complete verification and cutover planning.

### Parallel Work Opportunities

- Frontend result/activity work can begin once the execution-history response shape is stable.
- Jenkins and Ansible polling tasks can run in parallel after the shared adapter contract is defined.
- Audit/observability work can proceed alongside monitor implementation.
- QA work can prepare fixtures and scenarios before frontend wiring is complete.

## Task Breakdown by Domain

### Persistence / Data

- Execution-history metadata extensions
- DTO and API contract updates
- Compatibility handling for legacy task input

### Backend / API

- Tool resolution and validation
- AUTO submission orchestration changes
- Execution-history read model enhancements

### Workflow / Orchestration

- Scheduled external execution monitor
- Terminal-state reconciliation into task lifecycle
- Review-gate preservation after external completion

### Integrations

- Jenkins target parsing and polling
- Ansible target parsing and polling
- Optional callback foundation for later expansion

### Frontend / UI

- Tool-aware task edit flow
- Result modal enhancements
- Activity timeline enhancements

### Security / Reliability / Observability

- Execution sync diagnostics
- Stale execution detection
- Safe rollout and migration behavior

### Testing

- Resolution, adapter, and monitor coverage
- Contract and workflow coverage
- Frontend integration and E2E coverage

## Task Details

### EXE-001: Extend Execution Metadata Model and Contract

- **Objective**: Add the normalized execution fields required for external status, log link, approval link, and sync timing.
- **Scope**: Update persistence model, DTOs, and API responses for execution history so the frontend can render multi-tool result and activity states consistently.
- **Dependencies**: None
- **Owner type**: backend
- **Priority**: Must
- **Effort**: M
- **Notes**: Likely touches `TaskExecutionHistory`, `TaskExecutionHistoryDto`, repository queries, and any migration needed for first-class fields such as `external_status`, `external_status_message`, `external_log_url`, `external_approval_url`, and `last_synced_at`. Requires Oracle DDL migration in `src/main/resources/db/migration/`.

### EXE-002: Implement Execution Target Resolution and Input Validation

- **Objective**: Resolve the correct external tool for each AUTO task without silent misrouting.
- **Scope**: Add a resolver that supports explicit `system`, URL inference from `script`, compatibility fallback for legacy Jenkins values, and mismatch validation.
- **Dependencies**: EXE-001
- **Owner type**: backend
- **Priority**: Must
- **Effort**: M
- **Notes**: Update validation paths used by task editing, AUTO submission, and import-time normalization where needed. Touches `TaskService`, `AutoExecutionService`, and related validation helpers. [ASSUMPTION] UI may continue to submit `system` only when the user overrides auto-detect.

### EXE-003: Refactor Adapter Contract for Submit and Poll

- **Objective**: Turn the current submit-only adapter model into a reusable submit-plus-poll execution contract.
- **Scope**: Update `AutoExecutionAdapter` and orchestration services so adapters can return normalized submit data and normalized poll results.
- **Dependencies**: EXE-002
- **Owner type**: backend
- **Priority**: Must
- **Effort**: M
- **Notes**: This is the contract foundation for Jenkins and Ansible polling. Touches `AutoExecutionAdapter`, `AutoSubmissionResult`, `AutoExecutionService`, and any new normalized execution-result model. Design for polling-first delivery; do not block this contract on callback-specific requirements.

### EXE-004: Update AUTO Submission Orchestration

- **Objective**: Persist richer execution metadata immediately when an AUTO task is submitted.
- **Scope**: Update submit flow to use the new target resolver, record resolved tool metadata, seed normalized execution state, and keep current failure handling correct.
- **Dependencies**: EXE-001, EXE-002, EXE-003
- **Owner type**: backend
- **Priority**: Must
- **Effort**: M
- **Notes**: Focus area is `AutoExecutionService`. Preserve `Ready_For_Execution -> Executing` on submission success and immediate `Failed` handling on submission failure.

### EXE-005: Implement Jenkins Target Parsing and Status Polling

- **Objective**: Fully support Jenkins submission plus completion synchronization.
- **Scope**: Parse Jenkins URLs and legacy job paths, resolve queue/build state, derive log/approval links where available, and map raw Jenkins states into normalized external status.
- **Dependencies**: EXE-003
- **Owner type**: backend
- **Priority**: Must
- **Effort**: L
- **Notes**: Touches `JenkinsExecutionAdapter` and its tests. Include queue-to-build correlation, console/log URL derivation, and terminal-state mapping.

### EXE-006: Implement Ansible Target Parsing and Status Polling

- **Objective**: Fully support Ansible/AWX submission plus completion synchronization.
- **Scope**: Parse supported AWX/Tower targets, poll job/workflow status, derive output and approval URLs when available, and map raw Ansible states into normalized external status.
- **Dependencies**: EXE-003
- **Owner type**: backend
- **Priority**: Must
- **Effort**: L
- **Notes**: Touches `AnsibleExecutionAdapter` and its tests. [ASSUMPTION] Approval URL handling may apply only to workflow-based executions.

### EXE-007: Build External Execution Monitor Service

- **Objective**: Keep running AUTO executions synchronized without requiring manual backend intervention.
- **Scope**: Add a scheduled monitor that finds active execution attempts, polls the correct adapter, applies idempotent updates, and records sync timestamps and normalized state.
- **Dependencies**: EXE-004, EXE-005, EXE-006
- **Owner type**: backend
- **Priority**: Must
- **Effort**: L
- **Notes**: This is the core feature that prevents tasks from staying stuck in `Executing`. Include lock-safe/idempotent behavior, bounded polling intervals, and a feature-flag or configuration-based enablement path so polling can be rolled out gradually in non-prod and production.

### EXE-008: Reconcile Terminal External States into Task Lifecycle

- **Objective**: Translate synchronized external completion into Deployment Agent task state correctly.
- **Scope**: Implement `Executing -> Awaiting_Review` on remote success, `Executing -> Failed` on terminal remote failure, and update latest result metadata without bypassing the existing review gate.
- **Dependencies**: EXE-007
- **Owner type**: backend
- **Priority**: Must
- **Effort**: M
- **Notes**: Touches task-state update paths, execution-history completion logic, and release-flow recomputation. Preserve the rule that external success does not auto-approve the task. Must integrate with the existing release-flow recomputation path, explicitly reviewing `ReleaseFlowProgressionService` so terminal sync updates recompute flow state without accidentally invoking decision-only auto-progression behavior.

### EXE-009: Extend Task Edit UX for Tool Awareness

- **Objective**: Make task routing understandable and editable in the existing task-edit dialog.
- **Scope**: Update task-edit UI to explain full URL usage, show inferred or selected tool type, and support explicit override when needed.
- **Dependencies**: EXE-002
- **Owner type**: frontend
- **Priority**: Must
- **Effort**: M
- **Notes**: Likely touches `frontend/src/components/TaskEditDialog.vue`, `frontend/src/types/index.ts`, and `frontend/src/api/tasks.ts`. Preserve current simple edit flow and avoid a separate tool-specific screen.

### EXE-010: Extend Task Result Modal for External Execution Links and Status

- **Objective**: Surface external execution context clearly in the Task Result modal.
- **Scope**: Add normalized status badge, primary external job/build link, `Open Log`, optional `Open Approval`, submission/error text, and last-sync metadata.
- **Dependencies**: EXE-001, EXE-007, EXE-008
- **Owner type**: frontend
- **Priority**: Must
- **Effort**: M
- **Notes**: Likely touches `frontend/src/views/ReleaseFlowDetailView.vue`, `frontend/src/api/tasks.ts`, and task result types.

### EXE-011: Extend Task Activity Timeline for Execution Sync Visibility

- **Objective**: Make execution-history rows useful for external tool lifecycle tracing.
- **Scope**: Render normalized external status, sync results, external URLs, and terminal outcomes in Task Activity while preserving current audit rows and task definition context.
- **Dependencies**: EXE-001, EXE-007, EXE-008
- **Owner type**: frontend
- **Priority**: Must
- **Effort**: M
- **Notes**: Likely touches `frontend/src/components/TaskActivityDialog.vue` and related types. Ensure `WAITING_APPROVAL` is visually distinct from generic running state.

### EXE-012: Add Audit and Observability for External Sync

- **Objective**: Make synchronized execution behavior traceable and operable in production.
- **Scope**: Add structured logs, metrics, stale-execution detection, and high-value audit or lifecycle events for submit/sync/terminal transitions.
- **Dependencies**: EXE-004, EXE-007, EXE-008
- **Owner type**: security
- **Priority**: Should
- **Effort**: M
- **Notes**: Do not log secrets or raw tokens. Prefer high-signal lifecycle events over one-audit-row-per-poll.

### EXE-013: Add Backend Tests for Routing, Polling, and Lifecycle Reconciliation

- **Objective**: Lock down the new backend execution model with durable automated coverage.
- **Scope**: Add unit and integration tests for tool resolution, adapter submit/poll behavior, monitor idempotency, terminal-state mapping, and task-state transitions.
- **Dependencies**: EXE-005, EXE-006, EXE-007, EXE-008
- **Owner type**: QA
- **Priority**: Must
- **Effort**: L
- **Notes**: Include mismatch validation, remote approval wait, successful completion, failure completion, and repeated polling of the same execution.

### EXE-014: Add Frontend Integration and E2E Coverage

- **Objective**: Validate the new user-facing execution lifecycle.
- **Scope**: Cover task edit routing hints, result modal status and links, activity rendering, and final task-state updates after synchronized completion.
- **Dependencies**: EXE-009, EXE-010, EXE-011
- **Owner type**: QA
- **Priority**: Must
- **Effort**: M
- **Notes**: Include at least one Jenkins-oriented scenario and one Ansible-oriented scenario.

### EXE-015: Plan Legacy Compatibility and Rollout

- **Objective**: Ship the feature without breaking existing AUTO tasks and operational expectations.
- **Scope**: Define how legacy plain-script tasks continue to work, whether any backfill is needed for in-flight executions, how polling is enabled in non-prod and prod, and how support teams diagnose stale executions after rollout.
- **Dependencies**: EXE-007, EXE-012, EXE-013, EXE-014
- **Owner type**: devops
- **Priority**: Must
- **Effort**: M
- **Notes**: Include rollback considerations, especially if monitor behavior or new execution metadata is introduced incrementally. Rollout planning should begin during EXE-007 implementation even though final production enablement depends on later verification tasks.

### EXE-016: Prepare Optional Callback Ingestion Foundation

- **Objective**: Keep the design open for future tool-originated callbacks without blocking the polling-first delivery.
- **Scope**: Define the callback contract, security approach, and compatibility rules, but implement only the reusable foundations that do not delay the polling-based feature.
- **Dependencies**: EXE-003
- **Owner type**: backend
- **Priority**: Could
- **Effort**: S
- **Notes**: [ASSUMPTION] This remains optional unless infrastructure or tool owners confirm that callbacks are practical in production.

## Dependency Plan

### Critical Path

`EXE-001 -> EXE-002 -> EXE-003 -> EXE-004 -> EXE-005/EXE-006 -> EXE-007 -> EXE-008 -> EXE-009/EXE-010/EXE-011 -> EXE-013/EXE-014 -> EXE-015`

### Prerequisite Clusters

- **Data and contract foundation**
  - EXE-001, EXE-002, EXE-003, EXE-004
- **Tool integration**
  - EXE-005, EXE-006
- **Execution lifecycle synchronization**
  - EXE-007, EXE-008, EXE-012
- **Frontend adoption**
  - EXE-009, EXE-010, EXE-011
- **Verification and rollout**
  - EXE-013, EXE-014, EXE-015

### Parallel Workstreams

- EXE-005 and EXE-006 can proceed in parallel once EXE-003 is stable.
- EXE-009 can start as soon as EXE-002 clarifies the input contract.
- EXE-010 and EXE-011 can proceed in parallel once EXE-001 stabilizes the execution-history response shape.
- EXE-012 can run alongside EXE-007 and EXE-008.
- EXE-016 is independent from the critical path after EXE-003.

## Risks / Blockers

- Jenkins queue/build correlation may require environment-specific handling and careful mocking in tests.
- Ansible approval support may depend on workflow-job features not used by all current templates.
- Legacy plain-script tasks are convenient for compatibility but can mask routing ambiguity if the UI does not surface the resolved tool clearly.
- Polling introduces recurring API load and operational tuning needs.
- Preserving Deployment Agent review after external approval keeps governance intact but may be perceived as double approval unless communicated clearly.

## Open Questions

1. Should Deployment Agent always remain the final approval point after a remote external-approval step succeeds?
2. Which exact Ansible/AWX target types are in scope for the first implementation pass: job templates only, or workflow job templates as well?
3. Do users need a manual `Refresh External Status` action in the UI, or is background polling sufficient?
4. Is future callback support desirable enough to justify early contract work during this phase?
