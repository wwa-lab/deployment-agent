---
name: context-engineering-adr
description: Use this skill when a task changes architecture, cross-project conventions, AI-agent working context, platform boundaries, integration rules, security posture, data ownership, or any decision that future maintainers and coding agents should remember. Also use when the user asks for context engineering, ADRs, decision records, durable project knowledge, or shared instructions for Codex, Claude Code, or OpenCode.
---

# Context Engineering + ADR

Use this skill to turn useful engineering experience into durable, tool-neutral project memory.

## Purpose

- Keep stable project context discoverable by humans and AI agents.
- Record important decisions once, with rationale and consequences.
- Prevent architectural knowledge from being scattered across chat logs, PRs, and stale docs.
- Make the same guidance usable from Codex, Claude Code, OpenCode, and similar coding agents.

## When to Apply

Use this skill for changes involving:

- Architecture boundaries, module ownership, or platform-vs-product decisions
- Shared agent instructions, prompt/context strategy, or repo working conventions
- Security, audit, authorization, data ownership, and compliance-sensitive behavior
- External integrations, persistence strategy, runtime topology, or irreversible tradeoffs
- Any choice where a future maintainer may ask, "Why did we do it this way?"

Do not create a new ADR for small copy edits, local bug fixes, routine dependency bumps, or implementation details already covered by an existing decision.

## Repository Placement

Prefer this portable structure:

```text
docs/
  00-context/
    decisions/
      README.md
      ADR-0001-use-context-engineering-and-adrs.md
```

If the project already has an ADR location, reuse it. If no context directory exists, create `docs/00-context/` unless the repository has a stronger local convention.

## Workflow

1. Inspect the existing docs and agent instruction files:
   - `AGENTS.md`
   - `CLAUDE.md`
   - `OPENCODE.md`
   - `docs/00-context/`
   - `docs/04-architecture/`
2. Decide whether the knowledge belongs in:
   - **Context doc**: durable background, glossary, boundaries, onboarding notes
   - **ADR**: one significant decision and its rationale
   - **Agent instruction**: short rule that agents must remember before work
3. For an ADR, write one record per decision. Do not merge unrelated decisions.
4. Keep ADRs immutable after acceptance. To reverse or replace one, create a new ADR and mark the old one `Superseded`.
5. Link ADRs from architecture/design docs instead of duplicating rationale.
6. Keep agent instructions tool-neutral. Put detailed procedure in the skill or docs, then point Codex, Claude Code, and OpenCode to the same source.

## ADR Template

Use `references/adr-template.md` when creating a new ADR.

## Context Map Template

Use `references/context-map-template.md` when creating or refreshing a project context overview.

## Status Values

- `Proposed` - under discussion
- `Accepted` - current decision
- `Deprecated` - no longer recommended, but not replaced by a specific ADR
- `Superseded` - replaced by another ADR

## Good ADR Heuristics

- The title states the decision, not just the topic.
- Context explains the forces and constraints present at the time.
- Alternatives are real options that were considered.
- Consequences include costs, not only benefits.
- Review triggers say when the decision should be revisited.
- The record is concise enough to read during a code review.
