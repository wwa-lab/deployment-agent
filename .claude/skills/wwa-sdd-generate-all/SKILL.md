---
name: wwa-sdd-generate-all
description: >
  WWA Agent Hub project-specific SDD orchestration skill. Use when the user asks to generate all
  SDD documents for a WWA slice, bootstrap a feature slice, or prepare implementation-ready
  documentation. Produces the full English WWA SDD set using PROJECT_RULES, DEVELOPMENT_STANDARDS,
  SDD-BOOTSTRAP, and project-local SDD skills.
---

# wwa-sdd-generate-all

Generate the complete WWA Agent Hub SDD document set for one product slice.

This is a project-local orchestration skill. It coordinates smaller skills; it does not replace them.

## Mandatory Skill Chain

Use project-local skills in this order (do not hand-write the entire set in one ad hoc pass):

| Order | Skill | Output |
|---|---|---|
| 1 | `wwa-sdd-generate-all` | Slice contract, orchestration, final consistency gate |
| 2 | `req-to-user-story` | User stories + acceptance criteria |
| 3 | `user-story-to-spec` | Implementation-facing spec |
| 4 | `spec-to-architecture` | Architecture, data flow, data model |
| 5 | `architecture-to-design` | Design + contracts |
| 6 | `design-to-tasks` | Implementation tasks |
| 7 | `review-doc-quality` | Quality and traceability review |

Use `architecture-review` when the slice materially changes architecture, platform/agent boundaries, API/persistence, security, audit, or data ownership.

Use `review-code-against-design` only after implementation exists.

## When To Use

- "为这个 slice 生成完整 SDD"
- "一键生成 SDD"
- "prepare SDD for implementation"
- "bootstrap a new WWA feature slice"

## Required Context Before Generating

1. `PROJECT_RULES.md`
2. `AGENTS.md` / `CLAUDE.md`
3. `DEVELOPMENT_STANDARDS.md`
4. `docs/SDD-BOOTSTRAP.md`
5. `docs/00-context/sdd-profile.md`
6. Relevant ADRs under `docs/00-context/decisions/`
7. Existing slice docs under `docs/01-*` … `docs/06-*`
8. UI prototype / FE baseline when relevant (for example `docs/prototypes/`)

## Language

English-only (ADR-0009). Do not create `.zh-CN.md` companions unless the user explicitly asks.

## Document Set (WWA Paths)

1. `docs/01-requirements/{slice}-requirement.md`
2. `docs/02-user-stories/{slice}-user-stories.md`
3. `docs/03-spec/{slice}-spec.md`
4. `docs/04-architecture/{slice}-architecture.md`
5. Data flow when stateful/integrations: `…/{slice}-data-flow.md`
6. Data model when persistence: `…/{slice}-data-model.md`
7. `docs/05-design/{slice}-design.md`
8. API guide when API changes: `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md`
9. `docs/06-tasks/{slice}-tasks.md`
10. `docs/00-context/{slice}-traceability.md`

Keep WWA historical filenames (`-requirement`, `-user-stories`).

## Workflow

### Step 1 — Slice contract

Define slug, goal, in/out of scope, sources, acceptance, verification, constraints (security, agent boundary, audit, HITL).

### Step 2 — Requirements

Stable IDs such as `REQ-{SLICE}-001`. Then run `req-to-user-story`.

### Step 3 — User stories

IDs such as `US-{SLICE}-001` with Given/When/Then acceptance. Then `user-story-to-spec`.

### Step 4 — Spec

Happy path, empty/error states, acceptance matrix. `docs/03-spec/` is behavior source of truth.

### Step 5 — Architecture / data flow / data model

Call out platform vs agent ownership, shared-component rules, persistence boundaries, audit expectations. Use `spec-to-architecture`.

### Step 6 — Design

UX, components, API/integration, test hooks. Ground UI in accepted prototypes when present. Use `architecture-to-design`.

### Step 7 — API guide

Only when backend/API is in scope. Otherwise document deferral in traceability.

### Step 8 — Tasks

Actionable, ordered, mapped to requirements/spec, with verification commands. Use `design-to-tasks`.

### Step 9 — Traceability

Link sources → requirements → stories → spec/design → tasks → verification.

### Step 10 — Quality gate

Apply `review-doc-quality` and `docs/00-context/checklists/sdd-generation-gate.md`.

## WWA-Specific Constraints To Preserve

- Multi-agent isolation: server-forced `effectiveAgent`; thin agent FE wrappers
- Shared components must stay agent-agnostic
- HITL classes in `CLAUDE.md` must never be auto-approved
- Guest read-only mutations blocked
- Enum backend ↔ frontend type sync
- Flyway → regenerate `docs/sql/ORACLE_CURRENT_SCHEMA.sql`

## Final Response Must Include

- Slice slug
- Files created/updated
- API guide included or deferred
- Assumptions / open questions
- **SDD skill chain used: yes** (list entry + downstream skill files read)
- `review-doc-quality` result
- Recommended implementation handoff command

If the skill chain cannot be used, **stop** and report instead of generating ad hoc SDD.
