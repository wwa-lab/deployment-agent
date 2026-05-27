---
name: agentic-sdlc-orchestrator
description: Use this skill when a user wants the full predictable AI engineering workflow, especially propose/apply/verify/archive, OpenSpec-like change lifecycle, Superpowers-style execution rhythm, Agent Skills-style discipline, or an end-to-end Agentic SDLC flow across Codex, Claude Code, OpenCode, or other IDE agents.
---

# Agentic SDLC Orchestrator

Use this skill as the control layer for the full AI engineering workflow.

## Purpose

Coordinate the three-layer model:

- **Direction**: SDD / OpenSpec-style documents define what should change.
- **Rhythm**: Superpowers-style process keeps the agent moving through planning, TDD, review, and completion.
- **Discipline**: Agent Skills-style quality rules and CI gates define what acceptable output looks like.

## Command Semantics

Support these four lifecycle operations:

| Operation | Goal | Main Skills |
|---|---|---|
| `propose` | Define the change before implementation | `sdd-profile-manager`, `sdd-slice-bootstrap`, `context-engineering-adr` |
| `apply` | Implement from approved docs | `execution-manifest`, `design-to-tasks`, `tasks-to-implementation` |
| `verify` | Check docs/code/tests against the intended change | `freshness-gate`, `review-doc-quality`, `review-code-against-design`, `review-docs-against-code` |
| `archive` | Preserve final state and decisions | `context-engineering-adr`, `freshness-gate` |

## Default Change Layout

Use this layout for OpenSpec-like change containers when the project does not already use `openspec/`:

```text
docs/00-context/changes/{change-id}/
  proposal.md
  manifest.yaml
  review.md
  archive.md
```

The SDD source artifacts remain in the project's normal SDD folders, such as `docs/01-requirements/` through `docs/06-tasks/`. The change container links to them instead of duplicating them.

## Workflow

### 1. Propose

1. Read the active SDD profile.
2. Create or update a change proposal.
3. Bootstrap or audit the slice document set.
4. Capture open questions.
5. Identify ADRs needed for architecture, boundaries, security, integrations, or workflow changes.
6. Stop before implementation until the proposal and required docs are accepted.

### 2. Apply

1. Confirm the proposal/tasks are accepted.
2. Create an execution manifest.
3. Check freshness before implementation.
4. Follow the relevant task-to-implementation workflow.
5. Use TDD and review discipline when code changes are involved.
6. Keep changes scoped to the manifest and active SDD profile.

### 3. Verify

1. Run freshness checks against upstream docs, ADRs, code, and tests.
2. Review documents for phase discipline and completeness.
3. Review code against design and tasks.
4. Review docs against code when implementation changed behavior.
5. Run verification commands from the manifest or project instructions.
6. Report blockers, residual risks, and evidence.

### 4. Archive

1. Confirm implementation and verification are complete.
2. Update ADRs, changelog, release notes, and indexes where required.
3. Write an archive note with final artifact links and verification evidence.
4. Mark superseded or stale documents explicitly.

## Output Templates

Use:

- `references/change-proposal-template.md`
- `references/change-archive-template.md`

## Rules

- Do not implement from an unaccepted proposal unless the user explicitly chooses a reduced path.
- Do not trust "latest" context for agent handoff; use a manifest.
- Do not treat AI skill compliance as a hard gate. Critical gates must be backed by tests, scripts, hooks, or CI.
- If OpenSpec is installed and active in the project, use OpenSpec as the change-entry source of truth and map this workflow onto its change lifecycle instead of creating a parallel change container.
