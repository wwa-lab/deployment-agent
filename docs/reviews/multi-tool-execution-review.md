# Document Review Report

**Reviewed:** 2026-03-27
**Documents under review:**
- `docs/05-design/multi-tool-execution-design.md` (Design)
- `docs/06-tasks/multi-tool-execution-tasks.md` (Tasks)

---

## Part 1 — Design Document Review

### Document Summary
- **Document type:** Design (Phase 4)
- **Scope summary:** Extends Release Agent's AUTO execution model from fire-and-forget submission into multi-tool routing (Jenkins + Ansible), polling-based status synchronization, and normalized external execution context in the Task Result and Activity UI. Covers 6 internal modules, API changes, data model extensions, and UX requirements.
- **Intended next stage:** Task implementation (EXE-001 through EXE-016)

### Overall Assessment
- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** The design is well-structured, with clear module decomposition, an explicit normalized status model, resolution precedence, and state machine transitions. The document is honest about assumptions and open questions. Two major gaps — the unformalized adapter contract and the unspecified polling schedule — leave critical implementation decisions to the implementer rather than resolving them at the design stage. These are unlikely to cause architectural conflict but will generate unnecessary rework during EXE-003 and EXE-007.

### Strengths
- **Module decomposition is clear and non-overlapping.** Each of the 6 modules (Resolution, Submission Orchestrator, Status Sync, Adapter Capability, Result/Activity Presentation, Audit/Observability) has distinct responsibilities with no ambiguous overlap.
- **Normalized external status table is production-quality.** All 8 statuses are defined with terminal/non-terminal designation, meaning, and the exact Release Agent effect. This is the kind of precise contract that prevents adapter drift.
- **Resolution precedence table covers all routing cases.** Explicit override, URL inference, legacy fallback, and mismatch error are all defined with examples.
- **State machine transitions are fully enumerated.** The four AUTO-task transitions are explicit; the review gate preservation after external success is stated as a rule, not an afterthought.
- **Assumptions are labeled and resolved/unresolved status is explicit.** The `[Resolved]` / `[Assumption]` distinction is useful for downstream reviewers.
- **Observability expectations enumerate specific metrics.** Not generic "add monitoring" but specific counts and latency targets.
- **Security design avoids secrets leakage.** Explicit rule that secrets must not appear in DTOs, audit payloads, or structured logs.

---

### Issues Found

#### Major

**M1 — Adapter interface contract is described but not formalized**
- Why it matters: EXE-003 and EXE-005/EXE-006 are all building to the same `AutoExecutionAdapter` interface. Without typed method signatures, two engineers working these tasks in parallel will likely produce incompatible implementations.
- Affected section: "Internal Service Interfaces — `AutoExecutionAdapter`" (lists `submit(normalizedTarget, inputParameters)` and `pollStatus(executionHistory)` as method names only)
- Recommended fix: Add a Java interface skeleton or at minimum specify parameter types and return types for `submit()` and `pollStatus()`. Define the "common execution-status payload" that adapters normalize into — without this, "normalize raw tool data into a common response model" is under-specified.

**M2 — Polling schedule and concurrency model not designed**
- Why it matters: The monitor service is the core of this feature. How frequently it runs, whether it batches, what happens if a poll cycle takes longer than the interval, and whether any locking is needed to prevent concurrent polling of the same execution are all implementation decisions — but none are guided by the design. EXE-007 as written will require the implementer to make these decisions without a design basis.
- Affected section: "External Status Sync Module — Internal Design Concerns" mentions idempotency and operational visibility but not scheduling strategy.
- Recommended fix: Specify the polling interval range (or make it configurable), address whether `@Scheduled` with `fixedDelay` or `fixedRate` semantics is appropriate, and state whether pessimistic locking or a "locked-for-polling" flag is needed on `TaskExecutionHistory` rows.

**M3 — `execution_status` vs `submission_status` field naming is unresolved**
- Why it matters: The synchronization flow references `execution_status = Running` as the filter predicate for the monitor, but the existing entity uses `submission_status`. The design adds new fields (`external_status`, `last_synced_at`, etc.) but does not explicitly state whether `submission_status` is renamed, extended, or replaced. EXE-001 implementer will need to make this structural decision without a design baseline.
- Affected section: "Synchronization Flow — Step 1" and "Existing fields retained" table both mention `submission_status` as an existing field but then the sync flow uses `execution_status = Running`.
- Recommended fix: Clarify whether `submission_status` covers both submit-time and polling-time execution status, or whether a new `execution_status` column is introduced. If the latter, state it explicitly in the data model section.

