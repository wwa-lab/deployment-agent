---
name: sdd-slice-bootstrap
description: Use this skill when starting a new feature slice, module, workspace, agent, product area, or SDLC capability that needs a complete Spec Driven Development document set before implementation. Creates or verifies the standard slice documents, traceability index, and minimum gates before coding.
---

# SDD Slice Bootstrap

Use this skill to create or verify the SDD document set for a feature slice before implementation.

## Purpose

A slice is a coherent product or platform capability with its own requirements, stories, spec, architecture, design, tasks, and supporting contracts. The goal is to avoid coding from partial context.

## Default 9-Document Set

Use this set unless the project's `docs/00-context/sdd-profile.md` says otherwise:

| # | Stage | File Pattern |
|---|---|---|
| 1 | Requirements | `docs/01-requirements/{slice}-requirements.md` |
| 2 | User Stories | `docs/02-user-stories/{slice}-stories.md` |
| 3 | Spec | `docs/03-spec/{slice}-spec.md` |
| 4 | Architecture | `docs/04-architecture/{slice}-architecture.md` |
| 5 | Data Flow | `docs/04-architecture/{slice}-data-flow.md` |
| 6 | Data Model | `docs/04-architecture/{slice}-data-model.md` |
| 7 | Design | `docs/05-design/{slice}-design.md` |
| 8 | API Guide | `docs/05-design/contracts/{slice}-API_IMPLEMENTATION_GUIDE.md` |
| 9 | Tasks | `docs/06-tasks/{slice}-tasks.md` |

## Workflow

1. Identify the slice key:
   - lowercase kebab-case
   - stable across all documents
   - no spaces or transient ticket IDs
2. Check for an SDD profile:
   - `docs/00-context/sdd-profile.md`
   - root agent instructions
   - existing docs structure
3. Inventory existing slice docs.
4. Create missing docs using the templates in `references/`.
5. Add a traceability table to each doc or create `docs/00-context/{slice}-traceability.md`.
6. Stop before implementation if required docs are missing or materially thin.

## Minimum Gate Before Code

Before implementation starts, the slice must clearly answer:

- What problem is being solved?
- Who is the user or actor?
- What is in scope and out of scope?
- What happens in the happy path?
- What happens in error and empty states?
- What data shape or state contract is needed?
- Which module owns which responsibility?
- How acceptance will be checked?
- Which ADRs constrain the design?

## Output Modes

- **Bootstrap mode**: create empty-but-useful doc skeletons.
- **Audit mode**: report which docs exist, which are missing, and which are too thin.
- **Backfill mode**: mark docs as `Backfilled` when code already exists.

## Rules

- Do not pretend backfilled docs preceded implementation.
- Do not duplicate ADR rationale; link to ADRs.
- Do not start implementation from a slice with incomplete gates unless the user explicitly chooses a reduced profile.
