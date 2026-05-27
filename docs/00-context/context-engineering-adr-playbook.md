# Context Engineering And ADR Playbook

This playbook defines a portable pattern for preserving engineering experience across projects and coding tools.

## Goal

Make durable knowledge available to Codex, Claude Code, OpenCode, and future maintainers without depending on chat history or one tool's private memory.

## Portable Layout

Use this structure in every software project that needs durable architecture memory:

```text
AGENTS.md
CLAUDE.md
OPENCODE.md
.agents/
  skills/
    context-engineering-adr/
.claude/
  skills/
    context-engineering-adr/
docs/
  00-context/
    context-engineering-adr-playbook.md
    decisions/
      README.md
      ADR-0001-use-context-engineering-and-adrs.md
```

## Tool Integration

### Codex

- Put reusable skill content under `.agents/skills/context-engineering-adr/`.
- Add a short `Context Engineering And ADRs` section to `AGENTS.md`.
- Point Codex to `docs/00-context/` and `docs/00-context/decisions/`.

### Claude Code

- Mirror the same skill content under `.claude/skills/context-engineering-adr/`.
- Add the same durable-memory rule to `CLAUDE.md`.
- Keep the Claude-specific entry short and link to the shared docs.

### OpenCode

- Add `OPENCODE.md` when a project uses OpenCode.
- If OpenCode does not load local skill folders natively, make `OPENCODE.md` point to the same shared workflow and ADR directory.

## What To Capture

Use context docs for:

- Product/system positioning
- Domain glossary
- Long-lived architecture boundaries
- Agent onboarding rules
- Cross-tool working conventions

Use ADRs for:

- Platform ownership decisions
- Module boundaries
- Security, audit, and authorization decisions
- External integration strategy
- Data ownership and persistence tradeoffs
- Any decision likely to be questioned later

## What Not To Capture

Avoid ADRs for:

- Simple copy edits
- Local bug fixes with no architectural consequence
- Routine dependency updates
- Implementation details already covered by a current design doc
- Temporary notes that belong in an issue or task

## Minimal Install Checklist

1. Create `docs/00-context/decisions/README.md`.
2. Add `ADR-0001-use-context-engineering-and-adrs.md`.
3. Add or copy the `context-engineering-adr` skill into Codex and Claude skill directories.
4. Add a short ADR/context section to each root tool instruction file the project uses.
5. Link important architecture/design docs to relevant ADRs.
6. During reviews, check whether a cross-cutting decision needs a new ADR.

## Maintenance Rules

- Accepted ADRs are append-only except typo and link fixes.
- Supersede old decisions with new ADRs instead of rewriting history.
- Keep indexes current when adding ADRs.
- Prefer links over duplicated rationale.
- If code disagrees with an accepted ADR, resolve the drift explicitly.