#### Minor

**m1 — Task Edit UX is ambiguous on "chip vs selector" choice**
- Why it matters: The UX spec says "show either an inferred tool chip, or a tool selector with Auto Detect, Jenkins, and Ansible." Leaving this as an either/or is a design decision that will be resolved by whoever implements EXE-009, potentially inconsistently with product intent.
- Affected section: "Edit Task / Run Task — Primary UX changes"
- Recommended fix: Make one choice; document which UI pattern is preferred and when the selector is shown vs. the chip is shown.

**m2 — API interface design does not include field types**
- Why it matters: The `GET /tasks/{id}/executions` section lists new logical outputs (`externalStatus`, `externalLogUrl`, etc.) without their types or nullability. A developer implementing the DTO and the frontend component types will work from inference.
- Affected section: "Public Task APIs — GET /tasks/{id}/executions — Additional logical outputs"
- Recommended fix: Add types (String, Enum, Timestamp, URL-typed String) and indicate which fields are nullable/optional.

**m3 — Testing section does not distinguish unit vs integration scope**
- Why it matters: With Spring Boot + H2 for tests and the existing 167-test baseline, some tests require full Spring context and some should be pure unit tests. The testing section lists what to cover but not the test category.
- Affected section: "Testing Considerations"
- Recommended fix: Tag each test area as "unit" or "integration/slice test" to align with the project's existing test structure.

---

### Completeness Check

| Expected element | Status |
|---|---|
| Module design (internal structure) | Present — 6 modules with responsibilities and key interactions |
| Interface design (APIs, contracts, data structures) | Partial — API endpoints defined; adapter interface and DTO types incomplete |
| Data design (schemas, storage format) | Present — logical fields named; types absent; submission_status ambiguity (see M3) |
| Workflow / state machine | Present — transitions enumerated, sequence diagram included |
| Validation rules | Present — input validation and workflow-level validation sections |
| Error handling strategy | Present — submission failure, polling failure, and unknown-state paths all addressed |
| Edge cases | Partial — retry/rerun, UNKNOWN state, and legacy fallback addressed; concurrent polling not addressed |

### Consistency Check
- **Internal contradictions:** `execution_status = Running` in sync flow vs. `submission_status` in existing fields table (see M3)
- **Cross-section mismatches:** Module 5 (Presentation) lists `TaskEditDialog.vue` as a key interaction but the API Design section does not include any edit-dialog-specific endpoint change — this is consistent with the existing `PUT /tasks/{id}/input`, but the reference without clarification could confuse.
- **Phase drift:** None found. The design stays within Phase 4 scope — no business requirements restated, no actual Java code, no sprint planning.
- **Traceability gaps:** The document states it extends `docs/04-architecture/architecture.md` and `docs/05-design/design.md` but does not cite which specific architecture decisions are being fulfilled. This is low-risk since those documents exist and are accessible.

### Readiness for Next Stage
- **Target stage:** Task implementation (EXE-001 through EXE-016)
- **Verdict:** Sufficient with the adapter contract and polling model gaps flagged as known risks that implementers must resolve.
- **Blockers:** M1 (adapter interface) and M2 (polling schedule) are likely to cause EXE-003 and EXE-007 rework; M3 may require a schema decision mid-EXE-001. None are architectural blockers.

### Recommended Revisions
1. **[M1]** Add a Java interface skeleton for `AutoExecutionAdapter` and define the normalized poll-result payload structure.
2. **[M2]** Add a Polling Design subsection specifying interval, `fixedDelay` vs `fixedRate`, batch behavior, and concurrency safety approach.
3. **[M3]** Resolve and document whether `submission_status` is extended, renamed, or supplemented by a new column for polling-time execution state.
4. **[m1]** Commit to one of the two task-edit UX patterns (chip vs. explicit selector) and document it.
5. **[m2]** Add types and nullability to the `GET /tasks/{id}/executions` response fields.

### Minimal Fix Path
Resolve M3 (field naming ambiguity) before EXE-001 begins, and resolve M1 (adapter interface) before EXE-003 is assigned. M2 can be resolved as a design addendum at the start of EXE-007 if it is not pre-resolved.

