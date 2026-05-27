# ADR-0004: Add Orchestrator, Discipline Profile, And Doctor For Predictable AI Engineering Workflow

## Status

Accepted

## Date

2026-05-27

## Context

The global SDD skill library and Agentic SDLC primitives provide reusable building blocks, but the workflow still needs a clear control layer that maps the intended engineering lifecycle into repeatable operations.

The desired model is a three-layer workflow:

- SDD / OpenSpec-style documents set direction.
- Superpowers-style process keeps AI moving through planning, TDD, review, and completion.
- Agent Skills-style discipline and CI-backed checks keep output quality predictable.

Without an orchestrator, users must manually choose among many skills. Without a discipline profile, it is unclear which rules are AI behavior constraints and which require script, hook, CI, or human-review backing. Without a doctor, global skill drift can go unnoticed across Codex, Claude Code, OpenCode, and shared skill directories.

## Decision

Add three reusable assets:

- `agentic-sdlc-orchestrator` skill for `propose`, `apply`, `verify`, and `archive`.
- `docs/00-context/agent-discipline-profile.md` to define active quality disciplines and their enforcement strength.
- `agentic-sdlc-doctor` skill plus `scripts/agentic-sdlc-doctor.sh` to verify local/global skill installation, OpenCode routing, ADR index coverage, and profile presence.

Update OpenCode `/sdd` routing so `propose`, `apply`, `verify`, and `archive` go through the orchestrator first.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Keep using individual skills directly | Users and agents still need to remember the correct sequence manually. |
| Install OpenSpec only | OpenSpec-style lifecycle is valuable, but this repo already has a project-specific SDD chain and cross-tool skill library. |
| Put all discipline rules in root `AGENTS.md` / `CLAUDE.md` | Root instructions would become too large and hard to reuse across projects. |
| Rely on manual inspection for global drift | Cross-tool skill drift is easy to miss without a repeatable doctor check. |

## Consequences

### Positive

- Users can think in lifecycle verbs: propose, apply, verify, archive.
- The workflow is closer to a predictable engineering pipeline instead of a loose skill collection.
- Discipline rules are explicit about what AI can guide versus what must be enforced by tools or humans.
- Global installation health can be checked repeatedly.

### Negative

- The global skill library grows to 18 skills.
- The doctor script adds another maintenance surface when skills are added or removed.

### Neutral / Operational

- Run `scripts/agentic-sdlc-doctor.sh` after syncing global skills.
- Keep `.opencode/commands/sdd.md` aligned with orchestrator semantics.
- Future hard gates such as coverage, secret scanning, and dependency scanning should be added as CI templates rather than only as AI instructions.

## Review Triggers

Revisit this decision when:

- The project adopts native OpenSpec as the change-entry layer.
- OpenCode gains native skill lifecycle management.
- A dedicated Agent Skills package replaces the local discipline profile.
- Doctor checks need to become CI jobs.

## Related Documents

- [Agent discipline profile](../agent-discipline-profile.md)
- [Global SDD skills playbook](../global-sdd-skills-playbook.md)
- [SDD profile](../sdd-profile.md)
- [ADR-0003](ADR-0003-adopt-global-agentic-sdlc-primitives.md)
