# Project Rules

## Product First

WWA Agent Hub is an internal multi-agent delivery platform, not a one-off script. Features should support repeatable workflows across Deployment, Testing, Build, and shared Platform controls: upload, review, execute, audit, configure, and navigate.

## Required Reading

Before non-trivial changes, read:

1. `docs/00-context/AGENT_HANDOFF.md` — **active resume state; read first in every new session**
2. `PROJECT_RULES.md` (this file)
3. `AGENTS.md` / `CLAUDE.md` (tool bridges)
4. `DEVELOPMENT_STANDARDS.md`
5. `docs/00-context/sdd-profile.md`
6. `docs/SDD-BOOTSTRAP.md` when generating or updating a slice SDD set
7. Relevant slice docs under `docs/01-*` through `docs/06-*` and ADRs under `docs/00-context/decisions/`

## Language

Project rules, SDD artifacts, agent handoff, bootstrap, and related checklists are **English-only** (ADR-0009). Do not create `.zh-CN.md` companions for these unless the user explicitly asks. Code, comments, and runtime UI copy remain English unless a product slice requires otherwise.

## Agent Handoff Protocol

Cross-IDE / cross-agent continuity uses a single active handoff (ADR-0008, English-only per ADR-0009):

| Role | Path |
|---|---|
| Active handoff | `docs/00-context/AGENT_HANDOFF.md` |
| Archives / templates | `docs/00-context/handoffs/` |

Rules:

- **Incoming session:** read the active handoff before product, SDD, or implementation work; then follow its sources-of-truth order. Do not resume from chat memory alone.
- **Outgoing session:** before ending a session with meaningful progress, update the handoff (status, done, next, blockers, verification, branch/commit if known).
- Handoff complements — does not replace — `{slice}-traceability.md` and optional `execution-manifests/*.yaml`.
- When a major phase completes, optionally archive a frozen copy under `docs/00-context/handoffs/`.

## SDD First

Use Spec Driven Development before implementation work begins.

- Use `docs/00-context/sdd-profile.md` as the WWA SDD profile.
- Use `docs/03-spec/` as the behavior source of truth.
- Use `docs/06-tasks/` as the implementation checklist.
- Do not implement behavior that is not represented in the current slice spec.
- Keep slice traceability in `docs/00-context/{slice}-traceability.md`.
- Prototype-only refinements may remain lightweight only when they do not introduce new durable behavior contracts.

## Goal-Driven SDD

WWA uses goal-driven SDD for implementation slices when the user sets an explicit slice goal.

Each slice goal should include:

- Goal: the user-facing outcome
- Slice: stable kebab-case slug
- Scope: included and excluded behavior
- Source of truth: SDD files, prototypes, ADRs
- Acceptance: observable completion criteria
- Verification: commands / checks / manual review
- Constraints: security, agent boundary, audit, data-safety, profile limits

For goal-driven work:

- Do not start implementation until required SDD docs exist or are updated.
- Use `docs/06-tasks/{slice}-tasks.md` as the executable checklist.
- Keep scope surgical; split unrelated domains into multiple slices.
- Treat verification evidence as part of the deliverable.
- If the goal conflicts with WWA safety rails or platform boundaries, stop and surface the conflict before coding.

## SDD Generation Skill Chain

For full WWA SDD generation, use `docs/SDD-BOOTSTRAP.md` and the project-local `wwa-sdd-generate-all` workflow:

- Claude Code: `.claude/skills/wwa-sdd-generate-all/`
- Codex / shared agents: `.agents/skills/wwa-sdd-generate-all/`

The workflow must orchestrate the project-local SDD skill chain:

1. `wwa-sdd-generate-all` (entry / orchestration)
2. `req-to-user-story`
3. `user-story-to-spec`
4. `spec-to-architecture`
5. `architecture-to-design`
6. `design-to-tasks`
7. `review-doc-quality`

Use `architecture-review` when the slice materially changes architecture, platform boundaries, API/persistence, security, or data ownership.

The SDD completion report must list the entry skill, downstream skills used, skill files read, and `review-doc-quality` result. If the required project-local skill chain is unavailable or not used, stop and report instead of generating ad hoc SDD documents.

`sdd-slice-bootstrap` remains valid for auditing an existing slice skeleton; full generation should prefer `wwa-sdd-generate-all`.

SDD generation stops at reviewable artifacts and task checklists unless the user explicitly asks the same agent to continue into implementation.

## SDD And Implementation Collaboration