### Open Questions / Risks
1. The three unresolved open questions (DA as final approval gate, Ansible target types in scope, manual Refresh action) remain genuinely open and should be answered before EXE-005/EXE-006 and EXE-009 are assigned.
2. If `submission_status` is extended to cover polling-time state, existing code that checks `submission_status = SUCCESS` for display purposes may need to be audited.
3. Jenkins queue-to-build correlation is described as polling behavior but Jenkins API behavior here is version-sensitive — worth flagging to the EXE-005 implementer to verify against the actual Jenkins version in use.

---

**Final verdict: Ready with minor fixes**

---

---

## Part 2 — Tasks Document Review

### Document Summary
- **Document type:** Tasks (Phase 5)
- **Scope summary:** Breaks the multi-tool execution design into 16 implementation tasks (EXE-001–EXE-016) spanning backend, frontend, QA, DevOps, and security. Includes dependency ordering, critical path, parallel workstream opportunities, risks, and open questions.
- **Intended next stage:** Engineering sprint execution

### Overall Assessment
- **Quality rating:** Good
- **Readiness verdict:** Ready with minor fixes
- **Rationale:** The task breakdown is well-sequenced, covers all major workstreams from the design, correctly identifies the critical path, and calls out parallel opportunities. Two notable gaps — missing definition of done per task and absent effort estimates — reduce sprint-planning usability but do not block a capable team from starting. One dependency gap in EXE-008 could cause a missed integration with existing progression logic.

### Strengths
- **Critical path is correctly identified.** The linear chain from EXE-001 through EXE-015 reflects the actual dependency structure.
- **Parallel opportunities are explicit and accurate.** EXE-005/EXE-006 in parallel, frontend unblocking from EXE-001 contract, EXE-012 alongside monitor work — all correctly identified.
- **Per-task file references are specific.** Notes like "Likely touches `JenkinsExecutionAdapter`", `TaskActivityDialog.vue`, `AutoExecutionService`" give implementers a concrete starting point.
- **Assumptions are flagged inline.** `[ASSUMPTION]` markers in EXE-002, EXE-006, EXE-016 notes keep inherited uncertainty visible at the task level.
- **Risks and open questions are carried forward** from the design and rephrased in implementation terms — not copy-pasted verbatim.
- **EXE-016 is correctly marked "Could"** and correctly positioned as optional post-EXE-003.

---

### Issues Found

#### Major

**M1 — No definition of done for any task**
- Why it matters: Every task describes what to build (objective + scope) but not the condition under which it is complete. Without a DoD, tasks get marked done prematurely ("I wrote the class") or remain open indefinitely ("it's not fully tested yet"). This is particularly risky for EXE-007 (monitor service), which has behavioral properties (idempotency, stale detection) that are easy to claim but hard to verify without explicit acceptance criteria.
- Affected section: All task entries
- Recommended fix: Add a "Done when:" line per task, or define a shared DoD at the top of the document (e.g., "All Must tasks require: unit tests pass, integration tests pass, typecheck clean, code reviewed").

**M2 — EXE-008 missing dependency on `ReleaseFlowProgressionService`**
- Why it matters: EXE-008 moves a task from `Executing` to `Awaiting_Review` on remote success. In the existing codebase, task state changes that affect release-flow progression are coordinated through `ReleaseFlowProgressionService` (or equivalent). The task notes only say "touches task-state update paths, execution-history completion logic, and release-flow recomputation" but does not explicitly connect to the existing service. A developer who implements this as a direct status-field update without invoking the progression service will produce a silent bug where the release flow does not recompute.
- Affected section: EXE-008 notes
- Recommended fix: Add an explicit reference to `ReleaseFlowProgressionService` in the EXE-008 notes and specify that the terminal sync must invoke the same progression hook as manual decision paths.

**M3 — No effort estimates**
- Why it matters: The tasks document is intended for sprint planning. With 16 tasks spanning backend (10), frontend (3), QA (2), and DevOps (1), there is no sizing signal. A sprint planner cannot determine whether EXE-001–EXE-004 fit in one sprint or three. The completeness criteria for tasks.md explicitly requires "estimated effort or complexity indicators (if applicable)."
- Affected section: All task entries
- Recommended fix: Add t-shirt sizes (S/M/L/XL) or story points per task. Even rough estimates are more useful than none.

#### Minor

