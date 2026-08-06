# SDD Bootstrap: WWA Agent Hub

## Purpose

Starter guide for Spec Driven Development in this repository. Use it when a new or materially updated slice needs an implementation-ready English SDD set.

## Operating Model

- Claude Code is the preferred SDD document generator when available.
- Codex may also generate or update SDD documents when that is the fastest safe path.
- Codex is the preferred implementation agent.
- Any agent that writes SDD must follow the same bootstrap, skill chain, traceability rules, and quality gates.
- Language: English-only for SDD and project rules (ADR-0009).

## One-Entry SDD Skill

Start from:

- `.claude/skills/wwa-sdd-generate-all/SKILL.md` (Claude Code)
- `.agents/skills/wwa-sdd-generate-all/SKILL.md` (Codex / shared agents)

This skill orchestrates the smaller SDD skills instead of writing every document ad hoc.

## Mandatory Skill Chain

| Order | Skill | Purpose |
|---|---|---|
| 1 | `wwa-sdd-generate-all` | Slice contract, paths, consistency gate |
| 2 | `req-to-user-story` | Requirements → user stories + acceptance |
| 3 | `user-story-to-spec` | Stories → implementation-facing spec |
| 4 | `spec-to-architecture` | Spec → architecture, data flow, data model |
| 5 | `architecture-to-design` | Architecture → UX/API/design |
| 6 | `design-to-tasks` | Design → implementation tasks |
| 7 | `review-doc-quality` | Completeness and traceability review |

Use `architecture-review` when architecture, API, persistence, security, platform boundaries, or data ownership change materially.

Use `review-code-against-design` only after implementation exists.

## Required Context Before Generating SDD

- `PROJECT_RULES.md`
- `AGENTS.md` / `CLAUDE.md`
- `DEVELOPMENT_STANDARDS.md`
- `docs/SDD-BOOTSTRAP.md`
- `docs/00-context/sdd-profile.md`
- Relevant existing slice docs
- Relevant ADRs
- Prototype or FE baseline when UI is in scope (for example `docs/prototypes/`)

## Recommended Slice Goal Shape

```text
Goal: <user-facing outcome>
Slice: <stable kebab-case slug>
Scope: <included and excluded behavior>
Sources: <SDD / prototype / ADR references>
Acceptance: <observable completion criteria>
Verification: <commands, checks, manual review>
Constraints: <security, agent boundary, audit, data-safety>
```

## Language

Write SDD artifacts in **English only**. Do not create `.zh-CN.md` companions unless the user explicitly requests them.

## WWA SDD Document Set

Generate or update:

1. `docs/01-requirements/{slice}-requirement.md`
2. `docs/02-user-stories/{slice}-user-stories.md`
3. `docs/03-spec/{slice}-spec.md`
4. `docs/04-architecture/{slice}-architecture.md`
5. `docs/04-architecture/{slice}-data-flow.md` when stateful workflows/integrations exist
6. `docs/04-architecture/{slice}-data-model.md` when persistence exists
7. `docs/05-design/{slice}-design.md`
8. `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` when API contracts change
9. `docs/06-tasks/{slice}-tasks.md`
10. `docs/00-context/{slice}-traceability.md`

Filename note: WWA keeps historical names (`-requirement`, `-user-stories`).

## Quality Gate

Before finishing SDD generation, verify:

- [ ] Required English SDD artifacts exist for the slice
- [ ] Requirements map to stories / spec / tasks
- [ ] Platform and agent boundaries are explicit when relevant
- [ ] Security / audit / HITL constraints are explicit when relevant
- [ ] API guide is included or deferral is documented
- [ ] `review-doc-quality` was applied (or skip reason recorded)
- [ ] Completion report lists the skill chain used

Checklist detail: `docs/00-context/checklists/sdd-generation-gate.md`

## Final Response Format

Summarize:

- Slice slug
- Files created/updated
- Whether API guide was included or deferred
- Key assumptions and open questions
- Skill chain evidence
- Recommended implementation handoff, for example:

```text
Implement {slice} strictly against docs/03-spec/{slice}-spec.md and docs/06-tasks/{slice}-tasks.md. Do not expand scope.
```
