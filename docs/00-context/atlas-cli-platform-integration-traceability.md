# Atlas CLI Platform Integration Traceability

**Slice:** `atlas-cli-platform-integration`
**Date:** 2026-08-07
**Status:** Complete and verified
**Language:** English-only (ADR-0009)

## Slice Contract

| Field | Value |
|---|---|
| Goal | Agent-neutral Atlas control plane for local CLI execution, artifacts, human review, event/audit, and capability/Skill usage |
| Source of behavior | `docs/03-spec/atlas-cli-platform-integration-spec.md` |
| Source of execution tasks | `docs/06-tasks/atlas-cli-platform-integration-tasks.md` |
| Boundary decision | `docs/00-context/decisions/ADR-0011-atlas-integration-is-platform-control-plane.md` (Accepted) |
| Source contract | Adjacent Atlas CLI three-document set pinned in the execution manifest |
| Verification | Backend full suite, architecture selection, frontend tests/build, responsive browser smoke, code/design/docs/security review |

## Document Map

| Stage | Path | Status |
|---|---|---|
| Requirements | `docs/01-requirements/atlas-cli-platform-integration-requirement.md` | Accepted |
| User stories | `docs/02-user-stories/atlas-cli-platform-integration-user-stories.md` | Accepted |
| Specification | `docs/03-spec/atlas-cli-platform-integration-spec.md` | Accepted |
| Architecture | `docs/04-architecture/atlas-cli-platform-integration-architecture.md` | Accepted |
| Data flow | `docs/04-architecture/atlas-cli-platform-integration-data-flow.md` | Accepted |
| Data model | `docs/04-architecture/atlas-cli-platform-integration-data-model.md` | Accepted |
| Design | `docs/05-design/atlas-cli-platform-integration-design.md` | Accepted |
| API guide | `docs/05-design/contracts/atlas-cli-platform-integration-API_IMPLEMENTATION_GUIDE.md` | Accepted |
| Tasks | `docs/06-tasks/atlas-cli-platform-integration-tasks.md` | Complete |
| ADR | `docs/00-context/decisions/ADR-0011-atlas-integration-is-platform-control-plane.md` | Accepted |
| Manifest | `docs/00-context/execution-manifests/atlas-cli-platform-integration.yaml` | Completed |

## Skill Chain Evidence

**SDD skill chain used: yes.**

| Step | Project-local skill file read | Output |
|---|---|---|
| Entry | `.agents/skills/wwa-sdd-generate-all/SKILL.md` | slice contract, chain, gate requirements |
| 1 | `.agents/skills/req-to-user-story/SKILL.md` | requirements and stories |
| 2 | `.agents/skills/user-story-to-spec/SKILL.md` | behavioral specification |
| 3 | `.agents/skills/spec-to-architecture/SKILL.md` | architecture, data flow, data model |
| 4 | `.agents/skills/architecture-to-design/SKILL.md` | detailed design and API guide |
| 5 | `.agents/skills/design-to-tasks/SKILL.md` | ordered implementation tasks |
| 6 | `.agents/skills/review-doc-quality/SKILL.md` | final quality review; result recorded below |

Supporting project-local skills read: `agentic-sdlc-orchestrator`, `execution-manifest`, `freshness-gate`,
`context-engineering-adr`, `tasks-to-code`, `review-code-against-design`, and `review-docs-against-code`.
Supporting engineering skills read: `tdd-workflow`, `security-review`, and `verification-loop`.

`architecture-review`: not applicable because no project-local skill by that name is available. Architecture
was reviewed against the `spec-to-architecture` checklist, SDD profile architecture gate, ADR-0011, existing
ArchUnit rules, and the read-only planner/backend explorer findings.

`review-doc-quality`: passed on 2026-08-07. No unresolved Critical or Major findings. The pass added an
explicit internal-to-public Task status mapping, made the exact-attempt Review service call direction
non-circular, and specified that an invalid bearer header cannot fall back to an existing Web session.