**m1 — EXE-001 does not flag Oracle DDL migration requirement**
- Why it matters: Adding `external_status`, `external_log_url`, `external_approval_url`, `external_status_message`, and `last_synced_at` to `task_execution_history` in Oracle production requires a DDL migration script. The project convention (from CLAUDE.md) is: "Oracle schema changes: provide DDL in `src/main/resources/db/migration/`". This is not mentioned in EXE-001.
- Affected section: EXE-001 notes
- Recommended fix: Add "Requires DDL migration for Oracle; provide script in `src/main/resources/db/migration/`" to EXE-001 notes.

**m2 — EXE-015 (rollout planning) is sequenced too late**
- Why it matters: EXE-015 depends on EXE-007, EXE-012, EXE-013, EXE-014 — meaning rollout decisions come after the monitor is built. But rollout concerns (feature flags, enabling polling in non-prod first, backward compatibility of new DB columns) should inform EXE-007 design, not follow it. If polling needs a feature flag, that needs to be in EXE-007 scope, not a separate later task.
- Affected section: EXE-015 and EXE-007
- Recommended fix: Either create an EXE-015-lite planning task with no dependencies (to define rollout strategy before implementation begins) or add a note to EXE-007 explicitly addressing whether a feature flag is required before enabling scheduling.

**m3 — `external_status_message` missing from EXE-001 field enumeration**
- Why it matters: The design data model lists 5 new fields including `external_status_message`. The EXE-001 notes enumerate only 4 (`external_status`, `external_log_url`, `external_approval_url`, `last_synced_at`). The 5th field is mentioned in the design but omitted from the tasks, which is an easy oversight to miss during implementation.
- Affected section: EXE-001 notes
- Recommended fix: Add `external_status_message` to the field list in EXE-001 notes.

---

### Completeness Check

| Expected element | Status |
|---|---|
| Task list granular enough to implement independently | Present — 16 tasks at reasonable granularity |
| Dependencies between tasks | Present — per-task and critical path |
| Priority or ordering | Present — Must/Should/Could + recommended sequencing |
| Effort estimates | Missing — no story points or t-shirt sizes |
| Owner/team assignment | Present — owner type per task |
| Blockers or pre-conditions | Partial — open questions noted; rollout prerequisites not pre-resolved |
| Definition of done per task | Missing — no explicit DoD at task or document level |

### Consistency Check
- **Internal contradictions:** None found. Dependency graph is acyclic; EXE-005 and EXE-006 are correctly parallel; EXE-016 is correctly optional.
- **Cross-section mismatches:** EXE-001 field list omits `external_status_message` vs. design data model (see m3).
- **Phase drift:** None found. No design decisions pushed into tasks; no business requirements restated.
- **Traceability gaps:** All 16 tasks trace to design modules or API sections. EXE-015 (rollout) has no explicit design section to trace to — this is acceptable for an operational task but worth noting.

### Readiness for Next Stage
- **Target stage:** Engineering sprint execution
- **Verdict:** Sufficient for a team to begin EXE-001 through EXE-004 immediately; EXE-007 and EXE-008 should not be assigned until the design gaps (polling model, adapter interface) are resolved.
- **Blockers:** M2 (EXE-008 missing progression service reference) is the highest-risk item for silent bugs. M1 (no DoD) should be addressed before sprint kickoff.

### Recommended Revisions
1. **[M1]** Add a shared "Definition of Done" section at the top or a "Done when:" line for each Must-priority task.
2. **[M2]** Add an explicit `ReleaseFlowProgressionService` reference in EXE-008 notes and specify it must be invoked on terminal sync.
3. **[m1]** Add DDL migration requirement to EXE-001 notes.
4. **[m3]** Add `external_status_message` to EXE-001 field list.
5. **[M3]** Add t-shirt size or story-point estimates for sprint planning.
6. **[m2]** Add a rollout-strategy pre-task or note in EXE-007 about feature flag / phased enablement requirement.

### Minimal Fix Path
Apply M2 fix (EXE-008 progression service) and m3 fix (EXE-001 missing field) before sprint kickoff. Add a shared DoD before tasks are assigned. Effort estimates can follow once the team does initial sizing.

### Open Questions / Risks
1. All 4 open questions from the design are carried forward and remain unresolved. Questions 1 (DA as final approval gate) and 2 (Ansible target types) must be answered before EXE-005/EXE-006 and EXE-009 are assigned.
2. EXE-016 (callback foundation) is correctly marked "Could" but its presence risks scope creep if EXE-003 adapter contract is designed with callback compatibility in mind — EXE-003 notes should explicitly state "design for polling-first; do not gate on callback requirements."

---

**Final verdict: Ready with minor fixes**