- Claude Code is the preferred SDD document owner when available.
- Codex may generate or update SDD when that is the fastest safe path, or when implementation reveals docs must be corrected.
- Codex is the preferred implementation owner for Vue/Java/test changes against accepted SDD.
- Implementation agents must not silently expand product scope beyond the accepted SDD.

Default handoff:

1. Produce or update English SDD for the slice.
2. User accepts scope and tasks.
3. Implement strictly against `docs/03-spec/` and `docs/06-tasks/`.
4. Report verification evidence and any SDD/implementation mismatches.
5. Material SDD changes go back through the SDD workflow.

Source-of-truth hierarchy for implementation:

1. Current user instruction
2. `PROJECT_RULES.md`, `AGENTS.md`, `CLAUDE.md`, `DEVELOPMENT_STANDARDS.md`
3. `docs/03-spec/` for behavior
4. `docs/06-tasks/` for implementation checklist
5. Other SDD, ADRs, and product context documents

## Quality Gates

A slice goal is not complete until quality gates are checked and reported.

Use `DEVELOPMENT_STANDARDS.md` for engineering detail. Required gates:

1. **Goal gate** — scope, exclusions, acceptance, verification, and constraints are clear.
2. **Product readiness claim gate** — do not call work “ready”, “done”, or product-ready unless the documented local user workflow was actually run. If only automated gates or prototypes were verified, say so.
3. **SDD gate** — required slice docs exist or are updated; IDs are traceable; tasks map to the spec; full SDD generation reports the skill chain used.
4. **Implementation gate** — changed behavior is represented in `docs/03-spec/`; code stays within the active slice.
5. **Security and data gate** — no secrets, real credentials, private absolute paths, or confidential production dumps in source. Respect HITL safety rails in `CLAUDE.md` / `AGENTS.md`.
6. **Platform / agent boundary gate** — agent-specific controllers and workspaces stay isolated; shared components remain agent-agnostic; server forces `effectiveAgent`.
7. **Verification gate** — task-listed checks are run, or skipped checks are named with reasons.
8. **Context status gate** — update `{slice}-traceability.md` when slice status changes.
9. **Evidence gate** — final response includes documents changed, code changed, verification evidence, and residual risks.

## Lessons Learned

Acceptance mismatches must become durable learning:

- Record reusable lessons in `docs/00-context/lessons-learned.md` (add file if missing).
- Update the artifact that would have prevented the issue (spec, design, task, standard, rule, test).
- Do not rely on chat memory alone.

## Control Tower And Atlas Reference

Use `wwa-lab/Agentic-SDLC-Control-Tower` and mature patterns from `atlas-knowledge-hub` as **reference** sources for SDD discipline, skill-chain evidence, and quality gates (not bilingual mandates).

- Borrow rules that improve clarity, safety, and traceability.
- Do **not** copy Atlas domain rules (knowledge wiki, WeKnora, parser/vector adapters, PostgreSQL-only assumptions).
- WWA project-local rules take precedence when more specific.

## Platform Boundaries (WWA)

- Agent-specific controllers: `agents/<agent>/web/` under `/api/<agent-key>/`
- Platform shared controllers: `platform/web/shared/`
- Domain logic: `domain/`
- Shared contracts: `contracts/`
- Frontend agents: `frontend/src/agents/<agent>/` as thin wrappers around `frontend/src/platform/components/`
- Do not merge navigation catalogs into Configuration Management unless an ADR explicitly reverses that boundary

## Safety Rails

### NEVER

- Modify `.env`, lockfiles, or CI secrets without explicit approval
- Remove feature flags without searching all call sites
- Commit without running the verification required for the changed layer
- Auto-approve the human-in-the-loop decision classes listed in `CLAUDE.md`

### ALWAYS

- Show diff before committing when asked to commit
- Update CHANGELOG for user-facing product changes
- Enforce `DEVOPS_ADMIN` (or documented role) server-side for admin mutations
- Audit sensitive admin mutations with `actor_kind = HUMAN` where required by the slice

## Related Documents

- `DEVELOPMENT_STANDARDS.md`
- `docs/SDD-BOOTSTRAP.md`
- `docs/00-context/sdd-profile.md`
- `docs/00-context/decisions/ADR-0007-adopt-atlas-style-project-rules-and-bilingual-sdd.md`
- `docs/00-context/decisions/ADR-0008-active-agent-handoff-markdown.md`
- `docs/00-context/decisions/ADR-0009-english-only-project-and-sdd-docs.md`
- `docs/00-context/AGENT_HANDOFF.md`
- `.agents/skills/wwa-sdd-generate-all/SKILL.md`