## Source Grounding

| Source | Revision/blob | Key rule carried forward |
|---|---|---|
| Atlas CLI API contract | source commit `fa95065a…`; blob `bdc5854e…` | Task distinct from Execution, one active attempt, Atlas authority, idempotency/fencing, least disclosure |
| Atlas CLI OpenAPI | source commit `fa95065a…`; blob `169a3663…` | `/api/v1/integration` baseline and envelopes/security |
| Atlas CLI architecture decisions | source commit `fa95065a…`; blob `3adc8e24…` | CLI separate, Web control plane, no server execution/LangGraph, generic route only with boundary enforcement |
| User request | 2026-08-07 | additive Web endpoints, exact five client types, usage filters/metrics, migration/tests/traceability |
| Existing code | working tree at `abf3850…` | reuse Task, attempt history, state machine, audit, access, correlation, Agent boundary |

## Requirement Traceability

| Requirements | Stories | Spec/design focus | Tasks | Verification |
|---|---|---|---|---|
| ACI-REQ-001-010 | US-01-03, US-09 | state model, readiness, locking/fencing, lifecycle service | T10, T20, T40 | lifecycle, concurrency, rerun, architecture tests |
| ACI-REQ-011-016 | US-02, US-03, US-06 | idempotency, correlation, atomic event + audit | T10, T30, T40 | replay/conflict/in-progress/correlation/event tests |
| ACI-REQ-017-021 | US-04 | Artifact policy and separate content path | T20, T50 | multipart/reference/policy/header/isolation tests |
| ACI-REQ-022-025 | US-05 | exact-attempt immutable Review Decision | T20, T60 | review state/permission/idempotency/rerun tests |
| ACI-REQ-026-030 | US-07 | immutable execution facts, scoped aggregation | T20, T70 | all metrics/filters/client/version tests |
| ACI-REQ-031-035 | US-01-03, US-09 | digest identity, composite authorization, Agent-neutral fitness | T10, T30 | bearer/scope/ownership/404 + ArchUnit tests |
| ACI-REQ-036-041 | US-08 | Platform Execution Center, polling, safe projections | T80 | frontend static tests/build/screenshot smoke |
| ACI-NFR-001-010 | all | security, consistency, portability, compatibility, privacy, no server runtime | T10-T90 | full verification and security/design reviews |

## Implementation Evidence

This table is updated as code lands; a task is not complete merely because a file exists.

| Task | Status | Evidence |
|---|---|---|
| ACI-T00 | Complete | Full English SDD chain, ADR-0011, accepted proposal, freshness record, validated execution manifest |
| ACI-T10 | Complete | TaskStateMachine, lifecycle/concurrency/controller/security, and `AtlasPlatformArchitectureTest` coverage |
| ACI-T20 | Complete | V21 + synchronized Oracle schema; Task/Execution extensions and Event/Artifact/Input/Review/Idempotency persistence; Oracle V20-to-V21 execution test is environment-gated |
| ACI-T30 | Complete | digest bearer registry, trusted-remote invalid-response throttle with same-address valid-credential bypass, exact-credential correlation/lifecycle/Artifact guards, secret-safe binding, per-request current employee Access Grant authority, isolated synthetic Guest marker, historical/mixed Guest fail-closed behavior, composite authorization, strict Integration-only JSON DTOs, safe errors, hashed-key idempotency replay/reclaim and atomic Task-lock fence tests |
| ACI-T40 | Complete | fenced lifecycle, positive safe-prose validation without silent loss, parent recomputation, atomic event/audit, keyset Task/history reads, human rerun, legacy route isolation |
| ACI-T50 | Complete | upload/reference/download, approved input, quotas/rate/pre-parse exact-client transfer admission, format-specific plain/Markdown/streaming-JSON source gates with independent byte/token/depth budgets, production scanner gate, globally sorted local/source locks, bounded ordered retention cleanup/renewal/legal hold |
| ACI-T60 | Complete | exact latest-attempt human Review Decision with preserved safe rationale, all-Request-Task lock order, replay reauthorization, and legal state progression |
| ACI-T70 | Complete | database aggregation with full metric/filter/client/user/version integration coverage |
| ACI-T80 | Complete | Platform route, typed API/store, 10-second visible polling, history/artifact/review/failure/pending-sync/usage views, frontend tests, and desktop/mobile screenshot smoke |
| ACI-T90 | Complete | code/design/docs/security reviews closed; backend, architecture, frontend, browser, secret/source, and boundary verification passed |

## Verification Evidence

| Gate | Result |
|---|---|
| Backend | `mvn test`: 547 tests, 0 failures, 0 errors, 1 environment-gated skip |
| Architecture | `mvn test -Dtest='*ArchitectureTest,*ArchTest,AgentModuleBoundaryTest'`: 15 tests, 0 failures/errors/skips |
| Frontend unit/static checks | `cd frontend && npm test`: 11 tests passed |
| Frontend production build | `cd frontend && npm run build`: `vue-tsc` and Vite build passed; 225 modules transformed |
| Browser smoke | Desktop Operations and Usage views plus 390 × 844 mobile shell passed; a second visible-page Task poll was observed, no console error occurred, and credential/source/path/repository sentinels were absent |
| Code and security review | Independent reviewers reported no remaining P1/P2 findings; 54 focused security tests passed during security review |
| Docs review | Requirements, stories, spec, architecture, design, API guide, tasks, migration, code, and tests are aligned; no unresolved Critical/Major finding |

Screenshot evidence:

- `docs/assets/screenshots/atlas-execution-center-after-desktop.png`
- `docs/assets/screenshots/atlas-execution-center-usage-after-desktop.png`
- `docs/assets/screenshots/atlas-execution-center-before-mobile.png`
- `docs/assets/screenshots/atlas-execution-center-after-mobile.png`

The single backend skip is `AtlasOracleMigrationIntegrationTest`: it is deliberately enabled only when a real
Oracle V20 database is supplied. H2 migration-contract coverage passed locally, and the Oracle test asserts that
Flyway executes exactly V21 from that baseline when the environment is available.

## Contract Clarifications And Non-Drift Rules

1. Additive v1 endpoints required by Web do not redefine the adjacent CLI paths.
2. The five requested client types replace older illustrative OpenAPI examples for this implementation.
3. Team means `Request.snowGroup`; project means `ReleaseFlow.projectId`; Agent means `Request.agent`.
4. Skill ID is capability ID when capability type is Skill.
5. Technical success/failure rate excludes running/cancelled; review acceptance remains separate.
6. Pending sync means Atlas has a running attempt awaiting a terminal client command. Unsent local facts are unknowable.
7. Atlas Server coordinates only; it does not run local Skills, code, builds, repository scans, LangGraph, or
   LLMs. The bounded Artifact intake malware/DLP scanner is a security gate, not a local Skill/runtime.
8. Idempotency namespace is principal + method + canonical path + key; client application is replay ownership,
   raw keys are hashed, and completed rows follow resource lifetime.
9. The adjacent API returns canonical repository URL and validated sourcePath for CLI compatibility; the Web
   renderer intentionally omits both.
10. Any implementation variance must update requirements/spec/design/tasks/this matrix before code is accepted.

## Closeout Checklist

- [x] All requirements map to concrete code and passing tests.
- [x] SDD doc-quality review has no unresolved Critical/Major findings.
- [x] Code-against-design and docs-against-code reviews completed.
- [x] Security review completed with no unresolved Critical/High findings.
- [x] `mvn test` and architecture selection pass.
- [x] Frontend tests/build and screenshot smoke pass.
- [x] CHANGELOG, change review/archive, task statuses, and handoff updated.
